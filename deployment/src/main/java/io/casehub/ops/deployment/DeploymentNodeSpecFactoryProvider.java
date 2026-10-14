package io.casehub.ops.deployment;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.desiredstate.api.NodeSpec;
import io.casehub.desiredstate.api.NodeSpecFactory;
import io.casehub.desiredstate.api.NodeSpecFactoryProvider;
import io.casehub.ops.api.deployment.CaseHubApplicationSpec;
import io.casehub.ops.api.deployment.DeploymentApplicationNodeSpec;

import java.util.LinkedHashMap;
import java.util.Map;

public class DeploymentNodeSpecFactoryProvider implements NodeSpecFactoryProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Map<String, NodeSpecFactory> provide() {
        return Map.of("casehub_application", this::createCaseHubApplication);
    }

    private NodeSpec createCaseHubApplication(Map<String, Object> specMap) {
        Map<String, Object> fields = new LinkedHashMap<>(specMap);
        String deploymentTarget = (String) fields.remove("deploymentTarget");
        String existing = (String) fields.remove("existing");
        CaseHubApplicationSpec appSpec = MAPPER.convertValue(fields, CaseHubApplicationSpec.class);
        if (deploymentTarget != null || existing != null) {
            return new DeploymentApplicationNodeSpec(appSpec, deploymentTarget, existing);
        }
        return appSpec;
    }
}
