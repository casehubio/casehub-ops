package io.casehub.ops.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "approval_plan")
@NamedQuery(name = "ApprovalPlanEntity.findByRef", query = "SELECT a FROM ApprovalPlanEntity a WHERE a.ref = :ref")
public class ApprovalPlanEntity {

    @Id
    @Column(length = 36)
    public String ref;

    @Column(name = "node_id", nullable = false)
    public String nodeId;

    @Column(nullable = false, length = 30)
    public String action;

    @Column(nullable = false, length = 30)
    public String risk;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(name = "plan_json", nullable = false, columnDefinition = "TEXT")
    public String planJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @PrePersist
    void onPersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
