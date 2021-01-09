package de.tum.ei.lkn.eces.dnm.proxies;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.core.Mapper;
import de.tum.ei.lkn.eces.dnm.NCRequestData;
import de.tum.ei.lkn.eces.dnm.config.BurstIncreaseModel;
import de.tum.ei.lkn.eces.dnm.config.DetServConfig;
import de.tum.ei.lkn.eces.dnm.exceptions.DNMException;
import de.tum.ei.lkn.eces.dnm.inputmodels.PerInEdgeTokenBucketUtilization;
import de.tum.ei.lkn.eces.dnm.inputmodels.ResourceUtilization;
import de.tum.ei.lkn.eces.dnm.inputmodels.TokenBucketUtilization;
import de.tum.ei.lkn.eces.dnm.mappers.*;
import de.tum.ei.lkn.eces.dnm.queuemodels.MHMQueueModel;
import de.tum.ei.lkn.eces.dnm.queuemodels.QueueModel;
import de.tum.ei.lkn.eces.graph.Edge;
import de.tum.ei.lkn.eces.graph.Node;
import de.tum.ei.lkn.eces.graph.mappers.EdgeMapper;
import de.tum.ei.lkn.eces.network.*;
import de.tum.ei.lkn.eces.network.mappers.*;
import de.tum.ei.lkn.eces.routing.distancevector.DistanceVector;
import de.tum.ei.lkn.eces.routing.distancevector.DistanceVectorMapper;
import de.tum.ei.lkn.eces.routing.proxies.PathProxy;
import de.tum.ei.lkn.eces.routing.proxies.ProxyTypes;
import de.tum.ei.lkn.eces.routing.requests.Request;
import de.tum.ei.lkn.eces.routing.responses.Path;
import de.uni_kl.cs.discodnc.curves.ArrivalCurve;
import de.uni_kl.cs.discodnc.curves.CurvePwAffine;
import de.uni_kl.cs.discodnc.curves.ServiceCurve;
import de.uni_kl.cs.discodnc.misc.Pair;
import de.uni_kl.cs.discodnc.nc.bounds.Bound;
import de.uni_kl.cs.discodnc.numbers.Num;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

/**
 * Proxy implementing the DetServ [1] NC models (TBM includes Silo [2]).
 *
 * [1] Guck, Jochen W., Amaury Van Bemten, and Wolfgang Kellerer. "DetServ: Network models for real-time QoS
 * provisioning in SDN-based industrial environments." IEEE Transactions on Network and Service Management 14, no. 4
 * (2017): 1003-1017.
 * [2] Jang, Keon, Justine Sherry, Hitesh Ballani, and Toby Moncaster. "Silo: Predictable message latency in the cloud."
 * ACM SIGCOMM Computer Communication Review 45, no. 4 (2015): 435-448.
 *
 * The configuration (DetServConfig) of the proxy (model, cost function, etc.) is assumed
 * stored on the Entity of the graph.
 */
public class DetServProxy extends PathProxy {
    private final Logger logger;

    // Mappers
	private Mapper<Edge> edgeMapper;
	private Mapper<NCRequestData> ncRequestDataMapper;
    private Mapper<Link> linkMapper;
	private Mapper<ToNetwork> toNetworkMapper;
	private Mapper<Delay> delayMapper;
	private Mapper<Rate> rateMapper;
	private Mapper<ResourceUtilization> resourceUtilizationMapper;
	private Mapper<QueueModel> queueModelMapper;
	private Mapper<Scheduler> schedulerMapper;
    private Mapper<DistanceVector> distanceVectorMapper;
	private Mapper<MHMQueueModel> MHMQueueModelMapper;
	private Mapper<DetServConfig> modelingConfigMapper;

    public DetServProxy(Controller controller) {
		edgeMapper = new EdgeMapper(controller);
		ncRequestDataMapper = new NCRequestDataMapper(controller);
		linkMapper = new LinkMapper(controller);
		toNetworkMapper = new ToNetworkMapper(controller);
		delayMapper = new DelayMapper(controller);
		rateMapper = new RateMapper(controller);
		resourceUtilizationMapper = new ResourceUtilizationMapper(controller);
		queueModelMapper = new QueueModelMapper(controller);
		MHMQueueModelMapper = new MHMQueueModelMapper(controller);
		schedulerMapper = new SchedulerMapper(controller);
		distanceVectorMapper = new DistanceVectorMapper(controller);
		modelingConfigMapper = new DetServConfigMapper(controller);
        logger = Logger.getLogger(this.getClass());
	}

