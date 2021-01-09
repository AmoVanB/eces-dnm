package de.tum.ei.lkn.eces.dnm.config.costmodels.values;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.dnm.config.CostModel;
import de.tum.ei.lkn.eces.graph.Edge;
import de.tum.ei.lkn.eces.network.Queue;
import de.tum.ei.lkn.eces.network.Scheduler;
import de.tum.ei.lkn.eces.routing.requests.Request;

/**
 * Cost is the priority of a queue.
 *
 * @author Jochen Guck
 * @author Amaury Van Bemten
 */
public class QueuePriority extends Base {

	public QueuePriority() {
	}

	public QueuePriority(Controller controller) {
		super(controller);
	}

	@Override
	public CostModel init(Controller controller) {
		return new QueuePriority(controller);
	}

	@Override
	public double getCost(Iterable<Edge> iterable, Edge edge, double[] doubles, Request request, boolean isFroward) {
		Queue queue = getQueueMapper().getOptimistic(edge.getEntity());
		Scheduler scheduler = queue.getScheduler();
		for (int i = 0; i < scheduler.getQueues().length; i++)
			if (scheduler.getQueues()[i] == queue)
				return i;
		return 0;
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
