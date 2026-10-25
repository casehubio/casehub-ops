package io.casehub.ops.service.adaptation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.desiredstate.api.DesiredStateGraphFactory;
import io.casehub.ops.api.deployment.AdaptationRuleSpec;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AdaptationRuleCompiler {

    private final ObjectMapper mapper;

    public AdaptationRuleCompiler(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public List<AdaptationRule> compile(
            Map<String, Object> rawYaml,
            Map<String, Class<?>> nodeTypeRegistry,
            DesiredStateGraphFactory factory) {

        Object adaptationsObj = rawYaml.get("adaptations");
        if (adaptationsObj == null) {
            return List.of();
        }

        List<AdaptationRuleSpec> specs = mapper.convertValue(
                adaptationsObj, new TypeReference<>() {});

        return AdaptationRule.fromSpecs(
                specs, null, mapper, factory, null, nodeTypeRegistry);
    }
}
