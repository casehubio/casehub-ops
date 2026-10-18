package io.casehub.ops.claudony;

import io.casehub.ops.api.claudony.ClaudonyAppNodeSpec;
import io.casehub.ops.api.claudony.ClaudonyPoolNodeSpec;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudonyNodeSpecFactoryProviderTest {

    private final ClaudonyNodeSpecFactoryProvider provider = new ClaudonyNodeSpecFactoryProvider();

    @Test
    void provides_factories_for_app_and_pool() {
        var factories = provider.provide();
        assertThat(factories).containsKeys("claudony_app", "claudony_pool");
    }

    @Test
    void app_factory_creates_ClaudonyAppNodeSpec() {
        var factories = provider.provide();
        var spec = factories.get("claudony_app").create(Map.of(
            "name", "my-app", "image", "img:1", "config", Map.of()));
        assertThat(spec).isInstanceOf(ClaudonyAppNodeSpec.class);
        assertThat(((ClaudonyAppNodeSpec) spec).name()).isEqualTo("my-app");
    }

    @Test
    void pool_factory_creates_ClaudonyPoolNodeSpec() {
        var factories = provider.provide();
        var spec = factories.get("claudony_pool").create(Map.of(
            "appName", "my-app", "agentId", "agent-1", "minActive", 1, "maxActive", 3));
        assertThat(spec).isInstanceOf(ClaudonyPoolNodeSpec.class);
        assertThat(((ClaudonyPoolNodeSpec) spec).agentId()).isEqualTo("agent-1");
    }
}
