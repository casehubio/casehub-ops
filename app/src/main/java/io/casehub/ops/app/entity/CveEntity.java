package io.casehub.ops.app.entity;

import io.casehub.ops.app.model.CveSeverity;
import io.casehub.ops.app.model.CveStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "cve_record")
@IdClass(CveEntity.CveId.class)
public class CveEntity extends PanacheEntityBase {

    @Id
    @Column(name = "application_id", nullable = false)
    public UUID applicationId;

    @Id
    @Column(name = "cve_id", nullable = false, length = 64)
    public String cveId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public CveSeverity severity;

    @Column(name = "affected_image", nullable = false, length = 512)
    public String affectedImage;

    @Column(name = "affected_services", columnDefinition = "TEXT")
    public String affectedServices;

    @Column(name = "fixed_in_tag", length = 256)
    public String fixedInTag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public CveStatus status;

    @Column(name = "tenancy_id", nullable = false, length = 128)
    public String tenancyId;

    @Column(name = "detected_at", nullable = false, updatable = false)
    public Instant detectedAt;

    @PrePersist
    void onPersist() {
        if (detectedAt == null) detectedAt = Instant.now();
        if (status == null) status = CveStatus.DETECTED;
    }

    public static List<CveEntity> findByApplicationId(UUID applicationId) {
        return list("applicationId", applicationId);
    }

    public static CveEntity findByCveId(UUID applicationId, String cveId) {
        return find("applicationId = ?1 and cveId = ?2", applicationId, cveId).firstResult();
    }

    public static class CveId implements Serializable {
        public UUID applicationId;
        public String cveId;

        public CveId() {}

        public CveId(UUID applicationId, String cveId) {
            this.applicationId = applicationId;
            this.cveId = cveId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CveId that = (CveId) o;
            return Objects.equals(applicationId, that.applicationId) && Objects.equals(cveId, that.cveId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(applicationId, cveId);
        }
    }
}
