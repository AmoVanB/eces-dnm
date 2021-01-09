package de.tum.ei.lkn.eces.dnm.inputmodels;

import de.tum.ei.lkn.eces.dnm.DiscoCurveToJSON;
import de.uni_kl.cs.discodnc.curves.ArrivalCurve;
import de.uni_kl.cs.discodnc.curves.CurvePwAffine;
import de.uni_kl.cs.discodnc.numbers.Num;
import org.json.JSONObject;

/**
 * Token bucket usage of something.
 *
 * @author Jochen Guck
 * @author Amaury Van Bemten
 */
public class TokenBucketUtilization extends ResourceUtilization {
	private Num burst = Num.getFactory().create(0);
	private Num rate = Num.getFactory().create(0);

	public void addFlow(Num rate, Num burst) {
		this.burst = Num.getFactory().add(this.burst, burst);
		this.rate = Num.getFactory().add(this.rate, rate);
	}

	public void removeFlow(Num rate, Num burst) {
		this.burst = Num.getFactory().sub(this.burst, burst);
		this.rate = Num.getFactory().sub(this.rate, rate);
	}

	public Num getRate() {
		return rate;
	}

	public Num getBurst() {
		return burst;
	}

	public ArrivalCurve getTokenBucket() {
		return CurvePwAffine.getFactory().createTokenBucket(rate, burst);
	}

	@Override
	public JSONObject toJSONObject() {
		JSONObject result = super.toJSONObject();
		JSONObject tbJSON = DiscoCurveToJSON.get(getTokenBucket());
		tbJSON.getJSONObject("plotting").put("name", "Used Token Bucket");
		result.put("Token Bucket", tbJSON);
		return result;
	}
}