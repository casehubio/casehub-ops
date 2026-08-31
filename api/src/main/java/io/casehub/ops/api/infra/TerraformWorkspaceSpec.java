package io.casehub.ops.api.infra;

import java.util.Objects;

import io.casehub.desiredstate.api.NodeTypeId;
import io.casehub.ops.api.infra.types.TerraformBackendConfig;

@NodeTypeId("terraform_workspace")
public record TerraformWorkspaceSpec(String workspacePath, TerraformBackendConfig state) implements InfraNodeSpec {

    public TerraformWorkspaceSpec {
        Objects.requireNonNull(workspacePath, "workspacePath");
        Objects.requireNonNull(state, "state");
    }

    @Override
    public String resourceType() {
        return "terraform_workspace";
    }
}
