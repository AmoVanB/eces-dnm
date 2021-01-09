package de.tum.ei.lkn.eces.dnm.resourcemanagement.resourceallocation;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.core.Entity;
import de.tum.ei.lkn.eces.core.Mapper;
import de.tum.ei.lkn.eces.dnm.ResidualMode;
import de.tum.ei.lkn.eces.dnm.config.ACModel;
import de.tum.ei.lkn.eces.dnm.config.BurstIncreaseModel;
import de.tum.ei.lkn.eces.dnm.config.DetServConfig;
import de.tum.ei.lkn.eces.dnm.config.costmodels.values.Constant;
import de.tum.ei.lkn.eces.dnm.mappers.DetServConfigMapper;
import de.tum.ei.lkn.eces.dnm.resourcemanagement.resourceallocation.TBM.TBMDelayRatiosAllocation;
import de.tum.ei.lkn.eces.network.Scheduler;
import org.apache.log4j.Logger;
import org.json.JSONObject;

/**
 * Able to allocate resources to a scheduler.
 *
 * @author Amaury Van Bemten
 */
public abstract class ResourceAllocation {
    private Mapper<DetServConfig> graphConfigMapper;
    private DetServConfig defaultConfig;
    protected final Logger logger;

    public ResourceAllocation(Controller controller) {
        this(controller, new DetServConfig(
                ACModel.TBM,
                ResidualMode.HIGHEST_SLOPE,
                BurstIncreaseModel.WORST_CASE_BURST_REAL_RESERVATION,
                false,
                new Constant(),
                (cont, sched) -> new TBMDelayRatiosAllocation(cont)));
    }

    public ResourceAllocation(Controller controller, DetServConfig defaultConfig) {
        this.logger = Logger.getLogger(this.getClass());
        this.graphConfigMapper = new DetServConfigMapper(controller);
        this.defaultConfig =  defaultConfig;
    }

    // Array dimensions: queue, resources ([1][2]: resource #2 of queue #1)
    public abstract double[][] allocateResources(Scheduler scheduler);

    protected DetServConfig getGraphConfig(Entity entity) {
        if(graphConfigMapper.isIn(entity))
            return graphConfigMapper.get(entity);
        else
            return defaultConfig;
    }

    public JSONObject toJSONObject() {
        JSONObject result = new JSONObject();
        result.put("algorithm", this.getClass().getSimpleName());
        return result;
    }
}
