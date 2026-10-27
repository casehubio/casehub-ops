package io.casehub.ops.testing.env;

public record LlmResponse(String appId, String role, String domain, String content,
                           String backendKey, String instanceId) {
    public boolean isSimulated() { return "simulation".equals(backendKey); }
    public boolean isReal() { return !isSimulated(); }
}
