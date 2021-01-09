package de.tum.ei.lkn.eces.dnm.config.costmodels.functions;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.dnm.config.CostModel;
import de.tum.ei.lkn.eces.graph.Edge;
import de.tum.ei.lkn.eces.routing.requests.Request;

/**
 * Lower bounds a cost model.
 *
 * @author Jochen Guck
 * @author Amaury Van Bemten
 */
public class LowerLimit extends CostModel {
	private CostModel costModel;
	private double limit;

	public LowerLimit(CostModel costModel, double limit) {
		this.costModel = costModel;
		this.limit = limit;
	}

	@Override
	public CostModel init(Controller controller) {
		CostModel newCostModel = costModel.init(controller);
		return new LowerLimit(newCostModel, limit);
	}

	@Override
	public double getCost(Iterable<Edge> iterable, Edge edge, double[] doubles, Request request, boolean isFroward) {
		return Math.max(limit, costModel.getCost(iterable, edge, doubles, request, isFroward));
	}

	@Override
	public double minCostValue() {
		return Math.max(limit, costModel.minCostValue());
	}

	@Override
	public double maxCostValue()  {
		return  costModel.maxCostValue();
	}

	@Override
	public boolean containsCostModel(Class clazz){
        return super.containsCostModel(clazz) || costModel.containsCostModel(clazz);
    }

	@Override
	public String toString(){
		return this.getClass().getSimpleName() + "( " + costModel + "; " + limit + " )";
	}
}
