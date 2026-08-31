package io.casehub.ops.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.desiredstate.api.NodeSpec;
import io.casehub.desiredstate.api.NodeSpecFactory;
import io.casehub.ops.api.infra.InfraDesiredNodeSpec;
import io.casehub.ops.api.infra.InfraNodeSpec;

import java.util.LinkedHashMap;
import java.util.Map;

public class InfraWrappingFactory implements NodeSpecFactory {

    private final Class<? extends InfraNodeSpec> specClass;
    private final ObjectMapper mapper;
    private final String defaultBackend;

    public InfraWrappingFactory(Class<? extends InfraNodeSpec> specClass,
                                ObjectMapper mapper, String defaultBackend) {
        this.specClass = specClass;
        this.mapper = mapper;
        this.defaultBackend = defaultBackend;
    }

    @Override
    public NodeSpec create(Map<String, Object> specMap) {
        Map<String, Object> infraFields = new LinkedHashMap<>(specMap);
        String backendId = (String) infraFields.remove("backendId");
        if (backendId == null) backendId = defaultBackend;
        InfraNodeSpec infraSpec = mapper.convertValue(infraFields, specClass);
        return new InfraDesiredNodeSpec(infraSpec, backendId);
    }
}
