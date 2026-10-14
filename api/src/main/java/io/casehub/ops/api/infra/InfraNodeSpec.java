package io.casehub.ops.api.infra;

import io.casehub.desiredstate.api.NodeSpec;
import io.casehub.desiredstate.api.NodeType;

public sealed interface InfraNodeSpec extends NodeSpec
        permits K8sNamespaceSpec, K8sDeploymentSpec, K8sServiceSpec, K8sIngressSpec,
                K8sConfigMapSpec,
                ComputeInstanceSpec, DatabaseClusterSpec,
                TerraformWorkspaceSpec, AnsiblePlaybookSpec,
                GenericResourceSpec,
                LoadBalancerSpec, SidecarProxySpec, ServiceMeshControlPlaneSpec,
                DnsFailoverSpec, DataReplicationSpec {

    String resourceType();

    @Override
    default NodeType nodeType() { return NodeType.of(resourceType()); }
}
