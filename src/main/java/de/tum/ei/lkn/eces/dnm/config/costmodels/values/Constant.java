package de.tum.ei.lkn.eces.dnm.config.costmodels.values;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.dnm.config.CostModel;
import de.tum.ei.lkn.eces.graph.Edge;
import de.tum.ei.lkn.eces.routing.requests.Request;

/**
 * The cost is a static constant (e.g., for shortest path).
 *
 * @author Jochen Guck
 * @author Amaury Van Bemten
 */
public class Constant extends CostModel {
	private double constant;

	public Constant() {
		this(1.0);
	}

	public Constant(double constant) {
		this.constant = constant;
	}

	@Override
	public CostModel init(Controller controller) {
		return this;
	}

	@Override
	public double getCost(Iterable<Edge> iterable, Edge edge, double[] doubles, Request request, boolean isFroward) {
		return constant;
	}

	@Override
	public double minCostValue() {
		return constant;
	}

	@Override
	public double maxCostValue()  {
		return constant;
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + "( " + constant + " )";
	}
}
