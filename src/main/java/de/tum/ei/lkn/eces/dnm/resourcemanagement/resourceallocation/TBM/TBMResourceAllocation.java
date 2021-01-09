package de.tum.ei.lkn.eces.dnm.resourcemanagement.resourceallocation.TBM;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.dnm.resourcemanagement.resourceallocation.ResourceAllocation;

/**
 * Defines the single resource for the TBM.
 *
 * @author Amaury Van Bemten
 */
public abstract class TBMResourceAllocation extends ResourceAllocation {
    // For each queue: delay
    public final static int DELAY_INDEX = 0;

    public TBMResourceAllocation(Controller controller) {
        super(controller);
    }
}
