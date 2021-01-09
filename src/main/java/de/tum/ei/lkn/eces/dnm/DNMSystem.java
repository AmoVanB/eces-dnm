package de.tum.ei.lkn.eces.dnm;

import de.tum.ei.lkn.eces.core.*;
import de.tum.ei.lkn.eces.core.annotations.ComponentStateIs;
import de.tum.ei.lkn.eces.dnm.config.ACModel;
import de.tum.ei.lkn.eces.dnm.config.DetServConfig;
import de.tum.ei.lkn.eces.dnm.exceptions.DNMException;
import de.tum.ei.lkn.eces.dnm.inputmodels.PerInEdgeTokenBucketUtilization;
import de.tum.ei.lkn.eces.dnm.inputmodels.ResourceUtilization;
import de.tum.ei.lkn.eces.dnm.inputmodels.TokenBucketUtilization;
import de.tum.ei.lkn.eces.dnm.mappers.*;
import de.tum.ei.lkn.eces.dnm.queuemodels.MHMQueueModel;
import de.tum.ei.lkn.eces.dnm.queuemodels.QueueModel;
import de.tum.ei.lkn.eces.dnm.resourcemanagement.resourceallocation.MHM.MHMResourceAllocation;
import de.tum.ei.lkn.eces.dnm.resourcemanagement.resourceallocation.ResourceAllocation;
import de.tum.ei.lkn.eces.dnm.resourcemanagement.resourceallocation.SelectedResourceAllocation;
import de.tum.ei.lkn.eces.dnm.resourcemanagement.resourceallocation.TBM.TBMResourceAllocation;
import de.tum.ei.lkn.eces.graph.Edge;
import de.tum.ei.lkn.eces.graph.Node;
import de.tum.ei.lkn.eces.graph.mappers.EdgeMapper;
import de.tum.ei.lkn.eces.network.*;
import de.tum.ei.lkn.eces.network.mappers.*;
import de.tum.ei.lkn.eces.network.util.NetworkInterface;
import de.uni_kl.cs.discodnc.curves.ArrivalCurve;
import de.uni_kl.cs.discodnc.curves.CurvePwAffine;
import de.uni_kl.cs.discodnc.curves.ServiceCurve;
import de.uni_kl.cs.discodnc.curves.dnc.ServiceCurve_DNC;
import de.uni_kl.cs.discodnc.misc.Pair;
import de.uni_kl.cs.discodnc.nc.bounds.Bound;
import de.uni_kl.cs.discodnc.numbers.Num;
import org.json.JSONObject;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;

/**
 * Deterministic network modeling (DNM) system.
 *
 * It
 * - automatically allocates resources to queues based on the chosen resource allocation algorithm.
 * - automatically updates MHM delays if ILS is used
 * - automatically updates TBM service curves when new flows are accepted or removed
 *
 * @author Jochen Guck
 * @author Amaury Van Bemten
 */
public class DNMSystem extends RootSystem {
	// Mappers
    private Mapper<DetServConfig> modelingConfigMapper = new DetServConfigMapper(controller);
    private Mapper<Edge> edgeMapper                                   = new EdgeMapper(controller);
    private Mapper<Link> linkMapper                                   = new LinkMapper(controller);
    private Mapper<Rate> rateMapper                                   = new RateMapper(controller);
    private Mapper<Queue> queueMapper                                 = new QueueMapper(controller);
    private Mapper<Scheduler> schedulerMapper                         = new SchedulerMapper(controller);
    private Mapper<Delay> delayMapper                                 = new DelayMapper(controller);
    private Mapper<ResourceUtilization> resourceUtilizationMapper     = new ResourceUtilizationMapper(controller);
    private Mapper<QueueModel> queueModelMapper                       = new QueueModelMapper(controller);
    private Mapper<MHMQueueModel> MHMQueueModelMapper                 = new MHMQueueModelMapper(controller);
    private Mapper<ToNetwork> toNetworkMapper                         = new ToNetworkMapper(controller);
    private Mapper<PerInEdgeTokenBucketUtilization> inputModelMapper  = new PerInEdgeTokenBucketUtilizationMapper(controller);
    private Mapper<TokenBucketUtilization> singleTokenBucketMapper    = new TokenBucketUtilizationMapper(controller);
    private Mapper<NetworkNode> networkNodeMapper                     = new NetworkNodeMapper(controller);
    private Mapper<SelectedResourceAllocation> selResourceAllocMapper = new SelectedResourceAllocationMapper(controller);

