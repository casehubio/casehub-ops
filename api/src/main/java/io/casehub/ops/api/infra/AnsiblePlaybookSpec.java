package io.casehub.ops.api.infra;

import io.casehub.desiredstate.api.NodeTypeId;
import io.casehub.ops.api.infra.types.AnsibleExtraVars;
import io.casehub.ops.api.infra.types.AnsibleInventory;

import java.util.Objects;

@NodeTypeId("ansible_playbook")
public record AnsiblePlaybookSpec(
        String playbookPath,
        AnsibleInventory inventory,
        AnsibleExtraVars extraVars) implements InfraNodeSpec {

    public AnsiblePlaybookSpec {
        Objects.requireNonNull(playbookPath, "playbookPath");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(extraVars, "extraVars");
    }

    @Override
    public String resourceType() {
        return "ansible_playbook";
    }
}
