package io.casehub.ops.api.deployment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DeployConfig(
        String applicationId,
        int replicas,
        Map<String, Object> config,
        Map<String, String> env,
        String healthPath
) {
    public DeployConfig {
        config = config != null ? Map.copyOf(config) : Map.of();
        env    = env != null ? Map.copyOf(env) : Map.of();
        if (healthPath == null || healthPath.isBlank()) {healthPath = "/q/health/ready";}
    }

    public DeployConfig(String applicationId, int replicas,
                        Map<String, Object> config, Map<String, String> env) {
        this(applicationId, replicas, config, env, null);
    }
}