	public DNMSystem(Controller controller) {
		super(controller);
	}

	/**
	 * @param entity an entity.
	 * @return The modeling config attached to this entity, DNMException if there is none.
	 */
	private DetServConfig getModelingConfig(Entity entity) {
		if(modelingConfigMapper.isIn(entity))
			return modelingConfigMapper.get(entity);
		else
			throw new RuntimeException("No modeling configuration available!");
	}

    /**
     * When a new scheduler is created, defines the resource allocation algorithm to use.
     * This is done based on the 'resourceAllocationSelector' interface.
     * @param scheduler new scheduler.
     */
    @ComponentStateIs(State = ComponentStatus.New)
    public void chooseResourceAllocation(Scheduler scheduler) {
        Link link = linkMapper.get(toNetworkMapper.get(edgeMapper.get(scheduler.getEntity()).getEntity()).getNetworkEntity());
        Network network = link.getNetwork();
        if(this.getModelingConfig(network.getQueueGraph().getEntity()) != null) {
            logger.info("Looking for a resource allocation algorithm for " + scheduler + " on link " + link);
            ResourceAllocation algorithm = this.getModelingConfig(network.getQueueGraph().getEntity()).getSelectResourceAllocation().selectResourceAllocation(controller, scheduler);
            logger.info("Using " + algorithm + " for " + scheduler);
            selResourceAllocMapper.attachComponent(scheduler.getEntity(), new SelectedResourceAllocation(algorithm));
        }
    }

