package io.casehub.ops.app.service;

import io.casehub.ops.app.entity.ApplicationEntity;
import io.casehub.ops.app.model.ApplicationStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

@QuarkusTest
class ApplicationLifecycleServiceTest {

    @Inject
    ApplicationLifecycleService lifecycleService;

    @Test
    @Transactional
    void createsDraftApplication() {
        var app = lifecycleService.createDraft("test-app", "Test", "[]", "default");
        assertThat(app.id).isNotNull();
        assertThat(app.status).isEqualTo(ApplicationStatus.DRAFT);
        assertThat(app.name).isEqualTo("test-app");
        assertThat(app.description).isEqualTo("Test");
        assertThat(app.servicesJson).isEqualTo("[]");
        assertThat(app.tenancyId).isEqualTo("default");
    }

    @Test
    @Transactional
    void derivesStatusForDraft() {
        var app = lifecycleService.createDraft("status-test", "Test", "[]", "default");
        var status = lifecycleService.deriveStatus(app);
        assertThat(status).isEqualTo(ApplicationStatus.DRAFT);
    }

    @Test
    @Transactional
    void derivesStatusWhenEngineCaseIdPresent() {
        var app = lifecycleService.createDraft("engine-test", "Test", "[]", "default");
        app.engineCaseId = java.util.UUID.randomUUID();
        app.status = ApplicationStatus.DEPLOYING;
        app.persist();

        var status = lifecycleService.deriveStatus(app);
        assertThat(status).isEqualTo(ApplicationStatus.DEPLOYING);
    }

    @Test
    @Transactional
    void parsesValidServicesJson() {
        String servicesJson = """
            [
              {
                "serviceId": "web",
                "name": "Web Frontend",
                "image": "myapp/web:1.0",
                "replicas": 2,
                "ports": [],
                "env": {},
                "resources": {
                  "cpuRequest": "100m",
                  "memoryRequest": "128Mi",
                  "cpuLimit": "500m",
                  "memoryLimit": "512Mi"
                },
                "dependsOn": [],
                "healthCheck": null,
                "targetClusters": []
              }
            ]
            """;
        var app = lifecycleService.createDraft("parse-test", "Test", servicesJson, "default");
        assertThat(app.servicesJson).isNotEmpty();
        assertThat(app.id).isNotNull();
    }

    @Test
    @Transactional
    void updateServiceReplicasPatchesJson() {
        String servicesJson = """
                              [
                                {
                                  "serviceId": "web",
                                  "name": "Web Frontend",
                                  "image": "myapp/web:1.0",
                                  "replicas": 2,
                                  "ports": [],
                                  "env": {},
                                  "resources": {
                                    "cpuRequest": "100m",
                                    "memoryRequest": "128Mi",
                                    "cpuLimit": "500m",
                                    "memoryLimit": "512Mi"
                                  },
                                  "dependsOn": [],
                                  "healthCheck": null,
                                  "targetClusters": []
                                }
                              ]
                              """;
        var app = lifecycleService.createDraft("scale-test", "Test", servicesJson, "default");
        app.status = ApplicationStatus.RUNNING;
        app.persist();

        java.util.Set<String> affected = lifecycleService.updateServiceReplicas(
                app.id, "web", 5, "default");

        var updated = ApplicationEntity.<ApplicationEntity>findById(app.id);
        assertThat(updated.servicesJson).contains("\"replicas\":5");
        assertThat(affected).isNotNull();
    }

