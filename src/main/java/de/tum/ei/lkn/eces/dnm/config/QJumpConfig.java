package de.tum.ei.lkn.eces.dnm.config;

import de.tum.ei.lkn.eces.core.Component;
import de.tum.ei.lkn.eces.core.annotations.ComponentBelongsTo;
import de.tum.ei.lkn.eces.dnm.DNMSystem;
import org.json.JSONObject;

/**
 * Description of the QJump configuration (nb of hosts, etc.) [1].
 *
 * [1] Grosvenor, Matthew P., Malte Schwarzkopf, Ionel Gog, Robert NM Watson, Andrew W. Moore, Steven Hand, and Jon
 * Crowcroft. "Queues Don’t Matter When You Can JUMP Them!." In 12th USENIX Symposium on Networked Systems Design and
 * Implementation (NSDI 15), pp. 1-14. 2015.
 *
 * @author Amaury Van Bemten
 */
@ComponentBelongsTo(system = DNMSystem.class)
public class QJumpConfig extends Component {
    private final int n; // nb of hosts
    private final double epsilon; // cumulative processing time
    private final int P; // packet size
    private final double R; // link rate

    public QJumpConfig(int n, double epsilon, int p, double r) {
        this.n = n;
        this.epsilon = epsilon;
        this.P = p;
        this.R = r;
    }

	@Override
	public String toString() {
		return "QJumpConfig{" +
				"n=" + n +
				", epsilon=" + epsilon +
				", P=" + P +
				", R=" + R +
				", rate=" + this.getMaximumRate() +
                ", guarantee=" + this.getGuaranteedDelay() +
                '}';
	}

    public int getMaxNumberOfHosts() {
        return n;
    }

    public double getEpsilon() {
        return epsilon;
    }

    public int getMaximumPacketSize() {
        return P;
    }

    public double getRate() {
        return R;
    }

    public double getMaximumRate() {
        return P/this.getGuaranteedDelay();
    }

    public double getGuaranteedDelay() {
        return 2*n*P/R + epsilon;
    }

    public JSONObject toJSONObject() {
        JSONObject result = new JSONObject();
        result.put("n", this.getMaxNumberOfHosts());
        result.put("epsilon", this.getEpsilon());
        result.put("P", this.getMaximumPacketSize());
        result.put("R", this.getRate());
        result.put("maximum rate", this.getMaximumRate());
        result.put("guaranteed delay", this.getGuaranteedDelay());
        return result;
    }
}
