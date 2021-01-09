package de.tum.ei.lkn.eces.dnm;

import de.uni_kl.cs.discodnc.curves.ArrivalCurve;
import de.uni_kl.cs.discodnc.curves.Curve;
import de.uni_kl.cs.discodnc.curves.LinearSegment;
import de.uni_kl.cs.discodnc.curves.ServiceCurve;
import org.json.JSONObject;

import java.awt.*;

/**
 * Class for obtaining a JSON Object out of a Disco DNC curve (use for plotting by the WebGraphGUI).
 *
 * @author Amaury Van Bemten
 */
public class DiscoCurveToJSON {
    public static JSONObject get(Curve curve) {
        JSONObject result = new JSONObject();

        // Plotting part
        JSONObject plotting = new JSONObject();

        int nSegments = curve.getSegmentCount();
        for(int i = 0; i < nSegments; i++) {
            LinearSegment segment = curve.getSegment(i);
            plotting.put(segment.getX().toString(), segment.getY().toString());
        }

        plotting.put("finalSlope", curve.getSegment(nSegments - 1).getGrad());

        Color color = new Color(0, 0, 0);
        if(curve instanceof ArrivalCurve) {
            color = new Color(255, 0, 0);
        }
        else if(curve instanceof ServiceCurve) {
            color = new Color(0, 0, 255);
        }

        plotting.put("color", "rgb(" + color.getRed() + ", " + color.getGreen() + ", " + color.getBlue() + ")");
        result.put("plotting", plotting);

        return result;
    }
}