    /**
     * Allocate resources when a Resource Allocation algorithm was defined.
     *
     * This means,
     * for TBM:
     *  - define a delay for each queue
     *  - attach a Delay object to the entity of each Queue
     *  - attach an PerInEdgeTokenBucketUtilization object which will keep track of the used resources to the entity of each Queue
     *  - attach an R, T (link parameters) service curve as QueueModel to each Queue's entity
     *
     * For MHM:
     *  - define a burst, rate for each queue
     *  - attach resulting Delay object to the entity of each Queue
     *  - attach an TokenBucket object which will keep track of the used resources to the entity of each Queue
     *  - attach an token bucket (defined burst and rate) as QueueModel to each Queue's entity
     * @param selectedResourceAllocation newly defined resource allocation algorithm for a specific scheduler.
     */
	@ComponentStateIs(State = ComponentStatus.New)
	public void allocateResources(SelectedResourceAllocation selectedResourceAllocation) {
        Scheduler scheduler = schedulerMapper.get(selectedResourceAllocation.getEntity());
        Link link = linkMapper.get(toNetworkMapper.get(edgeMapper.get(scheduler.getEntity()).getEntity()).getNetworkEntity());
        double linkRate = rateMapper.get(edgeMapper.get(scheduler.getEntity()).getEntity()).getRate();
        DetServConfig myConfig = getModelingConfig(link.getNetwork().getQueueGraph().getEntity());
		Queue[] queues = scheduler.getQueues();

        logger.info("allocating resources to " + scheduler);

        // Get resource allocation algorithm
        ResourceAllocation resourceAllocation = selectedResourceAllocation.getResourceAllocation();

        // Allocate resources
        double[][] resources = resourceAllocation.allocateResources(scheduler);

        // First service curve (Eqn. 2 & 3 of DetServ)
        double R = linkRate;
        double T = (2 * myConfig.getMaximumPacketSize()) / R;

        // Storing allocated resources and queue models.
        switch(myConfig.getAcModel()) {
			case MHM:
                ServiceCurve currentServiceCurve = CurvePwAffine.getFactory().createRateLatency(R, T);
                for(int i = 0; i < queues.length; i++) {
                    ArrivalCurve currentTokenBucket = CurvePwAffine.getFactory().createTokenBucket(resources[i][MHMResourceAllocation.RATE_INDEX], resources[i][MHMResourceAllocation.BURST_INDEX]);
                    // Storing allocated resources to the queue
                    MHMQueueModel resourcesForQueue = new MHMQueueModel(currentServiceCurve, currentTokenBucket);

                    currentServiceCurve = Bound.leftOverServiceARB(currentServiceCurve, currentTokenBucket);
                    if(i < queues.length - 1)
					    ((ServiceCurve_DNC) currentServiceCurve).makeRateLatency();
                    queueModelMapper.attachComponent(queues[i], resourcesForQueue);
                }

                break;

			case TBM:
                // Store service curve of the link to all queues
                for (Queue queue : queues) {
                    QueueModel queueModel = new QueueModel();
					queueModel.setServiceCurve(CurvePwAffine.getFactory().createRateLatency(R, T));
                    queueModelMapper.attachComponent(queue, queueModel);
                }

			    break;

            default:
                throw new DNMException("Unknown access control model: " + myConfig.getAcModel());
		}

		// Attach utilized resources components (and delay values)
		for (int i = 0; i < queues.length; i++) {
            switch(myConfig.getAcModel()) {
                case MHM:
                    // For ILS,
                    // We implement the first case mentioned in DetServ p9-10: normal computations and then
                    // just reduce the delay by shaping.
                    // The delay will be directly corrected by the implemented listeners in the current system.
                    delayMapper.attachComponent(queues[i], new Delay(resources[i][MHMResourceAllocation.DELAY_INDEX]));
                    resourceUtilizationMapper.attachComponent(queues[i], new TokenBucketUtilization());
                    break;

                case TBM:
                    if(myConfig.isInputLinkShaping()) {
                        // We will keep track of the token buckets coming from each input link
                        resourceUtilizationMapper.attachComponent(queues[i], new PerInEdgeTokenBucketUtilization());
                        delayMapper.attachComponent(queues[i], new Delay(resources[i][TBMResourceAllocation.DELAY_INDEX]));
                    }
                    else {
                        resourceUtilizationMapper.attachComponent(queues[i], new TokenBucketUtilization());
                        delayMapper.attachComponent(queues[i], new Delay(resources[i][TBMResourceAllocation.DELAY_INDEX]));
                    }

                    break;

                default:
                    throw new DNMException("Unknown access control model: " + myConfig.getAcModel());
            }
		}
	}

    /**
     * Resources have changed, the service curves of lower priority queues.
     * @param resourceUtilization new resource usage.
     */
	@ComponentStateIs(State = ComponentStatus.Updated)
	public void TBMUpdateServiceCurves(ResourceUtilization resourceUtilization) {
		updateLowerPriorityServiceCurves(resourceUtilization.getEntity());
	}

    /**
     * A service curve has changed, lower priority service must also change.
     * @param queueModel new service curve.
     */
	@ComponentStateIs(State = ComponentStatus.Updated)
	public void TBMUpdateServiceCurves(QueueModel queueModel) {
		updateLowerPriorityServiceCurves(queueModel.getEntity());
	}

