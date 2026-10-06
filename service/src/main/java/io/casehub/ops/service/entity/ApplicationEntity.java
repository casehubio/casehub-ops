package io.casehub.ops.service.entity;

import io.casehub.ops.service.model.ApplicationStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "application")
@NamedQuery(name = "ApplicationEntity.findByTenancyId", query = "SELECT a FROM ApplicationEntity a WHERE a.tenancyId = :tenancyId")
@NamedQuery(name = "ApplicationEntity.findActiveByTenancyId", query = "SELECT a FROM ApplicationEntity a WHERE a.tenancyId = :tenancyId AND a.status NOT IN (io.casehub.ops.service.model.ApplicationStatus.DRAFT, io.casehub.ops.service.model.ApplicationStatus.DECOMMISSIONED)")
public class ApplicationEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(nullable = false)
    public String name;

    public String description;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(name = "services_json", nullable = false, columnDefinition = "TEXT")
    public String servicesJson;

    @Column(name = "compliance_policies_json", columnDefinition = "TEXT")
    public String compliancePoliciesJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    public ApplicationStatus status;

    @Column(name = "engine_case_id")
    public UUID engineCaseId;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PrePersist
    void onPersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (status == null) status = ApplicationStatus.DRAFT;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public static List<ApplicationEntity> findByTenancyId(String tenancyId) {
        return find("tenancyId", tenancyId).list();
    }

    public static List<ApplicationEntity> findActiveByTenancyId(String tenancyId) {
        return find("tenancyId = ?1 AND status NOT IN (?2, ?3)",
                    tenancyId, ApplicationStatus.DRAFT, ApplicationStatus.DECOMMISSIONED).list();
    }


}
