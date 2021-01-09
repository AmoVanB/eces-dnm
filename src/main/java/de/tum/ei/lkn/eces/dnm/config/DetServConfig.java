package de.tum.ei.lkn.eces.dnm.config;

import de.tum.ei.lkn.eces.core.Component;
import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.core.annotations.ComponentBelongsTo;
import de.tum.ei.lkn.eces.dnm.DNMSystem;
import de.tum.ei.lkn.eces.dnm.ResidualMode;
import de.tum.ei.lkn.eces.dnm.resourcemanagement.resourceallocation.SelectResourceAllocation;
import org.json.JSONObject;

/**
 * Description of the Modeling configuration (network model, resource allocation, etc.) for the DetServ [1] and
 * Chameleon [2] models.
 *
 * [1] Guck, Jochen W., Amaury Van Bemten, and Wolfgang Kellerer. "DetServ: Network models for real-time QoS
 * provisioning in SDN-based industrial environments." IEEE Transactions on Network and Service Management 14, no. 4
 * (2017): 1003-1017.
 * [2] Van Bemten, Amaury, Nemanja Ðerić, Amir Varasteh, Stefan Schmid, Carmen Mas-Machuca, Andreas Blenk, and Wolfgang
 * Kellerer. "Chameleon: predictable latency and high utilization with queue-aware and adaptive source routing." In
 * Proceedings of the 16th International Conference on emerging Networking EXperiments and Technologies, pp. 451-465.
 * 2020.
 *
 * @author Jochen Guck
 * @author Amaury Van Bemten
 */
@ComponentBelongsTo(system = DNMSystem.class)
public class DetServConfig extends Component {
	private final ACModel acModel;
    private final ResidualMode residualMode;
    private final BurstIncreaseModel burstIncrease;
    private final boolean inputLinkShaping;
    private CostModel costModel;
    private final SelectResourceAllocation selectResourceAllocation;
    private final double minPerHopDelay;
    private final double maximumPacketSize;

    public DetServConfig(ACModel acModel, ResidualMode residualMode, BurstIncreaseModel burstIncrease, boolean inputLinkShaping, CostModel costModel, SelectResourceAllocation selectResourceAllocation) {
        this(acModel, residualMode, burstIncrease, inputLinkShaping, costModel, selectResourceAllocation, 1530, 0.000001);
    }

	public DetServConfig(ACModel acModel, ResidualMode residualMode, BurstIncreaseModel burstIncrease, boolean inputLinkShaping, CostModel costModel, SelectResourceAllocation selectResourceAllocation, double maximumPacketSize) {
		this(acModel, residualMode, burstIncrease, inputLinkShaping, costModel, selectResourceAllocation, maximumPacketSize, 0.000001);
	}

	public DetServConfig(ACModel acModel, ResidualMode residualMode, BurstIncreaseModel burstIncrease, boolean inputLinkShaping, CostModel costModel, SelectResourceAllocation selectResourceAllocation, double maximumPacketSize, double minPerHopDelay) {
		this.acModel = acModel;
		this.residualMode = residualMode;
		this.burstIncrease = burstIncrease;
		this.inputLinkShaping = inputLinkShaping;
		this.costModel = costModel;
		this.selectResourceAllocation = selectResourceAllocation;
		this.minPerHopDelay = minPerHopDelay;
		this.maximumPacketSize = maximumPacketSize;
	}

	public void initCostModel(Controller controller) {
        costModel = costModel.init(controller);
    }

	@Override
	public String toString() {
		return "DetServConfig{" +
				"acModel=" + acModel +
				", residualMode=" + residualMode +
				", burstIncrease=" + burstIncrease +
				", inputLinkShaping=" + inputLinkShaping +
				", costModel=" + costModel +
                ", selectResAlloc=" + selectResourceAllocation +
                ", maximumPacketSize=" + maximumPacketSize +
                ", minPerHopDelay=" + minPerHopDelay +
                '}';
	}

    public ACModel getAcModel() {
        return acModel;
    }

    public ResidualMode getResidualMode() {
        return residualMode;
    }

    public BurstIncreaseModel getBurstIncrease() {
        return burstIncrease;
    }

    public boolean isInputLinkShaping() {
        return inputLinkShaping;
    }

    public CostModel getCostModel() {
        return costModel;
    }

    public SelectResourceAllocation getSelectResourceAllocation() {
        return selectResourceAllocation;
    }

    public double getMinPerHopDelay() {
        return minPerHopDelay;
    }

    public double getMaximumPacketSize() {
        return maximumPacketSize;
    }

    public JSONObject toJSONObject() {
        JSONObject result = new JSONObject();
        result.put("AC Model", acModel);
        result.put("Residual Mode", residualMode);
        result.put("Burst Increase", burstIncrease);
        result.put("Input Link Shaping", inputLinkShaping);
        result.put("Cost Model", costModel);
        result.put("Resource Allocation", selectResourceAllocation);
        result.put("Maximum Packet Size", maximumPacketSize);
        result.put("Minimum Per-Hop Delay", minPerHopDelay);
        return result;
    }
}