    /**
     * Update the service curves of low priority queues when something changed on a given queue.
     * @param queueEntity Entity of the queue where something changed.
     */
    private void updateLowerPriorityServiceCurves(Entity queueEntity) {
        Edge queueEdge = edgeMapper.get(queueEntity);
		DetServConfig modelingConfig = getModelingConfig(queueEdge.getSource().getGraph().getEntity());

		// Only for TBM, MHM does not need this!
		if(modelingConfig.getAcModel() == ACModel.MHM)
			return;

		Queue updatedQueue = queueMapper.get(queueEntity);
		Scheduler updatedScheduler = updatedQueue.getScheduler();

		// Getting the lower priority queue
		Queue lowerPriorityQueue = null;
		boolean foundQueue = false;
		for(Queue queue : updatedScheduler.getQueues()) {
			if(foundQueue) {
				lowerPriorityQueue = queue;
				break;
			}
			if(queue.getEntity() == updatedQueue.getEntity())
                foundQueue = true;
		}
		if(lowerPriorityQueue == null)
			return;


		ResourceUtilization updatedQueueResourceUtilization = resourceUtilizationMapper.get(queueEntity);
		ServiceCurve updatedQueueServiceCurve = queueModelMapper.get(queueEntity).getServiceCurve();

		// Getting the arrival curve of the updated queue.
		ArrivalCurve updatedQueueArrivalCurve;
		if(updatedQueueResourceUtilization instanceof TokenBucketUtilization) {
			Num rate = ((TokenBucketUtilization) updatedQueueResourceUtilization).getRate();
			Num burst = ((TokenBucketUtilization) updatedQueueResourceUtilization).getBurst();
			updatedQueueArrivalCurve = CurvePwAffine.getFactory().createTokenBucket(rate, burst);
		}
		else if(updatedQueueResourceUtilization instanceof PerInEdgeTokenBucketUtilization) {
			LinkedList<ArrivalCurve> individualArrivalCurves = new LinkedList<>();
			for(Map.Entry<Edge, Pair<Num>> oneEdge : ((PerInEdgeTokenBucketUtilization) updatedQueueResourceUtilization).getTokenBuckets()) {
			    // Not shaping traffic coming from the node itself.
			    if(oneEdge.getKey() == queueEdge)
	                individualArrivalCurves.add(CurvePwAffine.getFactory().createTokenBucket(oneEdge.getValue().getFirst(), oneEdge.getValue().getSecond()));
                else
    				individualArrivalCurves.add(CurvePwAffine.min(
							CurvePwAffine.getFactory().createTokenBucket(oneEdge.getValue().getFirst(), oneEdge.getValue().getSecond()),
							CurvePwAffine.getFactory().createTokenBucket(rateMapper.get(oneEdge.getKey().getEntity()).getRate(), modelingConfig.getMaximumPacketSize())
					));
			}

			updatedQueueArrivalCurve = CurvePwAffine.getFactory().createTokenBucket(0, 0);
			for(ArrivalCurve individualArrivalCurve : individualArrivalCurves)
                updatedQueueArrivalCurve = CurvePwAffine.add(updatedQueueArrivalCurve, individualArrivalCurve);

		}
		else {
			throw new DNMException(updatedQueueResourceUtilization.getClass().getTypeName() + " is not supported");
		}

		// Update curve of the next queue (note: "more" lower queues will automatically be updated by the listeners)
		final ServiceCurve nextServiceCurve = modelingConfig.getResidualMode().getResidualServiceCurve(updatedQueueServiceCurve, updatedQueueArrivalCurve);
        QueueModel queueModel = queueModelMapper.get(lowerPriorityQueue.getEntity());
        logger.info("New service curve for queue " + queueEdge + ": " + nextServiceCurve);
        queueModelMapper.updateComponent(queueModel, () -> queueModel.setServiceCurve(nextServiceCurve));
    }

    /**
     * A new corrected delay component has been attached.
     *
     * We now compute the delay reduction through ILS.
     * This method propagates the computation to neighbor edges, as a new edge requires
     * the re-computation for all neighbors (as the latters then have a now additional
     * input link).
     * @param delay the CorrectableDelay component attached.
     */
	@ComponentStateIs(State = ComponentStatus.New)
	public void doILSforMHMDelayCorrection(Delay delay) {
		doILSforMHMDelayCorrection(delay.getEntity());

        Edge edge = edgeMapper.get(delay.getEntity());

        /* It can happen that the reduction was already done to nodes when they didn't
         * have some neighbors which they have now. In this case, we have to rerun the
         * correction, as the number of input links changed.
         *
         * Hence, each time we do a correction, we do it again for the neighbors.
         */
		for(Edge e : edge.getSource().getIncomingConnections())
			doILSforMHMDelayCorrection(e.getEntity());

		for(Edge e : edge.getDestination().getOutgoingConnections())
			doILSforMHMDelayCorrection(e.getEntity());
	}

