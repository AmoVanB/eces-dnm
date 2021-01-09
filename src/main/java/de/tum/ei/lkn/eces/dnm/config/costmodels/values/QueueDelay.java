package de.tum.ei.lkn.eces.dnm.config.costmodels.values;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.core.Mapper;
import de.tum.ei.lkn.eces.dnm.config.CostModel;
import de.tum.ei.lkn.eces.graph.Edge;
import de.tum.ei.lkn.eces.network.Delay;
import de.tum.ei.lkn.eces.network.mappers.DelayMapper;
import de.tum.ei.lkn.eces.routing.requests.Request;

/**
 * Cost is the delay of the queue (or a link).
 *
 * @author Jochen Guck
 * @author Amaury Van Bemten
 */
public class QueueDelay extends Base {
	private Mapper<Delay> delayMapper;

	public QueueDelay() {
	}

	public QueueDelay(Controller controller) {
		super(controller);
		delayMapper = new DelayMapper(controller);
	}

	@Override
	public CostModel init(Controller controller) {
		return new QueueDelay(controller);
	}

	@Override
	public double getCost(Iterable<Edge> iterable, Edge edge, double[] doubles, Request request, boolean isFroward) {
		return delayMapper.getOptimistic(edge.getEntity()).getDelay();
	}

	@Override
	public double minCostValue() {
		return 0;
	}

	@Override
	public double maxCostValue()  {
		return Double.MAX_VALUE;
	}
}
