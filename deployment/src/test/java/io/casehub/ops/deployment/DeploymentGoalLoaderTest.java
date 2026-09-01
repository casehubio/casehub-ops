package io.casehub.ops.deployment;

import io.casehub.ops.api.deployment.AgentNodeSpec;
import io.casehub.ops.api.deployment.DeploymentGoals;
import io.casehub.ops.api.deployment.GoalEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeploymentGoalLoaderTest {

    private DeploymentGoalLoader loader;

    @BeforeEach
    void setUp() {
        loader = new DeploymentGoalLoader();
    }

    @Test
    void loadsSingleFile() {
        DeploymentGoals goals = loader.load("test-deployment/topology.yaml");
        assertThat(goals.agents()).hasSize(1);
        assertThat(goals.channels()).hasSize(1);
        assertThat(goals.caseTypes()).hasSize(1);
        assertThat(goals.trust()).hasSize(1);
        assertThat(goals.endpoints()).hasSize(1);
        assertThat(goals.agents().get(0).spec().agentId()).isEqualTo("test-agent");
    }

    @Test
    void loadsDirectoryAndMerges(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("agents.yaml"),
                "agents:\n  - spec:\n      agentId: a1\n      name: A1\n      slot: worker\n    dependsOn: []\n");
        Files.writeString(tempDir.resolve("channels.yaml"),
                "channels:\n  - spec:\n      name: ch/one\n      semantic: APPEND\n    dependsOn: []\n");

        DeploymentGoals goals = loader.loadDirectory(tempDir.toString());
        assertThat(goals.agents()).hasSize(1);
        assertThat(goals.channels()).hasSize(1);
        assertThat(goals.caseTypes()).isEmpty();
        assertThat(goals.trust()).isEmpty();
    }

    @Test
    void mergesConcatenatesLists() {
        var goals1 = new DeploymentGoals(
                List.of(new GoalEntry<>(minimalAgent("a1"), List.of())),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        var goals2 = new DeploymentGoals(
                List.of(new GoalEntry<>(minimalAgent("a2"), List.of())),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        var merged = loader.merge(goals1, goals2);
        assertThat(merged.agents()).hasSize(2);
    }

    @Test
    void missingFileThrows() {
        assertThatThrownBy(() -> loader.load("nonexistent.yaml"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void notADirectoryThrows(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("not-a-dir.yaml");
        Files.writeString(file, "agents: []");
        assertThatThrownBy(() -> loader.loadDirectory(file.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not a directory");
    }

    @Test
    void loadsEndpointFromYaml() {
        DeploymentGoals goals = loader.load("test-deployment/topology.yaml");
        assertThat(goals.endpoints()).hasSize(1);
        var endpoint = goals.endpoints().get(0).spec();
        assertThat(endpoint.path()).isEqualTo("test/kafka-stream");
        assertThat(endpoint.protocol()).isEqualTo(io.casehub.platform.api.endpoints.EndpointProtocol.KAFKA);
        assertThat(endpoint.properties()).containsEntry(io.casehub.platform.api.endpoints.EndpointPropertyKeys.TOPIC, "test.events");
    }

    @Test
    void loadsDurationFieldsFromDetectionYaml(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("detection.yaml"),
                          "detections:\n" +
                          "  - spec:\n" +
                          "      situationId: high-cpu\n" +
                          "      eventTypes: [metric.cpu]\n" +
                          "      correlationWindow: \"PT30S\"\n" +
                          "      eventBufferDelay: \"PT5S\"\n" +
                          "      chainMode:\n" +
                          "        type: count\n" +
                          "        ganglionId: cpu-ganglion\n" +
                          "        requiredCount: 3\n" +
                          "      triggerAction:\n" +
                          "        type: notify-only\n" +
                          "    dependsOn: []\n");

        DeploymentGoals goals = loader.load(tempDir.resolve("detection.yaml").toString());
        assertThat(goals.detections()).hasSize(1);
        var detection = goals.detections().get(0).spec();
        assertThat(detection.situationId()).isEqualTo("high-cpu");
        assertThat(detection.correlationWindow()).isEqualTo(Duration.ofSeconds(30));
        assertThat(detection.eventBufferDelay()).isEqualTo(Duration.ofSeconds(5));}

    @Test
    void mergePreservesDetections(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("detection.yaml"),
                          "detections:\n" +
                          "  - spec:\n" +
                          "      situationId: high-cpu\n" +
                          "      eventTypes: [metric.cpu]\n" +
                          "      correlationWindow: \"PT30S\"\n" +
                          "      eventBufferDelay: \"PT5S\"\n" +
                          "      chainMode:\n" +
                          "        type: count\n" +
                          "        ganglionId: cpu-ganglion\n" +
                          "        requiredCount: 3\n" +
                          "      triggerAction:\n" +
                          "        type: notify-only\n" +
                          "    dependsOn: []\n");

        DeploymentGoals goals = loader.loadDirectory(tempDir.toString());
        assertThat(goals.detections()).hasSize(1);
        assertThat(goals.detections().get(0).spec().situationId()).isEqualTo("high-cpu");
    }


    private AgentNodeSpec minimalAgent(String id) {
        return new AgentNodeSpec(id, "Agent", "worker",
                null, null, null, null, null, null, null, null, null, null,
                java.util.List.of(), null, null, null, null, java.util.List.of());
    }
}
