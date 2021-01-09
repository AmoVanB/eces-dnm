package de.tum.ei.lkn.eces.dnm.queuemodels;

import de.tum.ei.lkn.eces.dnm.DiscoCurveToJSON;
import de.tum.ei.lkn.eces.dnm.exceptions.DNMException;
import de.uni_kl.cs.discodnc.curves.ArrivalCurve;
import de.uni_kl.cs.discodnc.curves.ServiceCurve;
import org.json.JSONObject;

/**
 * For the MHM model, we further add the maximum allowed token bucket to the model
 * of a queue.
 *
 * @author Amaury Van Bemten
 * @author Jochen Guck
 */
public class MHMQueueModel extends QueueModel {
    private ArrivalCurve maximumTokenBucket;

    public MHMQueueModel(ServiceCurve serviceCurve, ArrivalCurve maximumTokenBucket) {
        super();
        this.setServiceCurve(serviceCurve);
        if(!maximumTokenBucket.isTokenBucket())
            throw new DNMException("MHMQueueModel must have a token bucket as maximum arrival curve!");
        this.maximumTokenBucket = maximumTokenBucket;
    }

    public ArrivalCurve getMaximumTokenBucket() {
        return this.maximumTokenBucket;
    }

    @Override
    public JSONObject toJSONObject() {
        JSONObject result = super.toJSONObject();
        JSONObject curveJSON = DiscoCurveToJSON.get(maximumTokenBucket);
        curveJSON.getJSONObject("plotting").put("name", "Maximum Token Bucket");
        curveJSON.getJSONObject("plotting").put("color", "rgb(0, 0, 0)");
        result.put("Token Bucket", curveJSON);
        return result;
    }
}
