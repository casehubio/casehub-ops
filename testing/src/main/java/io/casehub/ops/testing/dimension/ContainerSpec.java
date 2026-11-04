package io.casehub.ops.testing.dimension;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface ContainerSpec {
    String name();

    String image();

    String[] env() default {};
}
