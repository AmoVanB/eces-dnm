package de.tum.ei.lkn.eces.dnm;

import de.uni_kl.cs.discodnc.curves.ArrivalCurve;
import de.uni_kl.cs.discodnc.curves.CurvePwAffine;
import de.uni_kl.cs.discodnc.curves.LinearSegment;
import de.uni_kl.cs.discodnc.curves.ServiceCurve;
import de.uni_kl.cs.discodnc.nc.bounds.Bound;
import de.uni_kl.cs.discodnc.numbers.Num;

/**
 * Within the assumption of sub-additive arrival curves and super-additive
 * service curves, there are different ways of computing the residual
 * rate latency service curve from an arrival curve and a service curve.
 * Since they can both have several knee points, the residual service curve
 * can also have multiple knee points, but because the arrival curve (resp.
 * service curve) is assumed to be sub-additive (resp. super-additive), the
 * residual service curve will be super-additive.
 * From this, there are different ways of transforming a super-additive
 * service curve in a rate-latency curve depending on which slope of the
 * curve is used for the rate-latency one.
 *
 * @author Amaury Van Bemten
 * @author Jochen Guck
 */
public enum ResidualMode {
    /**
     * Use the first positive slope of the service curve.
     */
    LEAST_LATENCY {
        @Override
        public ServiceCurve getResidualServiceCurve(ServiceCurve sc, ArrivalCurve ac) {
            ServiceCurve realCurve = Bound.leftOverServiceARB(sc, ac);
            if(realCurve.getSegmentCount() == 1 && realCurve.getSegment(0).getX().eq(0)
                    && realCurve.getSegment(0).getY().eq(0)
                    && realCurve.getSegment(0).getGrad().geq(Num.getFactory().create(0))) {
                // Ensure that even latency 0 is working
                return  CurvePwAffine.getFactory().createRateLatency(realCurve.getSegment(0).getGrad(), Num.getFactory().create(0));
            }

            Num latency = realCurve.getLatency();
            Num rate = realCurve.getGradientLimitRight(latency);
            return CurvePwAffine.getFactory().createRateLatency(rate, latency);
        }
    },

    /**
     * Use the last positive slope of the service curve.
     */
    HIGHEST_SLOPE {
        @Override
        public ServiceCurve getResidualServiceCurve(ServiceCurve sc, ArrivalCurve ac) {
            ServiceCurve realCurve = Bound.leftOverServiceARB(sc, ac);
            if(realCurve.getSegmentCount() == 1 && realCurve.getSegment(0).getX().eq(0)
                    && realCurve.getSegment(0).getY().eq(0)
                    && realCurve.getSegment(0).getGrad().geq(Num.getFactory().create(0))) {
                // Ensure that even latency 0 is working
                return CurvePwAffine.getFactory().createRateLatency(realCurve.getSegment(0).getGrad(), Num.getFactory().create(0));
            }

            // Getting info from last segment.
            LinearSegment segment = realCurve.getSegment(realCurve.getSegmentCount() - 1);
            Num rate = segment.getGrad();
            Num x = segment.getX();
            Num y = segment.getY();

            // From last segment, compute where it would intersect the X axis.
            Num deltaX = Num.getFactory().div(y, rate);
            Num latency = Num.getFactory().sub(x, deltaX);

            return CurvePwAffine.getFactory().createRateLatency(rate, latency);
        }
    },

    /**
     * Use the real curve.
     */
    REAL_CURVE {
        @Override
        public ServiceCurve getResidualServiceCurve(ServiceCurve sc, ArrivalCurve ac) {
            return Bound.leftOverServiceARB(sc, ac);
        }
    };

    public abstract ServiceCurve getResidualServiceCurve(ServiceCurve sc, ArrivalCurve ac);

}