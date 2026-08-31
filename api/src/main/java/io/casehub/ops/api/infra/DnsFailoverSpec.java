package io.casehub.ops.api.infra;

import io.casehub.desiredstate.api.NodeTypeId;

import java.util.Objects;

@NodeTypeId("dns_failover")
public record DnsFailoverSpec(
        String domainName,
        String primaryEndpoint,
        String secondaryEndpoint,
        int ttlSeconds,
        FailoverPolicy policy) implements InfraNodeSpec {

    public DnsFailoverSpec {
        Objects.requireNonNull(domainName, "domainName");
        Objects.requireNonNull(primaryEndpoint, "primaryEndpoint");
        Objects.requireNonNull(secondaryEndpoint, "secondaryEndpoint");
        if (policy == null) policy = FailoverPolicy.FAILOVER;
    }

    @Override
    public String resourceType() { return "dns_failover"; }
}
