package de.tum.ei.lkn.eces.dnm.queuemodels;

import de.tum.ei.lkn.eces.core.Component;
import de.tum.ei.lkn.eces.core.annotations.ComponentBelongsTo;
import de.tum.ei.lkn.eces.dnm.DNMSystem;
import de.tum.ei.lkn.eces.dnm.DiscoCurveToJSON;
import de.uni_kl.cs.discodnc.curves.ServiceCurve;
import org.json.JSONObject;

/**
 * Class representing the model of a queue.
 * That is basically just a service curve.
 *
 * @author Amaury Van Bemten
 * @author Jochen Guck
 */
@ComponentBelongsTo(system = DNMSystem.class)
public class QueueModel extends Component {
	private ServiceCurve serviceCurve;

	public ServiceCurve getServiceCurve() {
		return serviceCurve;
	}

	public void setServiceCurve(ServiceCurve serviceCurve) {
		this.serviceCurve = serviceCurve;
	}

	@Override
	public JSONObject toJSONObject() {
		JSONObject result = super.toJSONObject();
		JSONObject curveJSON = DiscoCurveToJSON.get(serviceCurve);
		if(serviceCurve.isRateLatency()) {
			curveJSON.getJSONObject("plotting").put("name", "Service Curve");
			result.put("Rate Latency", curveJSON);
		}
		else {
			curveJSON.getJSONObject("plotting").put("name", "Shaped Service Curve");
			result.put("Shaped Service Curve", curveJSON);
		}
		return result;
	}
}
