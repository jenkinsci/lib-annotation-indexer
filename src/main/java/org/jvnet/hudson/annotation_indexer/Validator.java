package org.jvnet.hudson.annotation_indexer;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;

/**
 * Checkes the usage of {@link Indexed} annotations at compile-time.
 *
 * @author Kohsuke Kawaguchi
 * @see Indexed
 */
public interface Validator {
    /**
     * Checks the occurrence of the {@link Indexed} annotation
     * and report any error. Useful for early error detection.
     */
    void check(Element use, RoundEnvironment e, ProcessingEnvironment env);
}
