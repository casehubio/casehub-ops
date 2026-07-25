package io.casehub.ops.api.infra.spi;

import io.casehub.ops.api.infra.InfraNodeSpec;
import io.casehub.ops.api.infra.task.ProvisionOutcome;
import io.casehub.ops.api.infra.task.ProvisionTask;

public interface ResourceProvisioner {

    String provisionerId();

    boolean handles(InfraNodeSpec spec);

    ProvisionOutcome execute(ProvisionTask task);
}