    /**
     * @param node a given node
     * @return true if the node corresponds to a host (that can send traffic)
     */
    private boolean isHost(Node node) {
        NetworkNode networkNode = null;
        ToNetwork toNetwork = toNetworkMapper.get(node.getEntity());
        networkNode = networkNodeMapper.get(toNetwork.getNetworkEntity());

        Collection<Host> hosts = networkNode.getNetwork().getHosts();
        for(Host host : hosts)
            for(NetworkInterface ifc : host.getInterfaces())
                if(host.getNetworkNode(ifc) == networkNode)
                    return true;

        return false;
    }

    /**
     * Helper method correcting the delay of a single edge.
     * @param edgeEntity Entity of the edge.
     */
	private void doILSforMHMDelayCorrection(Entity edgeEntity) {
		if(!modelingConfigMapper.isIn(edgeMapper.get(edgeEntity).getSource().getGraph().getEntity())) {
			// it was called on the link-level graph, we skip!
			return;
		}

        DetServConfig modelingConfig = getModelingConfig(edgeMapper.get(edgeEntity).getSource().getGraph().getEntity());

        // Only for MHM with ILS
        if(modelingConfig.getAcModel() != ACModel.MHM || !modelingConfig.isInputLinkShaping())
            return;

        //if(!this.queueModelMapper.isIn(edgeEntity) || !this.delayMapper.isIn(edgeEntity) || !this.edgeMapper.isIn(edgeEntity))
        //    return;

        if(isHost(edgeMapper.get(edgeEntity).getSource())) {
            logger.debug("No input link shaping because the edge is coming out of a host!");
            return;
        }

        logger.debug("Doing delay correction for " + edgeMapper.get(edgeEntity));

        Delay delayToReduce = this.delayMapper.get(edgeEntity);
        Edge physicalEdge = edgeMapper.get(queueMapper.get(edgeEntity).getScheduler().getEntity());
        double sumR = 0;
        double sumB = 0;
        // Computing the sum of maximum input arrival curves (Eqn 33 in DetServ)
        for(Edge incoming : physicalEdge.getSource().getIncomingConnections()) {
            sumR += rateMapper.get(incoming.getEntity()).getRate();
            sumB += modelingConfig.getMaximumPacketSize();
        }

        ArrivalCurve allocatedCurve = MHMQueueModelMapper.get(edgeEntity).getMaximumTokenBucket();
        ServiceCurve serviceCurve = MHMQueueModelMapper.get(edgeEntity).getServiceCurve();
        ArrivalCurve shapedCurve = CurvePwAffine.min(CurvePwAffine.getFactory().createTokenBucket(sumR, sumB), allocatedCurve);

        logger.debug("Maximum curve: r=" + allocatedCurve.getUltAffineRate() + ", b=" + allocatedCurve.getBurst());
        logger.debug("Shaping curve: r=" + sumR + ", b=" + sumB);
        logger.debug("Service curve: T=" + serviceCurve);

        Num newDelay = Bound.delayFIFO(shapedCurve, serviceCurve);

        // We always replace because maybe we are replacing a lower value that was computed earlier when there
        // was less input links!
        if(newDelay.doubleValue() != delayToReduce.getDelay()) {
            logger.debug("New delay " + newDelay + " replaces " + delayToReduce.getDelay() );
            this.delayMapper.updateComponent(delayToReduce, () -> delayToReduce.setDelay(newDelay.doubleValue()));
        }
        else {
            logger.debug("No correction (same delay of " + newDelay + ")!");
        }
	}

