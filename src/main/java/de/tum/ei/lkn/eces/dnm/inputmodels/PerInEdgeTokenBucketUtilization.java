package de.tum.ei.lkn.eces.dnm.inputmodels;

import de.tum.ei.lkn.eces.dnm.DiscoCurveToJSON;
import de.tum.ei.lkn.eces.graph.Edge;
import de.uni_kl.cs.discodnc.curves.CurvePwAffine;
import de.uni_kl.cs.discodnc.misc.Pair;
import de.uni_kl.cs.discodnc.numbers.Num;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Describes the traffic input to a queue per input physical edge.
 * This is used to be able to implement input link shaping (ILS) [1].
 *
 * [1] Guck, Jochen W., Amaury Van Bemten, and Wolfgang Kellerer. "DetServ: Network models for real-time QoS
 * provisioning in SDN-based industrial environments." IEEE Transactions on Network and Service Management 14, no. 4
 * (2017): 1003-1017.
 *
 * @author Jochen Guck
 * @author Amaury Van Bemten
 */
public class PerInEdgeTokenBucketUtilization extends ResourceUtilization {
    /**
     * We store traffic per-incoming edge.
     * For each incoming edge, we store the token bucket coming
     * from this edge and entering the current queue.
     */
	private Map<Edge, Pair<Num>> inputLinkUtilisation = new HashMap<>();

	public void addFlow(Edge edge, Num rate, Num burst) {
		Pair<Num> tokenBucket;
		if((tokenBucket = inputLinkUtilisation.get(edge)) == null) {
			tokenBucket = new Pair<>(Num.getFactory().create(0), Num.getFactory().create(0));
			inputLinkUtilisation.put(edge, tokenBucket);
		}

		inputLinkUtilisation.put(edge, new Pair<>(Num.getFactory().add(tokenBucket.getFirst(), rate), Num.getFactory().add(tokenBucket.getSecond(), burst)));
	}

	public void removeFlow(Edge edge, Num rate, Num burst) {
		Pair<Num> tokenBucket;
		if((tokenBucket = inputLinkUtilisation.get(edge)) != null)
			inputLinkUtilisation.put(edge, new Pair<>(Num.getFactory().sub(tokenBucket.getFirst(), rate), Num.getFactory().sub(tokenBucket.getSecond(), burst)));
	}

	public void deleteEdge(Edge edge) {
		inputLinkUtilisation.remove(edge);
	}

	public Set<Map.Entry<Edge, Pair<Num>>> getTokenBuckets() {
		return inputLinkUtilisation.entrySet();
	}

	@Override
	public JSONObject toJSONObject() {
		JSONObject obj = super.toJSONObject();

		if(inputLinkUtilisation.size() == 0) {
			obj.put("Input data from links", new JSONObject().put("Data", "No input data to this edge"));
		}
		else {
			JSONObject utilisationMap = new JSONObject();
			for (Map.Entry<Edge, Pair<Num>> element : inputLinkUtilisation.entrySet()) {
				JSONObject tbJSON = DiscoCurveToJSON.get(CurvePwAffine.getFactory().createTokenBucket(element.getValue().getFirst(), element.getValue().getSecond()));
				tbJSON.remove("plotting"); // do not plot the individual token buckets
				utilisationMap.put("Physical Edge #" + element.getKey().getId(), tbJSON);
			}

			obj.put("Input data from links", utilisationMap);
		}
		return obj;
	}
}
