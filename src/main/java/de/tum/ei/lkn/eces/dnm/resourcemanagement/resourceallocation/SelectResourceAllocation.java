package de.tum.ei.lkn.eces.dnm.resourcemanagement.resourceallocation;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.network.Scheduler;

/**
 * Ability to define a resource allocation for a given scheduler.
 *
 * @author Amaury Van Bemten
 */
public interface SelectResourceAllocation {
    ResourceAllocation selectResourceAllocation(Controller controller, Scheduler scheduler);
}
