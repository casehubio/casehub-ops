package io.casehub.ops.testing.dimension;

import io.casehub.ops.api.deployment.ApplicationDeploymentTarget;
import io.casehub.ops.api.deployment.DeployConfig;
import io.casehub.ops.api.deployment.DeployResult;
import org.junit.jupiter.api.extension.*;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Stream;

public class DimensionTestExtension implements
        BeforeAllCallback, AfterAllCallback, TestTemplateInvocationContextProvider {

    private static final ExtensionContext.Namespace NS =
        ExtensionContext.Namespace.create(DimensionTestExtension.class);

    static List<TestDimension> topologicalSort(List<TestDimension> dimensions) {
        Map<String, TestDimension> byName = new LinkedHashMap<>();
        for (TestDimension d : dimensions) byName.put(d.name(), d);

        Map<String, Integer> inDegree = new LinkedHashMap<>();
        Map<String, Set<String>> dependents = new LinkedHashMap<>();
        for (TestDimension d : dimensions) {
            inDegree.putIfAbsent(d.name(), 0);
            dependents.putIfAbsent(d.name(), new TreeSet<>());
            for (String req : d.requires()) {
                if (!byName.containsKey(req))
                    throw new IllegalStateException(
                        "Dimension '" + d.name() + "' requires '" + req
                        + "' which is not in the declared dimensions");
                dependents.computeIfAbsent(req, k -> new TreeSet<>()).add(d.name());
                inDegree.merge(d.name(), 1, Integer::sum);
            }
        }

        PriorityQueue<String> queue = new PriorityQueue<>();
        for (var e : inDegree.entrySet())
            if (e.getValue() == 0) queue.add(e.getKey());

        List<TestDimension> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String name = queue.poll();
            sorted.add(byName.get(name));
            for (String dep : dependents.getOrDefault(name, Set.of())) {
                int newDegree = inDegree.merge(dep, -1, Integer::sum);
                if (newDegree == 0) queue.add(dep);
            }
        }

        if (sorted.size() != dimensions.size())
            throw new IllegalStateException(
                "Dependency cycle detected among dimensions: "
                + byName.keySet());

        return sorted;
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        Class<?> testClass = context.getRequiredTestClass();
        DimensionTest annotation = testClass.getAnnotation(DimensionTest.class);
        if (annotation == null) return;

        ApplicationDeploymentTarget target = resolveTarget(testClass);
        ContainerSpec[] specs = annotation.containers();

        if (specs.length > 0) {
            for (ContainerSpec spec : specs) {
                try { target.undeploy(spec.name()); } catch (Exception ignored) {}

                Map<String, Object> configMap = new LinkedHashMap<>();
                for (String pair : spec.env()) {
                    int eq = pair.indexOf('=');
                    if (eq > 0) configMap.put(
                        pair.substring(0, eq), pair.substring(eq + 1));
                }
                DeployConfig config = new DeployConfig(
                    spec.name(), 1, configMap, Map.of());
                DeployResult result = target.deploy(spec.image(), config);
                if (result instanceof DeployResult.Failed f)
                    throw new IllegalStateException(
                        "Failed to deploy " + spec.name() + ": " + f.reason());
            }
        }

        ServiceUrls serviceUrls = new ServiceUrls(target);

        List<TestDimension> dimensions = new ArrayList<>();
        for (Class<? extends TestDimension> cls : annotation.dimensions())
            dimensions.add(cls.getDeclaredConstructor().newInstance());
        List<TestDimension> sorted = topologicalSort(dimensions);

        DimensionState dimensionState = new DimensionState();
        if (specs.length > 0) {
            dimensionState.put("containerName", specs[0].name());
        }

        context.getStore(NS).put("target", target);
        context.getStore(NS).put("serviceUrls", serviceUrls);
        context.getStore(NS).put("dimensions", sorted);
        context.getStore(NS).put("dimensionState", dimensionState);
        context.getStore(NS).put("failed", new HashSet<String>());
        context.getStore(NS).put("containerSpecs", specs);
    }

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getRequiredTestClass()
            .isAnnotationPresent(DimensionTest.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
            ExtensionContext context) {
        List<TestDimension> sorted = (List<TestDimension>)
            context.getStore(NS).get("dimensions");
        return sorted.stream().map(DimensionInvocationContext::new);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        ApplicationDeploymentTarget target = (ApplicationDeploymentTarget)
            context.getStore(NS).get("target");
        ContainerSpec[] specs = (ContainerSpec[])
            context.getStore(NS).get("containerSpecs");
        if (target != null && specs != null) {
            for (ContainerSpec spec : specs) {
                try { target.undeploy(spec.name()); }
                catch (Exception ignored) {}
            }
        }
        if (target instanceof AutoCloseable ac) {
            try { ac.close(); } catch (Exception ignored) {}
        }
    }

    private ApplicationDeploymentTarget resolveTarget(Class<?> testClass) {
        try {
            Method method = testClass.getDeclaredMethod("createTarget");
            method.setAccessible(true);
            return (ApplicationDeploymentTarget) method.invoke(null);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                "Test class " + testClass.getName()
                + " must define a static createTarget() method returning ApplicationDeploymentTarget");
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to invoke createTarget() on " + testClass.getName(), e);
        }
    }

    private static class DimensionInvocationContext
            implements TestTemplateInvocationContext {
        private final TestDimension dimension;

        DimensionInvocationContext(TestDimension dimension) {
            this.dimension = dimension;
        }

        @Override
        public String getDisplayName(int invocationIndex) {
            return dimension.name();
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            return List.of(new DimensionExecutor(dimension));
        }
    }

    private static class DimensionExecutor implements BeforeTestExecutionCallback {
        private final TestDimension dimension;

        DimensionExecutor(TestDimension dimension) {
            this.dimension = dimension;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void beforeTestExecution(ExtensionContext context) throws Exception {
            Set<String> failed = (Set<String>)
                context.getStore(NS).get("failed");

            for (String req : dimension.requires()) {
                if (failed.contains(req)) {
                    throw new org.opentest4j.TestAbortedException(
                        "Skipped: dependency '" + req + "' failed");
                }
            }

            ServiceUrls urls = (ServiceUrls)
                context.getStore(NS).get("serviceUrls");
            DimensionState state = (DimensionState)
                context.getStore(NS).get("dimensionState");

            try {
                dimension.execute(urls, state);
            } catch (Exception | AssertionError e) {
                failed.add(dimension.name());
                throw e;
            }
        }
    }
}
