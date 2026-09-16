package io.casehub.ops.app.service;

import java.util.List;
import java.util.UUID;

import io.casehub.ops.app.entity.ClusterReferenceEntity;
import io.casehub.ops.app.k8s.K8sClientRegistry;
import io.casehub.ops.app.model.ClusterStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ClusterService {

    @Inject
    ApplicationLifecycleService lifecycleService;

    @Inject
    K8sClientRegistry clientRegistry;

    @Inject
    EntityManager em;

    @Transactional
    public ClusterReferenceEntity register(ClusterReferenceEntity cluster, String tenancyId) {
        cluster.tenancyId = tenancyId;
        em.persist(cluster);
        return cluster;
    }

    public List<ClusterReferenceEntity> list(String tenancyId) {
        return em.createNamedQuery("ClusterReferenceEntity.findByTenancyId", ClusterReferenceEntity.class)
                .setParameter("tenancyId", tenancyId).getResultList();
    }

    public ClusterReferenceEntity findById(UUID id) {
        return em.find(ClusterReferenceEntity.class, id);
    }

    public ClusterStatus testConnectivity(UUID clusterId) {
        return ClusterStatus.UNKNOWN;
    }

    @Transactional
    public void delete(UUID clusterId, String tenancyId) {
        if (lifecycleService.hasActiveLoopsForCluster(clusterId.toString())) {
            throw new IllegalStateException(
                    "Cannot delete cluster " + clusterId + ": active reconciliation loops exist");
        }
        ClusterReferenceEntity entity = em.find(ClusterReferenceEntity.class, clusterId);
        if (entity != null) em.remove(entity);
        clientRegistry.deregister(clusterId.toString());
    }
}
