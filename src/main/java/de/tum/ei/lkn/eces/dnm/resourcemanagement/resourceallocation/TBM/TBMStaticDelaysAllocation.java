package de.tum.ei.lkn.eces.dnm.resourcemanagement.resourceallocation.TBM;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.network.Queue;
import de.tum.ei.lkn.eces.network.Scheduler;

/**
 * Allocate given delays to the queues.
 *
 * @author Amaury Van Bemten
 */
public class TBMStaticDelaysAllocation extends TBMResourceAllocation {
    private double[] delays;

    public TBMStaticDelaysAllocation(Controller controller, double[] delays) {
        super(controller);
        this.delays = delays;
    }

    public double[][] allocateResources(Scheduler scheduler) {
        Queue[] queues = scheduler.getQueues();
        double[][] allocatedResources = new double[queues.length][1];

        for(int i = 0; i < queues.length; i++) {
            if(i >= delays.length)
                allocatedResources[i][0] = 0;
            else
                allocatedResources[i][0] = delays[i];
        }

        return allocatedResources;
    }
}
