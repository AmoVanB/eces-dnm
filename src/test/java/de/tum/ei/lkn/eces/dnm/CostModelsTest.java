package de.tum.ei.lkn.eces.dnm;

import de.tum.ei.lkn.eces.dnm.config.CostModel;
import de.tum.ei.lkn.eces.dnm.config.costmodels.functions.*;
import de.tum.ei.lkn.eces.dnm.config.costmodels.values.Constant;
import org.javatuples.Pair;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CostModelsTest {
    // TODO: we could do some more testing here. Just basic functions/values/features tested right now.

    @Test
    public void testConstant() {
        double[] values = new double[] {11.0, 24.0, 11.0/4, 2111.90124};
        for(double value : values) {
            CostModel costModel = new Constant(value);
            costModel.init(null); // does not need a controller
            assertEquals(value, costModel.getCost(null, null, null, null, true), 1e-12);
            assertEquals(value, costModel.minCostValue(), 1e-12);
            assertEquals(value, costModel.minCostValue(), 1e-12);
        }
    }

    @Test
    public void testSummation() {
        Pair<Double, Double>[] values = new Pair[] {
                new Pair(11.0, 24.4),
                new Pair(15.0, 0.0),
                new Pair(1231231.0, 1.1),
                new Pair(2.0, 2.0)};

        for(Pair<Double, Double> value : values) {
            CostModel costModel = new Summation(
                    new Constant(value.getValue0()),
                    new Constant(value.getValue1()));
            costModel.init(null); // does not need a controller
            assertEquals(value.getValue0() + value.getValue1(), costModel.getCost(null, null, null, null, true), 1e-12);
            assertEquals(value.getValue0() + value.getValue1(), costModel.minCostValue(), 1e-12);
            assertEquals(value.getValue0() + value.getValue1(), costModel.minCostValue(), 1e-12);
        }
    }

    @Test
    public void testMultiplication() {
        Pair<Double, Double>[] values = new Pair[] {
                new Pair(11.0, 24.4),
                new Pair(15.0, 0.0),
                new Pair(1231231.0, 1.1),
                new Pair(2.0, 2.0)};

        for(Pair<Double, Double> value : values) {
            CostModel costModel = new Multiplication(
                    new Constant(value.getValue0()),
                    new Constant(value.getValue1()));
            costModel.init(null); // does not need a controller
            assertEquals(value.getValue0() * value.getValue1(), costModel.getCost(null, null, null, null, true), 1e-12);
            assertEquals(value.getValue0() * value.getValue1(), costModel.minCostValue(), 1e-12);
            assertEquals(value.getValue0() * value.getValue1(), costModel.minCostValue(), 1e-12);
        }
    }

    @Test
    public void testDifference() {
        Pair<Double, Double>[] values = new Pair[] {
                new Pair(11.0, 24.4),
                new Pair(15.0, 0.0),
                new Pair(1231231.0, 1.1),
                new Pair(2.0, 2.0)};

        for(Pair<Double, Double> value : values) {
            CostModel costModel = new Minus(
                    new Constant(value.getValue0()),
                    new Constant(value.getValue1()));
            costModel.init(null); // does not need a controller
            assertEquals(value.getValue0() - value.getValue1(), costModel.getCost(null, null, null, null, true), 1e-12);
            assertEquals(value.getValue0() - value.getValue1(), costModel.minCostValue(), 1e-12);
            assertEquals(value.getValue0() - value.getValue1(), costModel.minCostValue(), 1e-12);
        }
    }

    @Test
    public void testDivision() {
        Pair<Double, Double>[] values = new Pair[] {
                new Pair(11.0, 24.4),
                new Pair(15.0, 0.0),
                new Pair(1231231.0, 1.1),
                new Pair(2.0, 2.0)};

        for(Pair<Double, Double> value : values) {
            CostModel costModel = new Division(
                    new Constant(value.getValue0()),
                    new Constant(value.getValue1()));
            costModel.init(null); // does not need a controller
            assertEquals(value.getValue0() / value.getValue1(), costModel.getCost(null, null, null, null, true), 1e-12);
            assertEquals(value.getValue0() / value.getValue1(), costModel.minCostValue(), 1e-12);
            assertEquals(value.getValue0() / value.getValue1(), costModel.minCostValue(), 1e-12);
        }
    }

    @Test
    public void TestLimiter() {
        CostModel[] models = {
                new LowerLimit(new UpperLimit(new Constant(-1), 5), 0),
                new LowerLimit(new UpperLimit(new Constant(0), 5), 0),
                new LowerLimit(new UpperLimit(new Constant(1), 5), 0),
                new LowerLimit(new UpperLimit(new Constant(2), 5), 0),
                new LowerLimit(new UpperLimit(new Constant(3), 5), 0),
                new LowerLimit(new UpperLimit(new Constant(4), 5), 0),
                new LowerLimit(new UpperLimit(new Constant(5), 5), 0),
                new LowerLimit(new UpperLimit(new Constant(6), 5), 0),
        };
        double[] result = {
                0,
                0,
                1,
                2,
                3,
                4,
                5,
                5
        };
        for(int i = 0; i < models.length; i++) {
            CostModel costModel = models[i].init(null);
            double value = costModel.getCost(null,null,null,null,true);
            assertEquals(result[i], value, 1e-12);
        }
    }
}
