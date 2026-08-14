package io.casehub.ops.app.persistence;

import io.casehub.ops.app.entity.CveEntity;
import io.casehub.ops.app.model.CveRecord;
import io.casehub.ops.app.model.CveStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JpaCveStore implements CveStore {

    @Override
    @Transactional
    public void store(CveRecord record) {
        var entity = toEntity(record);
        entity.persist();
    }

    @Override
    public List<CveRecord> findByApplicationId(UUID applicationId) {
        return CveEntity.findByApplicationId(applicationId).stream()
                .map(JpaCveStore::toRecord)
                .toList();
    }

    @Override
    public List<CveRecord> findByServiceId(UUID applicationId, String serviceId) {
        return CveEntity.findByApplicationId(applicationId).stream()
                .map(JpaCveStore::toRecord)
                .filter(r -> r.affectedServices().contains(serviceId))
                .toList();
    }

    @Override
    public Optional<CveRecord> findByCveId(UUID applicationId, String cveId) {
        var entity = CveEntity.findByCveId(applicationId, cveId);
        return entity != null ? Optional.of(toRecord(entity)) : Optional.empty();
    }

    @Override
    @Transactional
    public void updateStatus(UUID applicationId, String cveId, CveStatus newStatus) {
        var entity = CveEntity.findByCveId(applicationId, cveId);
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
