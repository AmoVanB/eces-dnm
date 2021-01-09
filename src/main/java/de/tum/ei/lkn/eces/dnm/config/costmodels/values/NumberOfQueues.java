package de.tum.ei.lkn.eces.dnm.config.costmodels.values;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.dnm.config.CostModel;
import de.tum.ei.lkn.eces.graph.Edge;
import de.tum.ei.lkn.eces.routing.requests.Request;

/**
 * Cost is the number queues at a link.
 *
 * @author Amaury Van Bemten
 */
public class NumberOfQueues extends Base {
	public NumberOfQueues() {
	}

	public NumberOfQueues(Controller controller) {
		super(controller);
	}

	@Override
	public CostModel init(Controller controller) {
		return new NumberOfQueues(controller);
	}

	@Override
	public double getCost(Iterable<Edge> iterable, Edge edge, double[] doubles, Request request, boolean isFroward) {
		return getSchedulerMapper().get(getSchedulerEntity(edge)).getQueues().length;
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
