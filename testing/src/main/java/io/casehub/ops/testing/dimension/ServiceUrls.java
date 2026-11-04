package io.casehub.ops.testing.dimension;

import io.casehub.ops.api.deployment.ApplicationDeploymentTarget;

public class ServiceUrls {

    private final ApplicationDeploymentTarget target;

    public ServiceUrls(ApplicationDeploymentTarget target) {
        this.target = target;
    }

    public String serviceUrl(String serviceName) {
        return target.serviceUrl(serviceName)
            .orElseThrow(() -> new IllegalStateException(
                "No service URL for service '" + serviceName + "'"));
    }
}