	/**
	 * @param edge a given edge.
	 * @return The modeling config for (the graph of) this edge.
	 */
	private DetServConfig getConfig(Edge edge) {
	    return modelingConfigMapper.get(edge.getSource().getGraph().getEntity());
    }

    /**
     * @param queueEdge a given queue Edge.
     * @return The corresponding physical edge.
     */
    private Edge getPhysicalEdge(Edge queueEdge) {
        if(queueEdge == null)
            return null;
        return linkMapper.getOptimistic(toNetworkMapper.getOptimistic(queueEdge.getEntity()).getNetworkEntity()).getLinkEdge();
    }

    /**
     * @param queueEdge a given queue Edge.
     * @return The worst-case delay for this queue Edge.
     */
    private double getWorstCaseDelay(Edge queueEdge) {
        return delayMapper.getOptimistic(queueEdge.getEntity()).getDelay();
    }

	@Override
	public double[] getNewParameters(Iterable<Edge> iterable, Edge edge, double[] parameters, Request request, boolean isFroward) {
		/* The proxy just uses zero or one parameter: the delay so far.
		 *
		 * We only compute it when used: when the burst increase is computed with the delay so far.
		 *
		 * This function returns the delay after 'edge'. */

	    DetServConfig config = getConfig(edge);

		if(config.getBurstIncrease() == BurstIncreaseModel.REAL || config.getBurstIncrease() == BurstIncreaseModel.WORST_CASE_BURST_REAL_RESERVATION) {
			Iterator<Edge> iterator;
			if(iterable != null && (iterator = iterable.iterator()).hasNext()) {
				if(parameters == null || parameters.length == 0)
					return new double[]{getWorstCaseDelay(iterator.next())};
				else
					return new double[]{getWorstCaseDelay(iterator.next()) + parameters[0]};
			}
			else
				return  new double[]{ 0.0 };
		}
		else
			return new double[0];
	}

	@Override
	public boolean hasAccess(Iterable<Edge> iterable, Edge edge, double[] parameters, Request request, boolean isFroward) {
        logger.info("Checking access to " + edge + " from " + iterable + " for " + request + " with parameters " + Arrays.toString(parameters));

		Edge physicalEdge = null;
		if(iterable != null) {
            Iterator<Edge> iterator = iterable.iterator();
            if(iterable.iterator().hasNext())
                physicalEdge = getPhysicalEdge(iterator.next());
        }

		logger.trace("Computed physical edge: " + physicalEdge);

		return universalAccessCheck(physicalEdge, edge, parameters, request);
	}

