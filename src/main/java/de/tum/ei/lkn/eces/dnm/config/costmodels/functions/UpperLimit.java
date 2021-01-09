package de.tum.ei.lkn.eces.dnm.config.costmodels.functions;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.dnm.config.CostModel;
import de.tum.ei.lkn.eces.graph.Edge;
import de.tum.ei.lkn.eces.routing.requests.Request;

/**
 * Upper bounds a cost model.
 *
 * @author Jochen Guck
 * @author Amaury Van Bemten
 */
public class UpperLimit extends CostModel {
	private CostModel costModel;
	private double limit;

	public UpperLimit(CostModel costModel, double limit) {
		this.costModel = costModel;
		this.limit = limit;
	}

	@Override
	public CostModel init(Controller controller) {
		CostModel newCostModel = costModel.init(controller);
		return new UpperLimit(newCostModel, limit);
	}

	@Override
	public double getCost(Iterable<Edge> iterable, Edge edge, double[] parameters, Request request, boolean isFroward) {
		return Math.min(limit, costModel.getCost(iterable, edge, parameters, request, isFroward));
	}

	@Override
	public double minCostValue() {
		return costModel.minCostValue();
	}

	@Override
	public double maxCostValue()  {
		return Math.min(limit, costModel.maxCostValue());
	}

	@Override
	public boolean containsCostModel(Class clazz) {
        return super.containsCostModel(clazz) || costModel.containsCostModel(clazz);
    }

	@Override
	public String toString(){
		return this.getClass().getSimpleName() + "( " + costModel + "; " + limit + " )";
	}
}
