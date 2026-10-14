package io.casehub.ops.service.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class TopologyYamlCompilationTest {

    @Test
    void topologyYamlExistsOnClasspath() {
        InputStream is = getClass().getClassLoader()
                .getResourceAsStream("META-INF/desiredstate/casehub-topology.yaml");
        assertNotNull(is, "casehub-topology.yaml must exist on classpath");
    }

    @Test
    void topologyYamlParsesAsValidYaml() throws Exception {
        InputStream is = getClass().getClassLoader()
                .getResourceAsStream("META-INF/desiredstate/casehub-topology.yaml");
        var mapper = new ObjectMapper(new YAMLFactory());
        var tree = mapper.readTree(is);
        assertNotNull(tree.get("desiredState"), "Must have desiredState root");
        assertNotNull(tree.get("nodes"), "Must have nodes section");
        assertTrue(tree.get("nodes").has("qhorus"), "Must declare qhorus");
        assertTrue(tree.get("nodes").has("eidos"), "Must declare eidos");
        assertTrue(tree.get("nodes").has("engine"), "Must declare engine");
        assertTrue(tree.get("nodes").has("ops-app"), "Must declare ops-app");
    }

    @Test
    void infraModuleLocalDistributedExistsOnClasspath() {
        InputStream is = getClass().getClassLoader()
                .getResourceAsStream("META-INF/desiredstate/modules/casehub-infra-local-distributed.yaml");
        assertNotNull(is, "casehub-infra-local-distributed.yaml must exist on classpath");
    }
}
