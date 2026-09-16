package io.casehub.ops.app.persistence;

import io.casehub.ops.app.entity.CveEntity;
import io.casehub.ops.app.model.CveRecord;
import io.casehub.ops.app.model.CveStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JpaCveStore implements CveStore {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public void store(CveRecord record) {
        var entity = toEntity(record);
        em.persist(entity);
    }

    @Override
    public List<CveRecord> findByApplicationId(UUID applicationId) {
        return em.createNamedQuery("CveEntity.findByApplicationId", CveEntity.class)
                .setParameter("applicationId", applicationId)
                .getResultList().stream()
                .map(JpaCveStore::toRecord)
                .toList();
    }

    @Override
    public List<CveRecord> findByServiceId(UUID applicationId, String serviceId) {
        return em.createNamedQuery("CveEntity.findByApplicationId", CveEntity.class)
                .setParameter("applicationId", applicationId)
                .getResultList().stream()
                .map(JpaCveStore::toRecord)
                .filter(r -> r.affectedServices().contains(serviceId))
                .toList();
    }

    @Override
    public Optional<CveRecord> findByCveId(UUID applicationId, String cveId) {
        var results = em.createNamedQuery("CveEntity.findByCveId", CveEntity.class)
                .setParameter("applicationId", applicationId)
                .setParameter("cveId", cveId)
                .getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(toRecord(results.getFirst()));
    }

    @Override
    @Transactional
    public void updateStatus(UUID applicationId, String cveId, CveStatus newStatus) {
        var results = em.createNamedQuery("CveEntity.findByCveId", CveEntity.class)
                .setParameter("applicationId", applicationId)
                .setParameter("cveId", cveId)
                .getResultList();
        var entity = results.isEmpty() ? null : results.getFirst();
        if (entity != null) {
            entity.status = newStatus;
        }
    }

    static CveEntity toEntity(CveRecord record) {
        var entity = new CveEntity();
        entity.applicationId = record.applicationId();
        entity.cveId = record.cveId();
        entity.severity = record.severity();
        entity.affectedImage = record.affectedImage();
        entity.affectedServices = String.join(",", record.affectedServices());
        entity.fixedInTag = record.fixedInTag();
        entity.status = record.status();
        entity.tenancyId = record.tenancyId();
        entity.detectedAt = record.detectedAt();
        return entity;
    }

    static CveRecord toRecord(CveEntity entity) {
        List<String> services = entity.affectedServices != null && !entity.affectedServices.isEmpty()
                ? Arrays.asList(entity.affectedServices.split(","))
                : List.of();
        return new CveRecord(
                entity.cveId, entity.severity, entity.affectedImage,
                services, entity.fixedInTag, entity.status,
                entity.applicationId, entity.tenancyId, entity.detectedAt);
    }
}