	private boolean universalAccessCheck(Edge physicalInputEdge, Edge edge, double[] parameters, Request request) {
        DetServConfig config = getConfig(edge);
        NCRequestData ncRequestData = ncRequestDataMapper.get(request.getEntity());
        ArrivalCurve requestTokenBucket = ncRequestData.getTb();

        logger.debug("Request: b=" + requestTokenBucket.getBurst() + " r=" + requestTokenBucket.getUltAffineRate());

        // Add burst increase
        logger.debug("Burst increase configuration: " + config.getBurstIncrease());
        Num burstIncrease = null;
        if(config.getBurstIncrease() == BurstIncreaseModel.WORST_CASE_BURST || config.getBurstIncrease() == BurstIncreaseModel.WORST_CASE_BURST_REAL_RESERVATION) {
            // If it's the first edge, don't consider that burst increased
            if(physicalInputEdge != null)
                burstIncrease = Num.getFactory().mult(requestTokenBucket.getUltAffineRate(), ncRequestData.getDeadline());
        }
        else if(config.getBurstIncrease() == BurstIncreaseModel.REAL) {
            double delaySoFar = parameters[0];
            burstIncrease = Num.getFactory().mult(requestTokenBucket.getUltAffineRate(), Num.getFactory().create(delaySoFar));
        }

        if(burstIncrease == null)
        	burstIncrease = Num.getFactory().create(0);
        else
            requestTokenBucket = CurvePwAffine.add(requestTokenBucket, burstIncrease);

        logger.debug("Physical input edge: " + physicalInputEdge);
        logger.debug("Burst increase: " + burstIncrease);

		// For all reservation stuff, we use the current physical edge as input edge if there is no previous edge
		if(physicalInputEdge == null)
			physicalInputEdge = getPhysicalEdge(edge);

		switch(config.getAcModel()) {
			case MHM: {
				Num currentBurstUsage = ((TokenBucketUtilization) this.resourceUtilizationMapper.getOptimistic(edge.getEntity())).getBurst();
				Num currentRateUsage = ((TokenBucketUtilization) this.resourceUtilizationMapper.getOptimistic(edge.getEntity())).getRate();
				ArrivalCurve maxQueueUsage = this.MHMQueueModelMapper.getOptimistic(edge.getEntity()).getMaximumTokenBucket();
				ArrivalCurve currentQueueUsage = CurvePwAffine.getFactory().createTokenBucket(currentRateUsage, currentBurstUsage);

				logger.debug("MHM: Current usage: " + currentQueueUsage);
				logger.debug("MHM: Request TB: " + requestTokenBucket);
				logger.debug("MHM: Max usage: " + maxQueueUsage);
				logger.debug(Num.getFactory().add(currentQueueUsage.getUltAffineRate(), requestTokenBucket.getUltAffineRate()));
                logger.debug(Num.getFactory().add(currentQueueUsage.getBurst(), requestTokenBucket.getBurst()));

				if(Num.getFactory().add(currentQueueUsage.getUltAffineRate(), requestTokenBucket.getUltAffineRate()).leq(maxQueueUsage.getUltAffineRate()) &&
						Num.getFactory().add(currentQueueUsage.getBurst(), requestTokenBucket.getBurst()).leq(maxQueueUsage.getBurst())) {
					logger.info("MHM: Access granted!");
					return true;
				}
				else {
					logger.info(String.format("Maximum burst or rate violated: %s + %s > %s OR %s + %s > %s", currentQueueUsage.getUltAffineRate(), requestTokenBucket.getUltAffineRate(), maxQueueUsage.getUltAffineRate(), currentQueueUsage.getBurst(), requestTokenBucket.getBurst(), maxQueueUsage.getBurst()));
					return false;
				}
            }

			case TBM: {
				Scheduler scheduler = this.schedulerMapper.getOptimistic(this.getPhysicalEdge(edge).getEntity());
				if (!(scheduler instanceof PriorityScheduler))
                    throw new DNMException("Scheduler of type " + scheduler.getClass().getTypeName() + " is not supported");

				// Whether we reached the queue where the flow is to be added or not
                boolean reachedTargetQueue = false;
                // Whether we are checking the queue where the flow is to be added (true), or lower priority ones (false)
                boolean checkingTheTargetQueue = true;
                ServiceCurve serviceCurve = null;
                for (int i = 0; i < scheduler.getQueues().length; i++) {
                    // Looping from high priority to low waiting for the queue to check.
                    if (!reachedTargetQueue) {
                        if(scheduler.getQueues()[i].getEntity() != edge.getEntity())
                            continue;
                        else
                            reachedTargetQueue = true;
                            serviceCurve = this.queueModelMapper.getOptimistic(edge.getEntity()).getServiceCurve();
                    }

                    logger.debug("Checking queue " + i + " (target queue: " + checkingTheTargetQueue + ")");

                    // Computing the arrival curve for the current queue.
                    ResourceUtilization currentQueueUtilization = this.resourceUtilizationMapper.getOptimistic(scheduler.getQueues()[i].getEntity());
                    ArrivalCurve currentQueueArrivalCurve = null;
                    if (currentQueueUtilization instanceof TokenBucketUtilization) {
                        if (checkingTheTargetQueue) {
                            // Add flow curve to the current utilization of the target queue.
                            currentQueueArrivalCurve = CurvePwAffine.add(requestTokenBucket, CurvePwAffine.getFactory().createTokenBucket(((TokenBucketUtilization) currentQueueUtilization).getRate(), ((TokenBucketUtilization) currentQueueUtilization).getBurst()));
                            checkingTheTargetQueue = false;
                        }
                        else {
                            // For other queues, arrival curve is the already existing one.
                            currentQueueArrivalCurve = CurvePwAffine.getFactory().createTokenBucket(((TokenBucketUtilization) currentQueueUtilization).getRate(), ((TokenBucketUtilization) currentQueueUtilization).getBurst());
                        }
                    }
                    else if(currentQueueUtilization instanceof PerInEdgeTokenBucketUtilization) {
                        // We compute all the shaped arrival curves for each incoming edge.
                        LinkedList<ArrivalCurve> shapedIncomingArrivalCurves = new LinkedList<>();

                        boolean physicalInputEdgeIsInResourceMap = false;
                        for(Map.Entry<Edge, Pair<Num>> currentIncomingEdgeEntry : ((PerInEdgeTokenBucketUtilization) currentQueueUtilization).getTokenBuckets()) {
                            Pair<Num> incomingTbForCurrentEdge = currentIncomingEdgeEntry.getValue();
							ArrivalCurve incomingEdgeArrivalCurve = CurvePwAffine.getFactory().createTokenBucket(incomingTbForCurrentEdge.getFirst(), incomingTbForCurrentEdge.getSecond());
							Edge currentIncomingEdge = currentIncomingEdgeEntry.getKey();

							ArrivalCurve unshapedTbForCurrentEdge = null;
                            if (checkingTheTargetQueue && currentIncomingEdge == physicalInputEdge) {
                                // Add flow curve to the current utilization of the target queue and for the correct input link.
								unshapedTbForCurrentEdge = CurvePwAffine.add(incomingEdgeArrivalCurve, requestTokenBucket);
								physicalInputEdgeIsInResourceMap = true;
                            } else {
                                // For other queues, arrival curve is the already existing one.
                                unshapedTbForCurrentEdge = incomingEdgeArrivalCurve;
                            }

                            // Add the arrival curve shaped with input link rate
							// Except if traffic is local: then no shaping.
                            if(currentIncomingEdge == getPhysicalEdge(edge))
                                shapedIncomingArrivalCurves.add(unshapedTbForCurrentEdge);
                            else
                                shapedIncomingArrivalCurves.add(CurvePwAffine.min(CurvePwAffine.getFactory().createTokenBucket(rateMapper.getOptimistic(currentIncomingEdge.getEntity()).getRate(), config.getMaximumPacketSize()), unshapedTbForCurrentEdge));
                        }

                        /* If the physical input edge was not in the map, the request token bucket was not added yet.
                         * Hence, we add it now (and shape it if it's not local traffic)
                         */
						if(!physicalInputEdgeIsInResourceMap && checkingTheTargetQueue) {
							if(physicalInputEdge == getPhysicalEdge(edge))
								shapedIncomingArrivalCurves.add(requestTokenBucket);
							else
								shapedIncomingArrivalCurves.add(CurvePwAffine.min(CurvePwAffine.getFactory().createTokenBucket(rateMapper.getOptimistic(physicalInputEdge.getEntity()).getRate(), config.getMaximumPacketSize()), requestTokenBucket));
						}

                        currentQueueArrivalCurve = CurvePwAffine.getFactory().createTokenBucket(0, 0);
                        for(ArrivalCurve curve : shapedIncomingArrivalCurves)
							currentQueueArrivalCurve = CurvePwAffine.add(currentQueueArrivalCurve, curve);
                    }
                    else
                        throw new DNMException(currentQueueUtilization.getClass().getTypeName() + " is not supported!");

                    logger.debug("Arrival curve: " + currentQueueArrivalCurve);
                    logger.debug("Service curve: " + serviceCurve);
                    logger.debug("Buffer limit: " + scheduler.getQueues()[i].getSize());
                    logger.debug("Delay limit: " + getWorstCaseDelay(edgeMapper.get(scheduler.getQueues()[i].getEntity())));

                    Num delayBound = Bound.delayFIFO(currentQueueArrivalCurve, serviceCurve);
                    if(delayBound.geq(Num.getFactory().create(getWorstCaseDelay(edgeMapper.get(scheduler.getQueues()[i].getEntity()))))) {
						logger.debug("Delay bound is exceeded for this queue, access denied!");
						return false;
					}

					Num backlogBound = Bound.backlog(currentQueueArrivalCurve, serviceCurve);
					if(backlogBound.geq(Num.getFactory().create(scheduler.getQueues()[i].getSize()))) {
						logger.debug("Backlog bound is exceeded for this queue, access denied!");
						return false;
					}

                    logger.trace("This queue is fine!");

					// If we were checking the target queue, now we're done with it!
					if(checkingTheTargetQueue)
						checkingTheTargetQueue = false;

                    // Getting curve of next queue (cannot use the existing one as it has changed with the new flow)
                    serviceCurve = config.getResidualMode().getResidualServiceCurve(serviceCurve, currentQueueArrivalCurve);
				}

                logger.debug("Access granted!");
                return true;
            }
			default:
				throw new DNMException("Access control model " + config.getAcModel() + " is not supported!");
		}
	}

