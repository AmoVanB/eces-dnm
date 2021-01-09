package de.tum.ei.lkn.eces.dnm;

import de.uni_kl.cs.discodnc.curves.*;
import de.uni_kl.cs.discodnc.numbers.Num;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ResidualModesTest {
    private static double r1, r2, r3, b1, b2, b3, R, L;
    private static ArrivalCurve shapedArrivalCurve;
    private static ServiceCurve serviceCurve;

    /*
     * We use the following case, as it differs for each residual mode:
     *               /
     *        /     /
     *       /_____/
     *   -----
     *  |  /
     *  |_/
     *
     *  Ok drawing is bad.
     *
     *  Anyway: we have an arrival curve with 3 different slopes and the service curve intersects it
     *  in the first slope.
     */
    @Before
    public void setup() {
        r1 = 9000000;
        r2 = 6000000;
        r3 = 3000000;
        b1 = 1000;
        b2 = 4000;
        b3 = 10000;
        R = 18000000;
        L = 0.0004;
        ArrivalCurve curve1 = CurvePwAffine.getFactory().createTokenBucket(r1, b1);
        ArrivalCurve curve2 = CurvePwAffine.getFactory().createTokenBucket(r2, b2);
        ArrivalCurve curve3 = CurvePwAffine.getFactory().createTokenBucket(r3, b3);
        shapedArrivalCurve = CurvePwAffine.min(curve1, CurvePwAffine.min(curve2, curve3));
        serviceCurve = CurvePwAffine.getFactory().createRateLatency(R, L);

        Num intersection = CurvePwAffine.getXIntersection(shapedArrivalCurve, serviceCurve);

        // Making sure arrival curve indeed has 4 segments...
        assertEquals(4, shapedArrivalCurve.getSegmentCount());
        // ... and that the service curve intersects in the second one (which is the first slope)
        assertTrue(intersection.doubleValue() > shapedArrivalCurve.getSegment(0).getX().doubleValue());
        assertTrue(intersection.doubleValue() > shapedArrivalCurve.getSegment(1).getX().doubleValue());
        assertTrue(intersection.doubleValue() < shapedArrivalCurve.getSegment(2).getX().doubleValue());
    }


    @Test
    public void leastLatency() {
        ServiceCurve residualCurve = ResidualMode.LEAST_LATENCY.getResidualServiceCurve(serviceCurve, shapedArrivalCurve);
        assertTrue(residualCurve.isRateLatency());
        // T is the intersection of the two curves
        assertEquals(CurvePwAffine.getXIntersection(shapedArrivalCurve, serviceCurve), residualCurve.getLatency());
        // Rate is just reduced by the rate of the first arrival curve slope
        assertEquals(Num.getFactory().create(R - r1), residualCurve.getUltAffineRate());
    }

    @Test
    public void highestSlope() {
        ServiceCurve residualCurve = ResidualMode.HIGHEST_SLOPE.getResidualServiceCurve(serviceCurve, shapedArrivalCurve);
        assertTrue(residualCurve.isRateLatency());
        // Rate is just reduced by the smallest rate
        assertEquals(Num.getFactory().create(R - r3), residualCurve.getUltAffineRate());
        // T is then the intersection of this segment with X.
        LinearSegment segment = LinearSegment.createLinearSegment(
                // start of 4th segment
                shapedArrivalCurve.getSegment(3).getX(),
                // y is the RL value minus the arrival curve value
                Num.getUtils().sub(Num.getFactory().create((shapedArrivalCurve.getSegment(3).getX().doubleValue() - L) * R), shapedArrivalCurve.getSegment(3).getY()),
                Num.getFactory().create(R - r3),
                false);

        Num expectedT = segment.getXIntersectionWith(LinearSegment.createLinearSegment(Num.getFactory().create(0), Num.getFactory().create(0), Num.getFactory().create(0), false));
        assertEquals(expectedT, residualCurve.getLatency());
    }

    @Test
    public void real() {
        ServiceCurve residualCurve = ResidualMode.REAL_CURVE.getResidualServiceCurve(serviceCurve, shapedArrivalCurve);
        assertTrue(!residualCurve.isRateLatency());

        List<LinearSegment> segments = new LinkedList<>();
        // First segment is the latency
        segments.add(LinearSegment.createLinearSegment(Num.getFactory().create(0), Num.getFactory().create(0), Num.getFactory().create(0), false));
        // Second segment starts at intersection
        segments.add(LinearSegment.createLinearSegment(
                CurvePwAffine.getXIntersection(shapedArrivalCurve, serviceCurve),
                Num.getFactory().create(0),
                Num.getFactory().create(R - r1),
                false));
        // Next segments start at the same place as the next arrival curve segments.
        for(int i = 0; i < shapedArrivalCurve.getSegmentCount(); i++) {
            if(i == 0 || i == 1)
                continue;

            LinearSegment arrivalCurveSegment = shapedArrivalCurve.getSegment(i);
            Num segmentX = arrivalCurveSegment.getX();

            // Y is service curve value minus the arrival curve value corresponding to this segment.
            Num serviceCurveY = Num.getFactory().create((segmentX.doubleValue() - L) * R);
            Num arrivalCurveY = null;
            if(i == 2)
                arrivalCurveY = Num.getUtils().add(Num.getFactory().create(b2), Num.getUtils().mult(Num.getFactory().create(r2), segmentX));
            else if(i == 3)
                arrivalCurveY = Num.getUtils().add(Num.getFactory().create(b3), Num.getUtils().mult(Num.getFactory().create(r3), segmentX));

            Num segmentY = Num.getUtils().sub(serviceCurveY, arrivalCurveY);

            // Gradient is R - r2 or r3
            Num segmentGrad = null;
            if(i == 2)
                segmentGrad = Num.getUtils().sub(Num.getFactory().create(R), Num.getFactory().create(r2));
            else if(i == 3)
                segmentGrad = Num.getUtils().sub(Num.getFactory().create(R), Num.getFactory().create(r3));

            segments.add(LinearSegment.createLinearSegment(segmentX, segmentY, segmentGrad, true));
            // these are leftopen because they are directly from the arrival curve intersections
        }

        Curve computed = CurvePwAffine.getFactory().createCurve(segments);

        assertEquals(computed, residualCurve);
    }

    @Test
    public void noArrivalCurveTest() {
        // Check if service curve indeed does not change when arrival curve is null
        ArrivalCurve arrivalCurve = CurvePwAffine.getFactory().createTokenBucket(0, 0);
        serviceCurve = CurvePwAffine.getFactory().createRateLatency(R, L);

        ServiceCurve residualCurve = ResidualMode.LEAST_LATENCY.getResidualServiceCurve(serviceCurve, arrivalCurve);
        assertEquals(serviceCurve, residualCurve);

        residualCurve = ResidualMode.HIGHEST_SLOPE.getResidualServiceCurve(serviceCurve, arrivalCurve);
        assertEquals(serviceCurve, residualCurve);

        residualCurve = ResidualMode.REAL_CURVE.getResidualServiceCurve(serviceCurve, arrivalCurve);
        assertEquals(serviceCurve, residualCurve);
    }

    @Test
    public void noResidualCurve() {
        // Check if service curve indeed does not change when arrival curve is null
        ArrivalCurve arrivalCurve = CurvePwAffine.getFactory().createTokenBucket(999999999, 999999999);
        serviceCurve = CurvePwAffine.getFactory().createRateLatency(R, L);

        ServiceCurve residualCurve = ResidualMode.LEAST_LATENCY.getResidualServiceCurve(serviceCurve, arrivalCurve);
        assertEquals(CurvePwAffine.getFactory().createRateLatency(0, 0), residualCurve);

        residualCurve = ResidualMode.HIGHEST_SLOPE.getResidualServiceCurve(serviceCurve, arrivalCurve);
        assertEquals(CurvePwAffine.getFactory().createRateLatency(0, 0), residualCurve);

        residualCurve = ResidualMode.REAL_CURVE.getResidualServiceCurve(serviceCurve, arrivalCurve);
        assertEquals(CurvePwAffine.getFactory().createRateLatency(0, 0), residualCurve);
    }
}
