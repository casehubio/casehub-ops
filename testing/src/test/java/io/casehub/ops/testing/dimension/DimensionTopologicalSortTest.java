package io.casehub.ops.testing.dimension;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DimensionTopologicalSortTest {

    static class DimA implements TestDimension {
        @Override public String name() { return "a"; }
        @Override public Set<String> requires() { return Set.of(); }
        @Override public void execute(ServiceUrls u, DimensionState s) {}
    }

    static class DimB implements TestDimension {
        @Override public String name() { return "b"; }
        @Override public Set<String> requires() { return Set.of("a"); }
        @Override public void execute(ServiceUrls u, DimensionState s) {}
    }

    static class DimC implements TestDimension {
        @Override public String name() { return "c"; }
        @Override public Set<String> requires() { return Set.of("a"); }
        @Override public void execute(ServiceUrls u, DimensionState s) {}
    }

    static class DimD implements TestDimension {
        @Override public String name() { return "d"; }
        @Override public Set<String> requires() { return Set.of("b", "c"); }
        @Override public void execute(ServiceUrls u, DimensionState s) {}
    }

    @Test
    void sortsInDependencyOrder() {
        List<TestDimension> sorted = DimensionTestExtension.topologicalSort(
            List.of(new DimD(), new DimB(), new DimA(), new DimC()));
        List<String> names = sorted.stream().map(TestDimension::name).toList();
        assertThat(names.indexOf("a")).isLessThan(names.indexOf("b"));
        assertThat(names.indexOf("a")).isLessThan(names.indexOf("c"));
        assertThat(names.indexOf("b")).isLessThan(names.indexOf("d"));
        assertThat(names.indexOf("c")).isLessThan(names.indexOf("d"));
    }

    @Test
    void alphabeticalTiebreakAtSameDepth() {
        List<TestDimension> sorted = DimensionTestExtension.topologicalSort(
            List.of(new DimC(), new DimB(), new DimA()));
        List<String> names = sorted.stream().map(TestDimension::name).toList();
        assertThat(names).containsExactly("a", "b", "c");
    }

    @Test
    void detectsCycle() {
        var cycleA = new TestDimension() {
            @Override public String name() { return "x"; }
            @Override public Set<String> requires() { return Set.of("y"); }
            @Override public void execute(ServiceUrls u, DimensionState s) {}
        };
        var cycleB = new TestDimension() {
            @Override public String name() { return "y"; }
            @Override public Set<String> requires() { return Set.of("x"); }
            @Override public void execute(ServiceUrls u, DimensionState s) {}
        };
        assertThatThrownBy(() -> DimensionTestExtension.topologicalSort(
            List.of(cycleA, cycleB)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cycle");
    }

    @Test
    void rejectsMissingDependency() {
        assertThatThrownBy(() -> DimensionTestExtension.topologicalSort(
            List.of(new DimB())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("a");
    }
}