	@Override
	public double getCost(Iterable<Edge> iterable, Edge edge, double[] parameters, Request request, boolean isFroward) {
        DetServConfig config = getConfig(edge);
        logger.trace("Computing cost with config " + config.getCostModel());
        double cost = config.getCostModel().getCost(iterable, edge, parameters, request, isFroward);
        logger.trace("Cost: " + cost);
        return cost;
	}

	@Override
	public double[] getConstraintsValues(Iterable<Edge> iterable, Edge edge, double[] doubles, Request request, boolean isFroward) {
    	logger.trace("Getting constraints values");
    	double delayFromQueueEdge = delayMapper.getOptimistic(edge.getEntity()).getDelay();
    	double delayFromLinkEdge = delayMapper.getOptimistic(
				linkMapper.getOptimistic(
						toNetworkMapper.getOptimistic(edge.getEntity()).getNetworkEntity()
				).getLinkEdge().getEntity()
		).getDelay();
		double[] result = new double[]{delayFromQueueEdge + delayFromLinkEdge};
		logger.trace("Got " + delayFromQueueEdge + " + " + delayFromLinkEdge + " = " + Arrays.toString(result) + " from the delay components attached to the queue and link edges");
		return result;
	}

	// We only accept the registration of a complete path.
	@Override
	public boolean register(Iterable<Edge> iterable, Edge edge, double[] doubles, Request request) {
		throw new DNMException("We only accept the registration of a complete path");
	}

