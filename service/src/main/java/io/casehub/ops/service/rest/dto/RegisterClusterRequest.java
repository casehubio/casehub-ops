package io.casehub.ops.service.rest.dto;

import io.casehub.ops.service.model.ClusterType;

public record RegisterClusterRequest(
        String name,
        String apiUrl,
        String namespace,
        String credentialRef,
        ClusterType clusterType) {}
