package io.casehub.ops.service.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.ops.service.model.ClusterType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@QuarkusTest
@Disabled("H2 incompatible with JpaLedgerEntry JOINED inheritance DDL (#49)")
class ClusterReferenceEntityTest {

    @Test
    @Transactional
    void persistsAndFindsCluster() {
        var cluster = new ClusterReferenceEntity();
        cluster.name = "ops-prod";
        cluster.apiUrl = "https://k8s.example.com:6443";
        cluster.namespace = "casehub";
        cluster.clusterType = ClusterType.KUBERNETES;
        cluster.tenancyId = "default";
        cluster.persist();

        assertThat(cluster.id).isNotNull();
        var results = ClusterReferenceEntity.findByTenancyId("default");
        assertThat(results).isNotEmpty();
    }
}
