package de.tum.ei.lkn.eces.dnm.config.costmodels.functions;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.dnm.config.CostModel;
import de.tum.ei.lkn.eces.graph.Edge;
import de.tum.ei.lkn.eces.routing.requests.Request;

/**
 * Multiplication of two cost models.
 *
 * @author Jochen Guck
 * @author Amaury Van Bemten
 */
public class Multiplication extends CostModel {
	private CostModel costModel1;
	private CostModel costModel2;

	public Multiplication(CostModel costModel1, CostModel costModel2) {
		this.costModel1 = costModel1;
		this.costModel2 = costModel2;
	}

	@Override
	public CostModel init(Controller controller) {
		return new Multiplication(costModel1.init(controller), costModel2.init(controller));
	}

	@Override
	public double getCost(Iterable<Edge> iterable, Edge edge, double[] parameters, Request request, boolean isFroward) {
		return costModel1.getCost(iterable, edge, parameters, request, isFroward)
				* costModel2.getCost(iterable, edge, parameters, request, isFroward);
	}

	@Override
	public double minCostValue() {
		return costModel1.minCostValue() * costModel2.minCostValue();
	}

	@Override
	public double maxCostValue()  {
		return costModel1.maxCostValue() * costModel2.maxCostValue();
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
