package io.casehub.ops.testing.dimension;

import java.util.Set;

public interface TestDimension {

    String name();

    Set<String> requires();

    void execute(ServiceUrls urls, DimensionState state) throws Exception;
}
