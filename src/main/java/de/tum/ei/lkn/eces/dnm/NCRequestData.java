package de.tum.ei.lkn.eces.dnm;

import de.tum.ei.lkn.eces.core.Component;
import de.tum.ei.lkn.eces.core.JSONUtil;
import de.tum.ei.lkn.eces.core.annotations.ComponentBelongsTo;
import de.tum.ei.lkn.eces.dnm.exceptions.DNMException;
import de.uni_kl.cs.discodnc.curves.ArrivalCurve;
import de.uni_kl.cs.discodnc.numbers.Num;
import org.json.JSONObject;

/**
 * Class representing the traffic characteristics of a routing request.
 * This consists of an arrival curve (traffic envelope) and a delay requirement (deadline).
 *
 * @author Jochen Guck
 * @author Amaury Van Bemten
 */
@ComponentBelongsTo(system= DNMSystem.class)
public class NCRequestData extends Component {
	private final Num deadline;
	private final ArrivalCurve tokenBucket;

	public NCRequestData(ArrivalCurve tokenBucket, Num deadline) {
		if(!tokenBucket.isTokenBucket())
			throw new DNMException("We only accept token buckets as arrival curves for requests!");
		this.tokenBucket = tokenBucket;
		this.deadline = deadline;
	}

	public ArrivalCurve getTb() {
		return tokenBucket;
	}

	public Num getDeadline() {
		return deadline;
	}

	@Override
	public JSONObject toJSONObject() {
		JSONObject result = super.toJSONObject();
		result.put("deadline", Num.getFactory().mult(deadline, Num.getFactory().create(1000))); // ms
		result.put("burst", Num.getFactory().div(tokenBucket.getBurst(), Num.getFactory().create(1000))); // KB
		result.put("rate", Num.getFactory().mult(Num.getFactory().div(tokenBucket.getUltAffineRate(), Num.getFactory().create(1000)), Num.getFactory().create(8))); // Kbps
		return JSONUtil.merge(result, DiscoCurveToJSON.get(tokenBucket));
	}
}