	@Override
	protected JSONObject toJSONObject(Component component) {
		JSONObject result = super.toJSONObject(component);
		if(component instanceof PerInEdgeTokenBucketUtilization) {
			DetServConfig modelingConfig = getModelingConfig(edgeMapper.get(component.getEntity()).getSource().getGraph().getEntity());

            ArrivalCurve aggregatedArrivalCurve = CurvePwAffine.getFactory().createTokenBucket(0, 0);
            for(Map.Entry<Edge, Pair<Num>> entry : ((PerInEdgeTokenBucketUtilization) component).getTokenBuckets()) {
                ArrivalCurve curve;
                // No shaping if input edge is current one
                if(entry.getKey() == linkMapper.getOptimistic(toNetworkMapper.getOptimistic(component.getEntity()).getNetworkEntity()).getLinkEdge()) {
                    curve = CurvePwAffine.getFactory().createTokenBucket(entry.getValue().getFirst(), entry.getValue().getSecond());
                }
                else {
                    double rate = rateMapper.get(entry.getKey().getEntity()).getRate();
                    curve = CurvePwAffine.min(
                            CurvePwAffine.getFactory().createTokenBucket(entry.getValue().getFirst(), entry.getValue().getSecond()),
                            CurvePwAffine.getFactory().createTokenBucket(rate, modelingConfig.getMaximumPacketSize())
                    );
                }

                aggregatedArrivalCurve = CurvePwAffine.add(aggregatedArrivalCurve, curve);
            }

			JSONObject curveJSON = DiscoCurveToJSON.get(aggregatedArrivalCurve);
			curveJSON.getJSONObject("plotting").put("name", "Aggregated Shaped Arrival Curve");
			result.put("Input data from links", JSONUtil.merge(result.getJSONObject("Input data from links"), curveJSON));
		}

		return result;
	}

	@Override
	public JSONObject toJSONObject(Entity entity) {
		JSONObject result = super.toJSONObject(entity);

		ArrivalCurve usageCurve = null;
		ServiceCurve limitCurve = null;

		if(inputModelMapper.isIn(entity)) {
			PerInEdgeTokenBucketUtilization perInEdgeTokenBucketUtilization = inputModelMapper.get(entity);

			DetServConfig modelingConfig = getModelingConfig(edgeMapper.get(entity).getSource().getGraph().getEntity());

			usageCurve = CurvePwAffine.getFactory().createTokenBucket(0, 0);
			for(Map.Entry<Edge, Pair<Num>> entry : perInEdgeTokenBucketUtilization.getTokenBuckets()) {
                ArrivalCurve curve;
                // No shaping if input edge is current one
			    if(entry.getKey() == linkMapper.getOptimistic(toNetworkMapper.getOptimistic(entity).getNetworkEntity()).getLinkEdge()) {
                    curve = CurvePwAffine.getFactory().createTokenBucket(entry.getValue().getFirst(), entry.getValue().getSecond());
                }
                else {
                    double rate = rateMapper.get(entry.getKey().getEntity()).getRate();
                    curve = CurvePwAffine.min(
                            CurvePwAffine.getFactory().createTokenBucket(entry.getValue().getFirst(), entry.getValue().getSecond()),
                            CurvePwAffine.getFactory().createTokenBucket(rate, modelingConfig.getMaximumPacketSize())
                    );
                }

				usageCurve = CurvePwAffine.add(usageCurve, curve);
			}
		}

		if(singleTokenBucketMapper.isIn(entity))
			usageCurve = CurvePwAffine.getFactory().createTokenBucket(singleTokenBucketMapper.get(entity).getRate(), singleTokenBucketMapper.get(entity).getBurst());

		if(queueModelMapper.isIn(entity))
			limitCurve = queueModelMapper.get(entity).getServiceCurve();

		if(usageCurve != null && limitCurve != null) {
			JSONObject measures = new JSONObject();
			measures.put("horizontal_deviation", Double.toString(Bound.delayFIFO(usageCurve, limitCurve).doubleValue()*1000)); // ms
			measures.put("vertical_deviation", Double.toString(Bound.backlog(usageCurve, limitCurve).doubleValue()/1000.0)); // KB

			Num intersection = CurvePwAffine.getXIntersection(usageCurve, limitCurve);
			measures.put("intersection", intersection.doubleValue() * 1000); // ms
			measures.put("class", "Measures");
			result.put("boguscomponent", measures);
		}

		return result;
	}
}
