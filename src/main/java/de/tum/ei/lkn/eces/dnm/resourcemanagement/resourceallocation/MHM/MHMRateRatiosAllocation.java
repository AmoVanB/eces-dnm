package de.tum.ei.lkn.eces.dnm.resourcemanagement.resourceallocation.MHM;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.core.Mapper;
import de.tum.ei.lkn.eces.dnm.config.DetServConfig;
import de.tum.ei.lkn.eces.network.Queue;
import de.tum.ei.lkn.eces.network.Rate;
import de.tum.ei.lkn.eces.network.Scheduler;
import de.tum.ei.lkn.eces.network.mappers.RateMapper;

/**
 * Allocates resources to queues based on a pre-defined ratio of the link rate
 * for each queue.
 *
 * @author Amaury Van Bemten
 */
public class MHMRateRatiosAllocation extends MHMResourceAllocation {
    private Mapper<Rate> rateMapper;
    private double[] ratios;

    public MHMRateRatiosAllocation(Controller controller) {
        this(controller, new double[]{1.0/3, 1.0/3, 1.0/3});
    }

    public MHMRateRatiosAllocation(Controller controller, double[] ratios) {
        super(controller);
        rateMapper = new RateMapper(controller);
        this.ratios = ratios;
    }

    @Override
    public double[][] allocateResources(Scheduler scheduler) {
        DetServConfig myConfig = this.getGraphConfig(scheduler.getEntity());
        Queue[] queues = scheduler.getQueues();

        // Allocated resources for each queue (3: rate, burst, delay)
        double[][] allocatedResources = new double[queues.length][3];

        double sumR = 0;
        double sumB = 0;

        for(int i = 0; i < queues.length; i++) {
            // Service curve (Eqn. S.1 & S.2 of DetServ)
            double R = rateMapper.get(scheduler.getEntity()).getRate() - sumR;
            double T = (sumB + 2 * myConfig.getMaximumPacketSize()) / R;

            allocatedResources[i][RATE_INDEX] = rateMapper.get(scheduler.getEntity()).getRate() * this.ratios[i];

            // Eqn. 22 of DetServ
            allocatedResources[i][BURST_INDEX] = Math.max(0, queues[i].getSize() - allocatedResources[i][RATE_INDEX] * (sumB + 2 * myConfig.getMaximumPacketSize())/(R));

            // Delay: Eqn. 23 of DetServ
            allocatedResources[i][DELAY_INDEX] = (sumB + allocatedResources[i][BURST_INDEX] + 2 * myConfig.getMaximumPacketSize()) / (R);

            logger.info(String.format("queue %d (%s): R=%.2f T=%.5fms r=%.2f b=%.2f d=%.5fms",
                    i,
                    queues[i].toString(),
                    R*8,
                    T*1000,
                    allocatedResources[i][RATE_INDEX]*8,
                    allocatedResources[i][BURST_INDEX],
                    allocatedResources[i][DELAY_INDEX]*1000));


            sumB += allocatedResources[i][BURST_INDEX];
            sumR += allocatedResources[i][RATE_INDEX];
        }

        return allocatedResources;
    }
}