    // We only accept the registration of a complete path.
    @Override
	public boolean deregister(Iterable<Edge> iterable, Edge edge, double[] doubles, Request request) {
		throw new DNMException("We only accept the registration of a complete path");
	}

	@Override
	public boolean register(Path path, Request request) {
		return registerDeregister(path, request, true);
	}

	@Override
	public boolean deregister(Path path, Request request) {
		return registerDeregister(path, request, false);
	}

    /**
     * Registers or deregisters a request from a path.
     * @param path the Path.
     * @param request the Request.
     * @param register true if to be registered, false if to be deregistered.
     * @return always returns true except in case of RuntimeException.
     */
	private boolean registerDeregister(Path path, Request request, boolean register) {
        DetServConfig config = getConfig(path.getPath()[0]);
        NCRequestData ncRequestData = ncRequestDataMapper.getOptimistic(request.getEntity());
		ArrivalCurve flowTokenBucket = ncRequestData.getTb();

		// Going through the edges
        LinkedList<Edge> pathSoFar = null;
        double[] delaySoFar = new double[]{0.0};
        Edge previousEdge = null;

        for(Edge edge : path.getPath()) {
            // Computing the delay so far
            if(config.getBurstIncrease() == BurstIncreaseModel.REAL || config.getBurstIncrease() == BurstIncreaseModel.WORST_CASE_BURST_REAL_RESERVATION)
                    delaySoFar = this.getNewParameters(pathSoFar, edge, delaySoFar, request,true);

            // For worst-case burst, the delay so far is constant and corresponds to the deadline (except for the first link)
            if(config.getBurstIncrease() == BurstIncreaseModel.WORST_CASE_BURST) {
                if(pathSoFar == null)
                    delaySoFar = new double[]{0.0};
                else
                    delaySoFar = new double[]{ncRequestData.getDeadline().doubleValue()};
            }

            // Computing current arrival curve
            ArrivalCurve arrivalCurveForThisEdge;
            if(delaySoFar[0] == 0.0)
				arrivalCurveForThisEdge = flowTokenBucket;
			else
				arrivalCurveForThisEdge = CurvePwAffine.add(flowTokenBucket, Num.getFactory().mult(flowTokenBucket.getUltAffineRate(), Num.getFactory().create(delaySoFar[0])));

			// Effectively adding/removing token bucket (service curve will be updated by the listener)
			final ArrivalCurve tokenBucketToAddOrRemove = arrivalCurveForThisEdge;
			ResourceUtilization resourceUtilization = resourceUtilizationMapper.get(edge.getEntity());
			if (resourceUtilization instanceof TokenBucketUtilization) {
				if(register)
					resourceUtilizationMapper.updateComponent(resourceUtilization,
							()->((TokenBucketUtilization) resourceUtilization).addFlow(arrivalCurveForThisEdge.getUltAffineRate(), arrivalCurveForThisEdge.getBurst()));
				else
					resourceUtilizationMapper.updateComponent(resourceUtilization,
							()->((TokenBucketUtilization) resourceUtilization).removeFlow(arrivalCurveForThisEdge.getUltAffineRate(), arrivalCurveForThisEdge.getBurst()));
			}
			else if(resourceUtilization instanceof PerInEdgeTokenBucketUtilization) {
				final Edge physicalEdge;
				// We use the current edge for previous edge of traffic sourced at the current edge.
				if(previousEdge == null)
				    physicalEdge = getPhysicalEdge(edge);
				else
				    physicalEdge = getPhysicalEdge(previousEdge);

				if(register)
					resourceUtilizationMapper.updateComponent(resourceUtilization,
							()->((PerInEdgeTokenBucketUtilization) resourceUtilization).addFlow(physicalEdge, arrivalCurveForThisEdge.getUltAffineRate(), arrivalCurveForThisEdge.getBurst()));
				else
					resourceUtilizationMapper.updateComponent(resourceUtilization,
							()->((PerInEdgeTokenBucketUtilization) resourceUtilization).removeFlow(physicalEdge, arrivalCurveForThisEdge.getUltAffineRate(), arrivalCurveForThisEdge.getBurst()));
			}
			else {
				throw new DNMException("ResourceUtilization " + resourceUtilization.getClass().getTypeName() + " is not supported!");
			}

			if(pathSoFar == null)
			    pathSoFar = new LinkedList<>();
			pathSoFar.addFirst(edge); // because FIRST edge of iterator is expected to be the previous edge used
			previousEdge = edge;
		}

		return true;
	}

