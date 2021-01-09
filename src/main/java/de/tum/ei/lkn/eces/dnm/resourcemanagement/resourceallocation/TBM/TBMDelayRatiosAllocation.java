package de.tum.ei.lkn.eces.dnm.resourcemanagement.resourceallocation.TBM;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.core.Mapper;
import de.tum.ei.lkn.eces.network.Queue;
import de.tum.ei.lkn.eces.network.Rate;
import de.tum.ei.lkn.eces.network.Scheduler;
import de.tum.ei.lkn.eces.network.mappers.RateMapper;

/**
 * Allocates delay ratios to each queue in the following recursive way:
 * delay[i] = delay[i-1] + 5 * delay[i-1]
 * delay[0] = 0.0002
 *
 * @author Jochen Guck
 * @author Amaury Van Bemten
 */
public class TBMDelayRatiosAllocation extends TBMResourceAllocation {

    public TBMDelayRatiosAllocation(Controller controller) {
        super(controller);
        Mapper<Rate> rateMapper = new RateMapper(controller);
    }

    @Override
    public double[][] allocateResources(Scheduler scheduler) {
        Queue[] queues = scheduler.getQueues();

        // Allocated resources for each queue (3: rate, burst, delay)
        double[][] allocatedResources = new double[queues.length][1];

        double newDelay = 0.0002;
        for(int i = 0; i < queues.length; i++) {
            allocatedResources[i][DELAY_INDEX] = newDelay;
            newDelay = newDelay + 5 * newDelay;
        }

        return allocatedResources;
    }
}