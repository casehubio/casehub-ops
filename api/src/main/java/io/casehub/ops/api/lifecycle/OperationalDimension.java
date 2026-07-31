package io.casehub.ops.api.lifecycle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class OperationalDimension {

    private final DimensionType type;
    private volatile DimensionStatus status;
    private final DimensionSection section;
    private final List<CaseRef> activeResponses;
    private final List<GanglionBinding> subscriptions;

    public OperationalDimension(DimensionType type, DimensionStatus status,
                                DimensionSection section, List<GanglionBinding> subscriptions) {
        this.type = type;
        this.status = status;
        this.section = section;
        this.activeResponses = new ArrayList<>();
        this.subscriptions = List.copyOf(subscriptions);
    }

    public DimensionType type() { return type; }
    public DimensionStatus status() { return status; }
    public DimensionSection section() { return section; }
    public Severity severity() { return status.severity(); }
    public List<CaseRef> activeResponses() { return Collections.unmodifiableList(activeResponses); }
    public List<GanglionBinding> subscriptions() { return subscriptions; }

    public void updateStatus(DimensionStatus newStatus) {
        this.status = newStatus;
    }

    public void addResponse(CaseRef ref) {
        activeResponses.add(ref);
    }

    public void removeResponse(UUID caseId) {
        activeResponses.removeIf(r -> r.caseId().equals(caseId));
    }
}
