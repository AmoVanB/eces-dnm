package de.tum.ei.lkn.eces.dnm.config.costmodels.functions;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.dnm.config.CostModel;
import de.tum.ei.lkn.eces.graph.Edge;
import de.tum.ei.lkn.eces.routing.requests.Request;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Sum of some cost models.
 *
 * @author Jochen Guck
 * @author Amaury Van Bemten
 */
public class Summation extends CostModel {
	private Iterable<CostModel> costModels;

	public Summation(Iterable<CostModel> costModels){
		this.costModels = costModels;
	}

	public Summation(CostModel... costModels){
		this.costModels = Arrays.asList(costModels);
	}

	@Override
	public CostModel init(Controller controller) {
		LinkedList<CostModel> list = new LinkedList<>();
		for(CostModel costModel : costModels)
			list.add(costModel.init(controller));
		return new Summation(list);
	}

	@Override
	public double getCost(Iterable<Edge> iterable, Edge edge, double[] doubles, Request request, boolean isFroward) {
		double sum = 0;
		for(CostModel costModel : costModels)
			sum += costModel.getCost(iterable, edge, doubles, request, isFroward);
		return sum;
	}

	@Override
	public double minCostValue() {
		double sum = 0;
		for(CostModel costModel : costModels)
			sum += costModel.minCostValue();
		return sum;
	}

	@Override
	public double maxCostValue()  {
		double sum = 0;
		for(CostModel costModel : costModels)
			sum += costModel.maxCostValue();
		return sum;
	}
	@Override
	public boolean containsCostModel(Class clazz){
		if(super.containsCostModel(clazz))
			return true;
		for (CostModel costModel: costModels)
			if(costModel.containsCostModel(clazz))
				return true;
		return false;
	}

	@Override
	public String toString() {
		StringBuilder result = new StringBuilder(this.getClass().getSimpleName() + "( ");
		Iterator<CostModel> iterator = costModels.iterator();
		if(iterator.hasNext()){
			result.append(iterator.next());
			while(iterator.hasNext()){
				result.append("; ").append(iterator.next());
			}
		}
		return result + ")";
	}
}
