package de.tum.ei.lkn.eces.dnm.config.costmodels.functions;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.dnm.config.CostModel;
import de.tum.ei.lkn.eces.graph.Edge;
import de.tum.ei.lkn.eces.routing.requests.Request;

/**
 * Division of two cost models.
 *
 * @author Jochen Guck
 * @author Amaury Van Bemten
 */
public class Division extends CostModel {
	private CostModel costModel1;
	private CostModel costModel2;

	public Division(CostModel costModel1, CostModel costModel2) {
		this.costModel1 = costModel1;
		this.costModel2 = costModel2;

	}

	@Override
	public CostModel init(Controller controller) {
		CostModel newCostModel1 = costModel1.init(controller);
		CostModel newCostModel2 = costModel2.init(controller);
		return new Division(newCostModel1, newCostModel2);
	}

	@Override
	public double getCost(Iterable<Edge> iterable, Edge edge, double[] doubles, Request request, boolean isFroward) {
		return costModel1.getCost(iterable, edge, doubles, request, isFroward)
				/ costModel2.getCost(iterable, edge, doubles, request, isFroward);
	}

	@Override
	public double minCostValue() {
		return costModel1.minCostValue() / costModel2.maxCostValue();
	}

	@Override
	public double maxCostValue()  {
		return costModel1.maxCostValue() / costModel2.minCostValue();
	}

	@Override
	public boolean containsCostModel(Class clazz){
        return super.containsCostModel(clazz) || costModel1.containsCostModel(clazz) || costModel2.containsCostModel(clazz);
    }

	@Override
	public String toString(){
		return this.getClass().getSimpleName() + "( " + costModel1 + "; " + costModel2 + " )";
	}
}
