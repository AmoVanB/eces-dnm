package de.tum.ei.lkn.eces.dnm.proxies;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.core.Entity;
import de.tum.ei.lkn.eces.core.Mapper;
import de.tum.ei.lkn.eces.dnm.NCRequestData;
import de.tum.ei.lkn.eces.dnm.config.QJumpConfig;
import de.tum.ei.lkn.eces.dnm.mappers.NCRequestDataMapper;
import de.tum.ei.lkn.eces.dnm.mappers.QJumpConfigMapper;
import de.tum.ei.lkn.eces.graph.Edge;
import de.tum.ei.lkn.eces.graph.Node;
import de.tum.ei.lkn.eces.routing.distancevector.DistanceVector;
import de.tum.ei.lkn.eces.routing.distancevector.DistanceVectorMapper;
import de.tum.ei.lkn.eces.routing.proxies.PathProxy;
import de.tum.ei.lkn.eces.routing.proxies.ProxyTypes;
import de.tum.ei.lkn.eces.routing.requests.Request;
import de.tum.ei.lkn.eces.routing.responses.Path;
import de.uni_kl.cs.discodnc.curves.ArrivalCurve;
import de.uni_kl.cs.discodnc.numbers.Num;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Proxy implementing the QJump [1] access control.
 *
 * The configuration (QJumpConfig) of the proxy (nb of hosts, link rate, etc.) is assumed
 * stored on the Entity of the graph.
 *
 * [1] Grosvenor, Matthew P., Malte Schwarzkopf, Ionel Gog, Robert NM Watson, Andrew W. Moore, Steven Hand, and
 * Jon Crowcroft. "Queues Don’t Matter When You Can JUMP Them!." In 12th USENIX Symposium on Networked Systems
 * Design and Implementation (NSDI 15), pp. 1-14. 2015.
 *
 * @author Amaury Van Bemten
 */
public class QJumpProxy extends PathProxy {
    private final Logger logger;

    private Map<Entity, Path> acceptedFlows;

    // Mappers
	private Mapper<NCRequestData> ncRequestDataMapper;
    private Mapper<DistanceVector> distanceVectorMapper;
	private Mapper<QJumpConfig> qJumpConfigMapper;

    public QJumpProxy(Controller controller) {
		logger = Logger.getLogger(this.getClass());

		acceptedFlows = new HashMap<>();

		ncRequestDataMapper = new NCRequestDataMapper(controller);
		distanceVectorMapper = new DistanceVectorMapper(controller);
		qJumpConfigMapper = new QJumpConfigMapper(controller);
	}

	/**
	 * @param edge a given edge.
	 * @return The QJump config for (the graph of) this edge.
	 */
	private QJumpConfig getConfig(Edge edge) {
	    return qJumpConfigMapper.get(edge.getSource().getGraph().getEntity());
    }

	@Override
	public double[] getNewParameters(Iterable<Edge> iterable, Edge edge, double[] parameters, Request request, boolean isFroward) {
		// No parameters
		return new double[0];
	}

	@Override
	public boolean hasAccess(Iterable<Edge> iterable, Edge edge, double[] parameters, Request request, boolean isFroward) {
		NCRequestData ncRequestData = ncRequestDataMapper.get(request.getEntity());
		ArrivalCurve requestTokenBucket = ncRequestData.getTb();

        logger.info("Checking access to " + edge + " from " + iterable + " for " + request + " with token bucket " + requestTokenBucket);

		QJumpConfig config = getConfig(edge);

		// Checking if we already reached the max number of hosts
		if(acceptedFlows.size() >= config.getMaxNumberOfHosts()) {
			logger.debug("There is already " + acceptedFlows.size() + " in the network!");
			return false;
		}

		// Checking if flow is not too big
		if(requestTokenBucket.getUltAffineRate().gt(Num.getFactory().create(config.getMaximumRate())) || requestTokenBucket.getBurst().gt(Num.getFactory().create(config.getMaximumPacketSize()))) {
			logger.debug("The rate of burst of the flow is too high for the configuration!");
			return false;
		}

		// Checking delay guarantee
		if(ncRequestData.getDeadline().lt(Num.getFactory().create(config.getGuaranteedDelay()))) {
			logger.debug("The deadline of the flow is too low: " + ncRequestData.getDeadline() + " < " + config.getGuaranteedDelay());
			return false;
		}

		return true;
	}

	public int getNumberOfFlows() {
	    return this.acceptedFlows.size();
    }

	@Override
	public double getCost(Iterable<Edge> iterable, Edge edge, double[] parameters, Request request, boolean isFroward) {
		// Shortest path.
		return 1;
	}

	@Override
	public double[] getConstraintsValues(Iterable<Edge> iterable, Edge edge, double[] doubles, Request request, boolean isFroward) {
		return new double[0];
	}

	// We only accept the registration of a complete path.
	@Override
	public boolean register(Iterable<Edge> iterable, Edge edge, double[] doubles, Request request) {
		return false;
	}

    // We only accept the registration of a complete path.
    @Override
	public boolean deregister(Iterable<Edge> iterable, Edge edge, double[] doubles, Request request) {
		return false;
	}

	@Override
	public boolean register(Path path, Request request) {
		// Put returning null means there was no mapping yet
		return (this.acceptedFlows.put(request.getEntity(), path) == null);
	}

	@Override
	public boolean deregister(Path path, Request request) {
		// Remove returns not null if it removed something
		return (this.acceptedFlows.remove(request.getEntity()) != null);
	}

	@Override
	public boolean handle(Request request, boolean b) {
		return ncRequestDataMapper.isIn(request.getEntity()) && qJumpConfigMapper.isIn(request.getGraph().getEntity());
	}

	@Override
	public int getNumberOfConstraints(Request request) {
		// We do not put delay as a constraint as it is not per-edge.
		// We just have it in the has access directly.
		return 0;
	}

	@Override
	public int getNumberOfParameters(Request request) {
		return 0;
	}

	@Override
	public double[] getConstraintsBounds(Request request) {
		return new double[0];
	}

	@Override
	public ProxyTypes getType() {
	    // It's just shortest path and the access is same for all edges.
	    return ProxyTypes.EDGE_PROXY;
	}

	@Override
	public double getGuessForCost(Node source, Node destination) {
        return getHopCount(source, destination);
	}

	@Override
	public double getGuessForConstraint(int index, Node source, Node destination) {
		return 0;
	}

	private double getHopCount(Node source, Node destination) {
        DistanceVector distanceVector = distanceVectorMapper.getOptimistic(source.getEntity());
        if(distanceVector == null)
            return 0; // a guess of 0 is always fine.
        else
            return distanceVector.getDistance(destination);
	}
}