	@Override
	public boolean handle(Request request, boolean b) {
        return ncRequestDataMapper.isIn(request.getEntity()) && modelingConfigMapper.isIn(request.getGraph().getEntity());
    }

	@Override
	public int getNumberOfConstraints(Request request) {
		// Just delay
		return 1;
	}

	@Override
	public int getNumberOfParameters(Request request) {
        DetServConfig config = getConfig(request.getGraph().getEdges().iterator().next());

        switch (config.getBurstIncrease()) {
			case REAL:
			case WORST_CASE_BURST_REAL_RESERVATION:
				return 1;

			default:
				return 0;
		}
	}

	@Override
	public double[] getConstraintsBounds(Request request) {
		return new double[]{ncRequestDataMapper.getOptimistic(request.getEntity()).getDeadline().doubleValue()};
	}

	@Override
	public ProxyTypes getType() {
	    // worst-case
	    return ProxyTypes.PATH_PROXY;
	}

	@Override
	public double getGuessForCost(Node source, Node destination) {
        DetServConfig config = getConfig(source.getIncomingConnections().get(0));
        return config.getCostModel().minCostValue() * getHopCount(source, destination);
	}

	@Override
	public double getGuessForConstraint(int index, Node source, Node destination) {
		if(index == 0) {
            DetServConfig config = getConfig(source.getIncomingConnections().get(0));
            return config.getMinPerHopDelay() * getHopCount(source, destination);
		}
		else
		    throw new DNMException("The DetServProxy only considers one constraint!");
	}

	private double getHopCount(Node source, Node destination) {
        DistanceVector distanceVector = distanceVectorMapper.getOptimistic(source.getEntity());
        if(distanceVector == null)
            return 0; // a guess of 0 is always fine.
        else
            return distanceVector.getDistance(destination);
	}
}