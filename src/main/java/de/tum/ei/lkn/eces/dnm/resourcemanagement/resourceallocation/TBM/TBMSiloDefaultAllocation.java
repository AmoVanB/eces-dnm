package de.tum.ei.lkn.eces.dnm.resourcemanagement.resourceallocation.TBM;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.core.Mapper;
import de.tum.ei.lkn.eces.network.Queue;
import de.tum.ei.lkn.eces.network.Rate;
import de.tum.ei.lkn.eces.network.Scheduler;
import de.tum.ei.lkn.eces.network.mappers.RateMapper;

/**
 * Allocation of Silo [1]: each queue gets its emptying time (size/rate) as allocated delay.
 *
 * [1] Jang, Keon, Justine Sherry, Hitesh Ballani, and Toby Moncaster. "Silo: Predictable message latency in the cloud."
 * ACM SIGCOMM Computer Communication Review 45, no. 4 (2015): 435-448.
 *
 * @author Amaury Van Bemten
 */
public class TBMSiloDefaultAllocation extends TBMResourceAllocation {
    private Mapper<Rate> rateMapper;

    public TBMSiloDefaultAllocation(Controller controller) {
        super(controller);
        rateMapper = new RateMapper(controller);
    }

    @Override
    public double[][] allocateResources(Scheduler scheduler) {
        Queue[] queues = scheduler.getQueues();

        // Allocated resources for each queue (3: rate, burst, delay)
        double[][] allocatedResources = new double[queues.length][1];

        for(int i = 0; i < queues.length; i++) {
            // Silo just allocates a delay to the first queue and corresponding to the queue "emptying time"
            if(i == 0)
                allocatedResources[i][DELAY_INDEX] = queues[i].getSize() / rateMapper.get(scheduler.getEntity()).getRate();
            else
                allocatedResources[i][DELAY_INDEX] = 0;
        }

        return allocatedResources;
    }
}
