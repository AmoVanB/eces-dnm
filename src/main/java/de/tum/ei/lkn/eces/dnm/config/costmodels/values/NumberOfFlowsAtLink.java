package de.tum.ei.lkn.eces.dnm.config.costmodels.values;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.core.Entity;
import de.tum.ei.lkn.eces.dnm.config.CostModel;
import de.tum.ei.lkn.eces.graph.Edge;
import de.tum.ei.lkn.eces.network.Queue;
import de.tum.ei.lkn.eces.routing.requests.Request;

/**
 * Cost is the number of flows at a link.
 *
 * @author Jochen Guck
 * @author Amaury Van Bemten
 */
public class NumberOfFlowsAtLink extends NumberOfFlowsAtQueue {
	public NumberOfFlowsAtLink() {
	}

	public NumberOfFlowsAtLink(Controller controller) {
		super(controller);
	}

	@Override
	public CostModel init(Controller controller) {
		return new NumberOfFlowsAtLink(controller);
	}

	@Override
	public double getCost(Iterable<Edge> iterable, Edge edge, double[] doubles, Request request, boolean isFroward) {
		Entity entity = this.getSchedulerEntity(edge);
		int sum = 0;
		for(Queue queue : this.getSchedulerMapper().getOptimistic(entity).getQueues())
			sum += pathListMapper.getOptimistic(queue.getEntity()).getPathList().size();

		return sum;
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