    @Test
    @Transactional
    void updateServiceReplicasUnknownServiceThrows() {
        String servicesJson = """
                              [
                                {
                                  "serviceId": "web",
                                  "name": "Web Frontend",
                                  "image": "myapp/web:1.0",
                                  "replicas": 2,
                                  "ports": [],
                                  "env": {},
                                  "resources": {
                                    "cpuRequest": "100m",
                                    "memoryRequest": "128Mi",
                                    "cpuLimit": "500m",
                                    "memoryLimit": "512Mi"
                                  },
                                  "dependsOn": [],
                                  "healthCheck": null,
                                  "targetClusters": []
                                }
                              ]
                              """;
        var app = lifecycleService.createDraft("unknown-svc-test", "Test", servicesJson, "default");
        app.status = ApplicationStatus.RUNNING;
        app.persist();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> lifecycleService.updateServiceReplicas(
                        app.id, "nonexistent", 5, "default"))
                .withMessageContaining("nonexistent");
    }

    @Test
    @Transactional
    void updateServiceReplicasRejectsDraftStatus() {
        var app = lifecycleService.createDraft("draft-scale", "Test", "[]", "default");

        assertThatIllegalStateException()
                .isThrownBy(() -> lifecycleService.updateServiceReplicas(
                        app.id, "web", 5, "default"))
                .withMessageContaining("DRAFT");
    }

    @Test
    @Transactional
    void updateServiceReplicasRejectsDeployingStatus() {
        var app = lifecycleService.createDraft("deploying-scale", "Test", "[]", "default");
        app.status = ApplicationStatus.DEPLOYING;
        app.persist();

        assertThatIllegalStateException()
                .isThrownBy(() -> lifecycleService.updateServiceReplicas(
                        app.id, "web", 5, "default"))
                .withMessageContaining("DEPLOYING");
    }

    @Test
    @Transactional
    void restartServiceIncrementsGeneration() {
        String servicesJson = """
                              [
                                {
                                  "serviceId": "web",
                                  "name": "Web Frontend",
                                  "image": "myapp/web:1.0",
                                  "replicas": 2,
                                  "ports": [],
                                  "env": {},
                                  "resources": {
                                    "cpuRequest": "100m",
                                    "memoryRequest": "128Mi",
                                    "cpuLimit": "500m",
                                    "memoryLimit": "512Mi"
                                  },
                                  "dependsOn": [],
                                  "healthCheck": null,
                                  "targetClusters": []
                                }
                              ]
                              """;
        var app = lifecycleService.createDraft("restart-test", "Test", servicesJson, "default");
        app.status = io.casehub.ops.app.model.ApplicationStatus.RUNNING;
        app.persist();

        java.util.Set<String> affected = lifecycleService.restartService(
                app.id, "web", "default");

        var updated = ApplicationEntity.<ApplicationEntity>findById(app.id);
        assertThat(updated.servicesJson).contains("\"restartGeneration\":1");
        assertThat(affected).isNotNull();
    }

    @Test
    @Transactional
    void restartServiceUnknownServiceThrows() {
        String servicesJson = """
                              [{"serviceId":"web","name":"Web","image":"img:1.0","replicas":2,
                                "ports":[],"env":{},"resources":{"cpuRequest":"100m","memoryRequest":"128Mi","cpuLimit":"500m","memoryLimit":"512Mi"},
                                "dependsOn":[],"healthCheck":null,"targetClusters":[]}]
                              """;
        var app = lifecycleService.createDraft("restart-unknown", "Test", servicesJson, "default");
        app.status = io.casehub.ops.app.model.ApplicationStatus.RUNNING;
        app.persist();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> lifecycleService.restartService(app.id, "nonexistent", "default"))
                .withMessageContaining("nonexistent");
    }

    @Test
    @Transactional
    void restartServiceRejectsDraftStatus() {
        var app = lifecycleService.createDraft("restart-draft", "Test", "[]", "default");
        assertThatIllegalStateException()
                .isThrownBy(() -> lifecycleService.restartService(app.id, "web", "default"))
                .withMessageContaining("DRAFT");
    }

    @Test
    @Transactional
    void rollbackServiceRevertsImage() {
        String servicesJsonV1 = """
                                [{"serviceId":"web","name":"Web","image":"myapp/web:1.0","replicas":2,
                                  "ports":[],"env":{},"resources":{"cpuRequest":"100m","memoryRequest":"128Mi","cpuLimit":"500m","memoryLimit":"512Mi"},
                                  "dependsOn":[],"healthCheck":null,"targetClusters":[]}]
                                """;
        String servicesJsonV2 = """
                                [{"serviceId":"web","name":"Web","image":"myapp/web:2.0","replicas":2,
                                  "ports":[],"env":{},"resources":{"cpuRequest":"100m","memoryRequest":"128Mi","cpuLimit":"500m","memoryLimit":"512Mi"},
                                  "dependsOn":[],"healthCheck":null,"targetClusters":[]}]
                                """;
        var app = lifecycleService.createDraft("rollback-test", "Test", servicesJsonV2, "default");
        app.status = io.casehub.ops.app.model.ApplicationStatus.RUNNING;
        app.persist();

        var record = new io.casehub.ops.app.entity.DeploymentRecordEntity();
        record.applicationId = app.id;
        record.topologyJson  = servicesJsonV1;
        record.trigger       = io.casehub.ops.app.model.DeploymentTrigger.INITIAL;
        record.outcome       = io.casehub.ops.app.model.DeploymentOutcome.SUCCESS;
        record.persist();

        java.util.Set<String> affected = lifecycleService.rollbackService(
                app.id, "web", "default");

        var updated = ApplicationEntity.<ApplicationEntity>findById(app.id);
        assertThat(updated.servicesJson).contains("myapp/web:1.0");
        assertThat(updated.servicesJson).doesNotContain("myapp/web:2.0");
        assertThat(affected).isNotNull();
    }

    @Test
    @Transactional
    void rollbackServiceNoPreviousDeploymentThrows() {
        String servicesJson = """
                              [{"serviceId":"web","name":"Web","image":"myapp/web:1.0","replicas":2,
                                "ports":[],"env":{},"resources":{"cpuRequest":"100m","memoryRequest":"128Mi","cpuLimit":"500m","memoryLimit":"512Mi"},
                                "dependsOn":[],"healthCheck":null,"targetClusters":[]}]
                              """;
        var app = lifecycleService.createDraft("rollback-no-prev", "Test", servicesJson, "default");
        app.status = io.casehub.ops.app.model.ApplicationStatus.RUNNING;
        app.persist();

        assertThatIllegalStateException()
                .isThrownBy(() -> lifecycleService.rollbackService(app.id, "web", "default"))
                .withMessageContaining("No previous successful deployment");
    }

    @Test
    @Transactional
    void rollbackServiceUnknownServiceThrows() {
        String servicesJson = """
                              [{"serviceId":"web","name":"Web","image":"img:1.0","replicas":2,
                                "ports":[],"env":{},"resources":{"cpuRequest":"100m","memoryRequest":"128Mi","cpuLimit":"500m","memoryLimit":"512Mi"},
                                "dependsOn":[],"healthCheck":null,"targetClusters":[]}]
                              """;
        var app = lifecycleService.createDraft("rollback-unknown", "Test", servicesJson, "default");
        app.status = io.casehub.ops.app.model.ApplicationStatus.RUNNING;
        app.persist();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> lifecycleService.rollbackService(app.id, "nonexistent", "default"))
                .withMessageContaining("nonexistent");
    }

    @Test
    @Transactional
    void updateServiceConfigMergesEnvAndReturnsNodeIds() {
        String servicesJson = """
                              [{"serviceId":"web","name":"Web","image":"img:1.0","replicas":2,
                                "ports":[],"env":{"DB_HOST":"localhost","DB_PORT":"5432"},
                                "resources":{"cpuRequest":"100m","memoryRequest":"128Mi","cpuLimit":"500m","memoryLimit":"512Mi"},
                                "dependsOn":[],"healthCheck":null,"targetClusters":[]}]
                              """;
        var app = lifecycleService.createDraft("config-test", "Test", servicesJson, "default");
        app.status = io.casehub.ops.app.model.ApplicationStatus.RUNNING;
        app.persist();

        java.util.Set<String> affected = lifecycleService.updateServiceConfig(
                app.id, "web", java.util.Map.of("ENCRYPTION_ENABLED", "true", "DB_PORT", "5433"), "default");

        assertThat(affected).isNotNull();
        var updated = ApplicationEntity.<ApplicationEntity>findById(app.id);
        assertThat(updated.servicesJson).contains("\"DB_HOST\":\"localhost\"");
        assertThat(updated.servicesJson).contains("\"DB_PORT\":\"5433\"");
        assertThat(updated.servicesJson).contains("\"ENCRYPTION_ENABLED\":\"true\"");
    }

    @Test
    @Transactional
    void updateServiceConfigUnknownServiceThrows() {
        String servicesJson = """
                              [{"serviceId":"web","name":"Web","image":"img:1.0","replicas":2,
                                "ports":[],"env":{},"resources":{"cpuRequest":"100m","memoryRequest":"128Mi","cpuLimit":"500m","memoryLimit":"512Mi"},
                                "dependsOn":[],"healthCheck":null,"targetClusters":[]}]
                              """;
        var app = lifecycleService.createDraft("config-unknown", "Test", servicesJson, "default");
        app.status = io.casehub.ops.app.model.ApplicationStatus.RUNNING;
        app.persist();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> lifecycleService.updateServiceConfig(
                        app.id, "nonexistent", java.util.Map.of("KEY", "val"), "default"))
                .withMessageContaining("nonexistent");
    }

    @Test
    @Transactional
    void updateServiceConfigUnknownApplicationThrows() {
        java.util.UUID bogusId = java.util.UUID.randomUUID();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> lifecycleService.updateServiceConfig(
                        bogusId, "web", java.util.Map.of("KEY", "val"), "default"))
                .withMessageContaining(bogusId.toString());
    }

    @Test
    @Transactional
    void updateServiceConfigEmptyUpdatesReturnsEmpty() {
        String servicesJson = """
                              [{"serviceId":"web","name":"Web","image":"img:1.0","replicas":2,
                                "ports":[],"env":{"DB_HOST":"localhost"},"resources":{"cpuRequest":"100m","memoryRequest":"128Mi","cpuLimit":"500m","memoryLimit":"512Mi"},
                                "dependsOn":[],"healthCheck":null,"targetClusters":[]}]
                              """;
        var app = lifecycleService.createDraft("config-empty", "Test", servicesJson, "default");
        app.status = io.casehub.ops.app.model.ApplicationStatus.RUNNING;
        app.persist();

        java.util.Set<String> affected = lifecycleService.updateServiceConfig(
                app.id, "web", java.util.Map.of(), "default");

        assertThat(affected).isEmpty();
    }


    @Test
    @Transactional
    void updateServiceImagePatchesJson() {
        String servicesJson = """
                              [{"serviceId":"web","name":"Web","image":"myapp/web:1.0","replicas":2,
                                "ports":[],"env":{},"resources":{"cpuRequest":"100m","memoryRequest":"128Mi","cpuLimit":"500m","memoryLimit":"512Mi"},
                                "dependsOn":[],"healthCheck":null,"targetClusters":[]}]
                              """;
        var app = lifecycleService.createDraft("image-test", "Test", servicesJson, "default");
        app.status = ApplicationStatus.RUNNING;
        app.persist();

        java.util.Set<String> affected = lifecycleService.updateServiceImage(
                app.id, "web", "myapp/web:2.0", "default");

        var updated = ApplicationEntity.<ApplicationEntity>findById(app.id);
        assertThat(updated.servicesJson).contains("myapp/web:2.0");
        assertThat(updated.servicesJson).doesNotContain("myapp/web:1.0");
        assertThat(affected).isNotNull();
    }

    @Test
    @Transactional
    void updateServiceImageUnknownServiceThrows() {
        String servicesJson = """
                              [{"serviceId":"web","name":"Web","image":"img:1.0","replicas":2,
                                "ports":[],"env":{},"resources":{"cpuRequest":"100m","memoryRequest":"128Mi","cpuLimit":"500m","memoryLimit":"512Mi"},
                                "dependsOn":[],"healthCheck":null,"targetClusters":[]}]
                              """;
        var app = lifecycleService.createDraft("image-unknown", "Test", servicesJson, "default");
        app.status = ApplicationStatus.RUNNING;
        app.persist();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> lifecycleService.updateServiceImage(
                        app.id, "nonexistent", "img:2.0", "default"))
                .withMessageContaining("nonexistent");
    }

    @Test
    @Transactional
    void updateServiceImageUnknownApplicationThrows() {
        java.util.UUID bogusId = java.util.UUID.randomUUID();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> lifecycleService.updateServiceImage(
                        bogusId, "web", "img:2.0", "default"))
                .withMessageContaining(bogusId.toString());
    }

    @Test
    @Transactional
    void rollbackToDeploymentRestoresTopology() {
        String servicesJsonV1 = """
                                [{"serviceId":"web","name":"Web","image":"myapp/web:1.0","replicas":2,
                                  "ports":[],"env":{},"resources":{"cpuRequest":"100m","memoryRequest":"128Mi","cpuLimit":"500m","memoryLimit":"512Mi"},
                                  "dependsOn":[],"healthCheck":null,"targetClusters":[]}]
                                """;
        String servicesJsonV2 = """
                                [{"serviceId":"web","name":"Web","image":"myapp/web:2.0","replicas":3,
                                  "ports":[],"env":{},"resources":{"cpuRequest":"100m","memoryRequest":"128Mi","cpuLimit":"500m","memoryLimit":"512Mi"},
                                  "dependsOn":[],"healthCheck":null,"targetClusters":[]}]
                                """;
        var app = lifecycleService.createDraft("rollback-deploy-test", "Test", servicesJsonV2, "default");
        app.status = ApplicationStatus.RUNNING;
        app.persist();

        var record = new io.casehub.ops.app.entity.DeploymentRecordEntity();
        record.applicationId = app.id;
        record.topologyJson  = servicesJsonV1;
        record.trigger       = io.casehub.ops.app.model.DeploymentTrigger.INITIAL;
        record.outcome       = io.casehub.ops.app.model.DeploymentOutcome.SUCCESS;
        record.persist();

        lifecycleService.rollbackToDeployment(app.id, record.id, "default");

        var updated = ApplicationEntity.<ApplicationEntity>findById(app.id);
        assertThat(updated.servicesJson).contains("myapp/web:1.0");
        assertThat(updated.servicesJson).doesNotContain("myapp/web:2.0");
    }

    @Test
    @Transactional
    void rollbackToDeploymentUnknownDeploymentThrows() {
        var app = lifecycleService.createDraft("rollback-unknown-deploy", "Test", "[]", "default");
        app.status = ApplicationStatus.RUNNING;
        app.persist();

        java.util.UUID bogusDeployId = java.util.UUID.randomUUID();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> lifecycleService.rollbackToDeployment(
                        app.id, bogusDeployId, "default"))
                .withMessageContaining(bogusDeployId.toString());
    }

    @Test
    @Transactional
    void rollbackToDeploymentUnknownApplicationThrows() {
        java.util.UUID bogusAppId    = java.util.UUID.randomUUID();
        java.util.UUID bogusDeployId = java.util.UUID.randomUUID();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> lifecycleService.rollbackToDeployment(
                        bogusAppId, bogusDeployId, "default"))
                .withMessageContaining(bogusAppId.toString());
    }
}
