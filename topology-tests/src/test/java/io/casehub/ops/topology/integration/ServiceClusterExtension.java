package io.casehub.ops.topology.integration;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.PostgreSQLContainer;

import io.casehub.ops.testing.env.TargetEnvironment;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServiceClusterExtension implements BeforeAllCallback, AfterAllCallback {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(ServiceClusterExtension.class);

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (TargetEnvironment.current() != TargetEnvironment.LOCAL) return;

        ServiceCluster cluster = context.getRequiredTestClass()
                .getAnnotation(ServiceCluster.class);
        if (cluster == null) return;

        ClusterState state = new ClusterState();
        context.getStore(NAMESPACE).put("state", state);

        boolean needsPostgres = false;
        for (Service svc : cluster.services()) {
            if (svc.name().equals("claudony")) {
                needsPostgres = true;
                break;
            }
        }

        if (needsPostgres) {
            @SuppressWarnings("resource")
            PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");
            pg.start();
            state.postgres = pg;
        }

        for (Service svc : cluster.services()) {
            int port = ServiceProcess.findFreePort();
            Map<String, String> props = buildProperties(svc, state);

            ServiceProcess proc = new ServiceProcess(
                    svc.name(), ServiceProcess.resolveJar(svc.repo()), port, props);
            proc.start();
            state.processes.add(proc);
            state.endpoints.put(svc.name(), proc.baseUrl());
        }

        injectEndpoints(context.getRequiredTestClass(), state.endpoints);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        ClusterState state = context.getStore(NAMESPACE)
                .remove("state", ClusterState.class);
        if (state == null) return;

        List<ServiceProcess> reversed = new ArrayList<>(state.processes);
        Collections.reverse(reversed);
        for (ServiceProcess proc : reversed) {
            proc.stop();
        }

        if (state.postgres != null) {
            state.postgres.stop();
        }
    }

    private Map<String, String> buildProperties(Service svc, ClusterState state) {
        Map<String, String> props = new HashMap<>();
        props.put("quarkus.profile", "test");
        props.put("quarkus.http.auth.permission.protected.policy", "permit");

        if (svc.name().equals("claudony") && state.postgres != null) {
            String reactiveUrl = "postgresql://" + state.postgres.getHost() + ":"
                    + state.postgres.getFirstMappedPort() + "/" + state.postgres.getDatabaseName();
            props.put("quarkus.datasource.qhorus.reactive.url", reactiveUrl);
            props.put("quarkus.datasource.qhorus.jdbc.url", state.postgres.getJdbcUrl());
            props.put("quarkus.datasource.qhorus.username", state.postgres.getUsername());
            props.put("quarkus.datasource.qhorus.password", state.postgres.getPassword());
        }

        return props;
    }

    private void injectEndpoints(Class<?> testClass, Map<String, String> endpoints)
            throws IllegalAccessException {
        for (Field field : testClass.getDeclaredFields()) {
            ServiceEndpoint ann = field.getAnnotation(ServiceEndpoint.class);
            if (ann != null) {
                String url = endpoints.get(ann.value());
                if (url == null) {
                    throw new IllegalStateException(
                            "@ServiceEndpoint(\"" + ann.value()
                            + "\") — no service with that name in @ServiceCluster");
                }
                field.setAccessible(true);
                field.set(null, url);
            }
        }
    }

    private static class ClusterState implements ExtensionContext.Store.CloseableResource {
        final List<ServiceProcess> processes = new ArrayList<>();
        final Map<String, String> endpoints = new HashMap<>();
        PostgreSQLContainer<?> postgres;

        @Override
        public void close() {}
    }
}
