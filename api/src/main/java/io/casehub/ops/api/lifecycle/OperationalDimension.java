package io.casehub.ops.api.lifecycle;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class OperationalDimension {

    private final    DimensionType                 type;
    private final    DimensionSection              section;
    private final    CopyOnWriteArrayList<CaseRef> activeResponses;
    private final    List<GanglionBinding>         subscriptions;
    private final    ReadWriteLock                 lock = new ReentrantReadWriteLock();
    private volatile DimensionStatus               status;
    private volatile boolean                       loaded;

    public OperationalDimension(DimensionType type, DimensionStatus status,
                                DimensionSection section, List<GanglionBinding> subscriptions) {
        this(type, status, section, subscriptions, true);
    }

    public OperationalDimension(DimensionType type, DimensionStatus status,
                                DimensionSection section, List<GanglionBinding> subscriptions,
                                boolean loaded) {
        this.type            = type;
        this.status          = status;
        this.section         = section;
        this.activeResponses = new CopyOnWriteArrayList<>();
        this.subscriptions   = List.copyOf(subscriptions);
        this.loaded          = loaded;
    }

    public DimensionType type()                  {return type;}

    public DimensionStatus status()              {return status;}

    public DimensionSection section()            {return section;}

    public Severity severity()                   {return status.severity();}

    public List<GanglionBinding> subscriptions() {return subscriptions;}

    public boolean isLoaded()                    {return loaded;}

    public List<CaseRef> activeResponses() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableList(activeResponses);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void updateStatus(DimensionStatus newStatus) {
        lock.writeLock().lock();
        try {
            this.status = newStatus;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void updateStatusAndPersist(DimensionStatus newStatus) {
        lock.writeLock().lock();
        try {
            this.status = newStatus;
            section.put("status", ((Enum<?>) newStatus).name());
        } finally {
            lock.writeLock().unlock();
        }
    }


    public void addResponse(CaseRef ref) {
        lock.writeLock().lock();
        try {
            activeResponses.add(ref);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removeResponse(UUID caseId) {
        lock.writeLock().lock();
        try {
            activeResponses.removeIf(r -> r.caseId().equals(caseId));
        } finally {
            lock.writeLock().unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public void load(DimensionSection.ContextReader reader) {
        lock.writeLock().lock();
        try {
            if (loaded) {return;}
            Object raw = reader.read(type.contextPrefix() + "activeResponseIds");
            if (raw instanceof List<?> list) {
                for (Object entry : list) {
                    if (entry instanceof Map<?, ?> map) {
                        activeResponses.add(new CaseRef(
                                UUID.fromString((String) map.get("caseId")),
                                (String) map.get("bindingName"),
                                Instant.parse((String) map.get("createdAt"))));
                    }
                }
            }
            loaded = true;
        } finally {
            lock.writeLock().unlock();
        }
    }
}
