package de.tum.ei.lkn.eces.dnm.config;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.graph.Edge;
import de.tum.ei.lkn.eces.routing.requests.Request;

/**
 * A cost function.
 *
 * @author Jochen Guck
 * @author Amaury Van Bemten
 */
public abstract class CostModel {
	abstract public CostModel init(Controller controller);

	abstract public double getCost(Iterable<Edge> iterable, Edge edge, double[] doubles, Request request, boolean isFroward);

	abstract public double minCostValue();

	abstract public double maxCostValue();

    /**
     * @param clazz a given class.
     * @return Whether this cost function uses clazz as a sub-function.
     */
	public boolean containsCostModel(Class clazz) {
		return this.getClass().equals(clazz);
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName();
	}
}
