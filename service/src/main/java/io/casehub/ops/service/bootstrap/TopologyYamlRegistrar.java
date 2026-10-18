package io.casehub.ops.service.bootstrap;

import io.casehub.ops.service.topology.TopologyCompilerService;
import io.casehub.platform.bootstrap.spi.ClasspathYamlRegistrar;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.util.Set;

@ApplicationScoped
public class TopologyYamlRegistrar implements ClasspathYamlRegistrar {

    private static final Logger LOG = Logger.getLogger(TopologyYamlRegistrar.class);

    @Inject
    TopologyCompilerService topologyCompilerService;

    @Override
    public String sectionKey() {
        return "topology";
    }

    @Override
    public Set<String> classpathPaths() {
        return Set.of("topology");
    }

    @Override
    public int priority() {
        return 400;
    }

    @Override
    public void register(String resourcePath, InputStream content) {
        try {
            topologyCompilerService.compileDomain(resourcePath, java.util.Map.of());
            LOG.infof("Compiled topology from %s", resourcePath);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to compile topology from %s", resourcePath);
        }
    }
}
