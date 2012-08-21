package org.jvnet.hudson.annotation_indexer;

import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

/**
 * Marks annotations that should be indexed during compilation.
 *
 * The annotations indexed this way can be later enumerated efficiently with {@link Index}.
 *
 * @author Kohsuke Kawaguchi
 */
@Documented
@Retention(RUNTIME)
@Target(ANNOTATION_TYPE)
public @interface Indexed {
    Class<? extends Validator>[] validators() default {};
}
