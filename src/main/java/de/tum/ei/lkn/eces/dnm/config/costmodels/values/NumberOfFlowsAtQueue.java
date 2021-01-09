package de.tum.ei.lkn.eces.dnm.config.costmodels.values;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.core.Mapper;
import de.tum.ei.lkn.eces.dnm.config.CostModel;
import de.tum.ei.lkn.eces.graph.Edge;
import de.tum.ei.lkn.eces.routing.mappers.PathListMapper;
import de.tum.ei.lkn.eces.routing.pathlist.PathList;
import de.tum.ei.lkn.eces.routing.requests.Request;

/**
 * Cost is the number of flows at a queue.
 *
 * @author Jochen Guck
 * @author Amaury Van Bemten
 */
public class NumberOfFlowsAtQueue extends Base {
	protected Mapper<PathList> pathListMapper;

	public NumberOfFlowsAtQueue() {
	}

	public NumberOfFlowsAtQueue(Controller controller) {
		super(controller);
		pathListMapper = new PathListMapper(controller);
	}

	@Override
	public CostModel init(Controller controller) {
		return new NumberOfFlowsAtQueue(controller);
	}

	@Override
	public double getCost(Iterable<Edge> iterable, Edge edge, double[] doubles, Request request, boolean isFroward) {
		return pathListMapper.getOptimistic(edge.getEntity()).getPathList().size();
	}

	@Override
	public double minCostValue() {
		return 0;
	}

	@Override
	public double maxCostValue() {
		return Double.MAX_VALUE;
	}
}
