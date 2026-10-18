package io.casehub.ops.claudony;

import io.casehub.desiredstate.api.EventSource;
import io.casehub.desiredstate.api.NodeId;
import io.casehub.desiredstate.api.NodeStatus;
import io.casehub.desiredstate.api.StateEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.BackPressureStrategy;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ClaudonyEventSource implements EventSource {

    private volatile MultiEmitter<? super StateEvent> emitter;
    private final Multi<StateEvent> stream;

    public ClaudonyEventSource() {
        this.stream = Multi.createFrom().<StateEvent>emitter(
            e -> this.emitter = e, BackPressureStrategy.BUFFER)
            .broadcast().toAllSubscribers();
    }

    @Override
    public Multi<StateEvent> stream() { return stream; }

    public void emit(StateEvent event) {
        var e = this.emitter;
        if (e != null) e.emit(event);
    }

    public void emitDrift(NodeId nodeId) {
        emit(new StateEvent(nodeId, NodeStatus.DRIFTED, "drift detected"));
    }
}
