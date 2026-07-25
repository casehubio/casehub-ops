package io.casehub.ops.infra.standalone;

import io.casehub.desiredstate.api.NodeId;
import io.casehub.ops.api.infra.InfraNodeSpec;
import io.casehub.ops.api.infra.context.InfraProvisionContext;
import io.casehub.ops.api.infra.plan.ProvisionPlan;
import io.casehub.ops.api.infra.spi.BackendDeprovisionResult;
import io.casehub.ops.api.infra.spi.BackendProvisionResult;
import io.casehub.ops.api.infra.spi.InfraBackend;
import io.casehub.ops.api.infra.spi.ResourceProvisioner;
import io.casehub.ops.api.infra.state.DriftReport;
import io.casehub.ops.api.infra.state.ResourceOutputs;
import io.casehub.ops.api.infra.state.ResourceState;
import io.casehub.ops.api.infra.state.ResourceStatus;
import io.casehub.ops.api.infra.task.ProvisionOutcome;
import io.casehub.ops.api.infra.task.ProvisionTask;
import io.casehub.ops.api.infra.task.TaskAction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CaseHub-native provisioning backend. Delegates task execution to
 * {@link ResourceProvisioner} implementations discovered via CDI, tracking
 * state in a {@link ConcurrentHashMap}.
 *
 * <p>This backend provides the "standalone" provisioning path — no external
 * tools (Terraform, Ansible) required. The {@link InMemoryResourceProvisioner}
 * at {@code @Priority(0)} acts as the default catch-all; production provisioners
 * register at higher priorities for specific resource types.
 */
@ApplicationScoped
public class StandaloneBackend implements InfraBackend {

    private final List<ResourceProvisioner> provisioners;
    private final ConcurrentHashMap<NodeId, ResourceState> stateStore = new ConcurrentHashMap<>();

    @Inject
    public StandaloneBackend(@Any Instance<ResourceProvisioner> provisioners) {
        // CDI Instance iteration follows @Priority ordering (highest first)
        this.provisioners = provisioners.stream().toList();
    }

    /** Test constructor — accepts an explicit list of provisioners. */
    public StandaloneBackend(List<ResourceProvisioner> provisioners) {
        this.provisioners = List.copyOf(provisioners);
    }

    @Override
    public String backendId() {
        return "standalone";
    }

    @Override
    public BackendProvisionResult provision(InfraNodeSpec spec, InfraProvisionContext context) {
        var provisioner = findProvisioner(spec);
        if (provisioner == null) {
            return new BackendProvisionResult.Failed(
                    "No provisioner handles resource type: " + spec.resourceType(), false);
        }

        var currentState = stateStore.get(context.nodeId());
        var action       = currentState != null ? TaskAction.UPDATE : TaskAction.CREATE;
        var task         = new ProvisionTask(context.nodeId(), spec, action, currentState);

        ProvisionOutcome outcome = provisioner.execute(task);
        return mapProvisionOutcome(context.nodeId(), outcome);
    }

    @Override
    public BackendDeprovisionResult deprovision(InfraNodeSpec spec, InfraProvisionContext context) {
        var provisioner = findProvisioner(spec);
        if (provisioner == null) {
            return new BackendDeprovisionResult.Failed(
                    "No provisioner handles resource type: " + spec.resourceType(), false);
        }

        var currentState = stateStore.get(context.nodeId());
        var task         = new ProvisionTask(context.nodeId(), spec, TaskAction.DESTROY, currentState);

        ProvisionOutcome outcome = provisioner.execute(task);
        return mapDeprovisionOutcome(context.nodeId(), outcome);
    }

    @Override
    public ResourceState readState(NodeId nodeId, InfraNodeSpec spec) {
        var state = stateStore.get(nodeId);
        if (state != null) {
            return state;
        }
        return new ResourceState(
                nodeId, "unknown", ResourceStatus.UNKNOWN, Instant.now(), null, ResourceOutputs.empty());
    }

    @Override
    public DriftReport detectDrift(NodeId nodeId, InfraNodeSpec spec) {
        return new DriftReport(
                nodeId, false, List.of(), Instant.now(), backendId());
    }

    @Override
    public Optional<ProvisionPlan> plan(InfraNodeSpec spec, InfraProvisionContext context) {
        return Optional.empty();
    }

    private ResourceProvisioner findProvisioner(InfraNodeSpec spec) {
        for (var provisioner : provisioners) {
            if (provisioner.handles(spec)) {
                return provisioner;
            }
        }
        return null;
    }

    private BackendProvisionResult mapProvisionOutcome(NodeId nodeId, ProvisionOutcome outcome) {
        if (outcome.success() && outcome.resultState() != null) {
            stateStore.put(nodeId, outcome.resultState());
            return new BackendProvisionResult.Provisioned(outcome.resultState());
        }
        return new BackendProvisionResult.Failed(
                outcome.executionLog() != null ? outcome.executionLog() : "provision failed", false);
    }

    private BackendDeprovisionResult mapDeprovisionOutcome(NodeId nodeId, ProvisionOutcome outcome) {
        if (outcome.success()) {
            stateStore.remove(nodeId);
            return new BackendDeprovisionResult.Deprovisioned(nodeId);
        }
        return new BackendDeprovisionResult.Failed(
                outcome.executionLog() != null ? outcome.executionLog() : "deprovision failed", false);
    }
}
