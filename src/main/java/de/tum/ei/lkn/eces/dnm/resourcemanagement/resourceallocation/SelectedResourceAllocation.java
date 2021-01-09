package de.tum.ei.lkn.eces.dnm.resourcemanagement.resourceallocation;


import de.tum.ei.lkn.eces.core.Component;
import de.tum.ei.lkn.eces.core.annotations.ComponentBelongsTo;
import de.tum.ei.lkn.eces.dnm.DNMSystem;
import org.json.JSONObject;

/**
 * Component representing a selected resource allocation for a given scheduler.
 *
 * @author Amaury Van Bemten
 */
@ComponentBelongsTo(system = DNMSystem.class)
public class SelectedResourceAllocation extends Component {
    private final ResourceAllocation ra;

    public SelectedResourceAllocation(ResourceAllocation ra) {
        this.ra = ra;
    }

    public ResourceAllocation getResourceAllocation() {
        return ra;
    }

    public JSONObject toJSONObject() {
        return ra.toJSONObject();
    }
}
