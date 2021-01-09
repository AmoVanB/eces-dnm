package de.tum.ei.lkn.eces.dnm.resourcemanagement.resourceallocation.MHM;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.dnm.resourcemanagement.resourceallocation.ResourceAllocation;

/**
 * Defines the three resources for the MHM.
 *
 * @author Amaury Van Bemten
 */
public abstract class MHMResourceAllocation extends ResourceAllocation {
    // For each queue: burst, rate, delay
    public final static int BURST_INDEX = 0;
    public final static int RATE_INDEX = 1;
    public final static int DELAY_INDEX = 2;

    MHMResourceAllocation(Controller controller) {
        super(controller);
    }
}