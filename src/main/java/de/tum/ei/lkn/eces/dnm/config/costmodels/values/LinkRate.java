package de.tum.ei.lkn.eces.dnm.config.costmodels.values;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.core.Mapper;
import de.tum.ei.lkn.eces.dnm.config.CostModel;
import de.tum.ei.lkn.eces.graph.Edge;
import de.tum.ei.lkn.eces.network.Rate;
import de.tum.ei.lkn.eces.network.mappers.RateMapper;
import de.tum.ei.lkn.eces.routing.requests.Request;

/**
 * Cost is the link rate.
 *
 * @author Jochen Guck
 * @author Amaury Van Bemten
 */
public class LinkRate extends Base {
	private Mapper<Rate> rateMapper;

	public LinkRate() {
	}

	public LinkRate(Controller controller) {
		super(controller);
		rateMapper = new RateMapper(controller);
	}
	@Override
	public CostModel init(Controller controller) {
		return new LinkRate(controller);
	}

	@Override
	public double getCost(Iterable<Edge> iterable, Edge edge, double[] doubles, Request request, boolean isFroward) {
		return rateMapper.getOptimistic(this.getSchedulerEntity(edge)).getRate();
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
