package io.casehub.ops.app.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.ops.api.approval.ApprovalPlan;
import io.casehub.ops.api.approval.PlanStore;
import io.casehub.ops.api.approval.PlanStoreMapper;
import io.casehub.ops.app.entity.ApprovalPlanEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JpaPlanStore implements PlanStore {

    private final ObjectMapper mapper = PlanStoreMapper.mapper();

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public String store(ApprovalPlan plan) {
        String ref = UUID.randomUUID().toString();
        var entity = new ApprovalPlanEntity();
        entity.ref = ref;
        entity.nodeId = plan.nodeId().value();
        entity.action = plan.action().name();
        entity.risk = plan.risk().name();
        entity.tenancyId = plan.tenancyId();
        entity.planJson = serialize(plan);
        em.persist(entity);
        return ref;
    }

    @Override
    public Optional<ApprovalPlan> retrieve(String planReference) {
        ApprovalPlanEntity entity = em.find(ApprovalPlanEntity.class, planReference);
        if (entity == null) return Optional.empty();
        return Optional.of(deserialize(entity.planJson));
    }

    @Override
    @Transactional
    public void remove(String planReference) {
        ApprovalPlanEntity entity = em.find(ApprovalPlanEntity.class, planReference);
        if (entity != null) em.remove(entity);
    }

    private String serialize(ApprovalPlan plan) {
        try {
            return mapper.writeValueAsString(plan);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ApprovalPlan", e);
        }
    }

    private ApprovalPlan deserialize(String json) {
        try {
            return mapper.readValue(json, ApprovalPlan.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize ApprovalPlan", e);
        }
    }
}
