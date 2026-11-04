package io.casehub.ops.testing.dimension;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(DimensionTestExtension.class)
@Tag("integration-live")
public @interface DimensionTest {
    ContainerSpec[] containers();

    Class<? extends TestDimension>[] dimensions();
}
