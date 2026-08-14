package io.casehub.ops.app.persistence;

import io.casehub.ops.app.model.CveRecord;
import io.casehub.ops.app.model.CveStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CveStore {

    void store(CveRecord record);

    List<CveRecord> findByApplicationId(UUID applicationId);

    List<CveRecord> findByServiceId(UUID applicationId, String serviceId);

    Optional<CveRecord> findByCveId(UUID applicationId, String cveId);

    void updateStatus(UUID applicationId, String cveId, CveStatus newStatus);
}
