package io.casehub.ops.service.topology;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TopologyCompilerServiceTest {

    @Test
    void listTopologiesFromPath_findsSubdirectories(@TempDir Path tempDir) throws IOException {
        Path topoDir = tempDir.resolve("topologies");
        Files.createDirectories(topoDir.resolve("alpha"));
        Files.createDirectories(topoDir.resolve("beta"));
        Files.writeString(topoDir.resolve("alpha").resolve("topology.yaml"), "nodes: {}");
        Files.writeString(topoDir.resolve("beta").resolve("topology.yaml"), "nodes: {}");

        var service = new TopologyCompilerService();
        List<TopologyDescriptor> topologies = service.listTopologiesFromPath(topoDir.toString());

        assertThat(topologies).hasSize(2);
        assertThat(topologies).extracting(TopologyDescriptor::name)
                .containsExactlyInAnyOrder("alpha", "beta");
        assertThat(topologies).allMatch(t -> "filesystem".equals(t.source()));
    }

    @Test
    void listTopologiesFromPath_emptyDirectory_returnsEmpty(@TempDir Path tempDir) throws IOException {
        Path topoDir = tempDir.resolve("topologies");
        Files.createDirectories(topoDir);

        var service = new TopologyCompilerService();
        List<TopologyDescriptor> topologies = service.listTopologiesFromPath(topoDir.toString());

        assertThat(topologies).isEmpty();
    }

    @Test
    void listTopologiesFromPath_nonExistentDirectory_returnsEmpty() {
        var service = new TopologyCompilerService();
        List<TopologyDescriptor> topologies = service.listTopologiesFromPath("/nonexistent/path");

        assertThat(topologies).isEmpty();
    }

    @Test
    void listTopologiesFromPath_ignoresNonDirectoryEntries(@TempDir Path tempDir) throws IOException {
        Path topoDir = tempDir.resolve("topologies");
        Files.createDirectories(topoDir.resolve("real-topo"));
        Files.writeString(topoDir.resolve("README.md"), "not a topology");

        var service = new TopologyCompilerService();
        List<TopologyDescriptor> topologies = service.listTopologiesFromPath(topoDir.toString());

        assertThat(topologies).hasSize(1);
        assertThat(topologies.get(0).name()).isEqualTo("real-topo");
    }
}
