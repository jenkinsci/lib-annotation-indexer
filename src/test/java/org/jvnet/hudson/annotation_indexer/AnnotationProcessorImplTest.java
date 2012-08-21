package org.jvnet.hudson.annotation_indexer;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import net.java.dev.hickory.testing.Compilation;
import org.junit.Test;
import static org.junit.Assert.*;

public class AnnotationProcessorImplTest {

    @Test public void allInOne() {
        Compilation compilation = new Compilation();
        compilation.addSource("some.api.A").
                addLine("package some.api;").
                addLine("@" + Indexed.class.getCanonicalName() + " @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface A {}");
        compilation.addSource("some.pkg.Stuff").
                addLine("package some.pkg;").
                addLine("@some.api.A public class Stuff {}");
        compilation.doCompile(null, "-source", "6");
        assertEquals(Collections.emptyList(), Utils.filterSupportedSourceVersionWarnings(compilation.getDiagnostics()));
        assertEquals("some.pkg.Stuff\n", Utils.getGeneratedResource(compilation, "META-INF/annotations/some.api.A"));
    }

    @Indexed @Retention(RetentionPolicy.RUNTIME) public @interface A {}
    @Test public void separate() {
        Compilation compilation = new Compilation();
        compilation.addSource("some.pkg.Stuff").
                addLine("package some.pkg;").
                addLine("@" + A.class.getCanonicalName() + " public class Stuff {}");
        compilation.doCompile(null, "-source", "6");
        assertEquals(Collections.emptyList(), Utils.filterSupportedSourceVersionWarnings(compilation.getDiagnostics()));
        assertEquals("some.pkg.Stuff\n", Utils.getGeneratedResource(compilation, "META-INF/annotations/" + A.class.getName()));
    }

    @Test public void incremental() {
        Compilation compilation = new Compilation();
        compilation.addSource("some.pkg.Stuff").
                addLine("package some.pkg;").
                addLine("@" + A.class.getCanonicalName() + " public class Stuff {}");
        compilation.doCompile(null, "-source", "6");
        assertEquals(Collections.emptyList(), Utils.filterSupportedSourceVersionWarnings(compilation.getDiagnostics()));
        assertEquals("some.pkg.Stuff\n", Utils.getGeneratedResource(compilation, "META-INF/annotations/" + A.class.getName()));
        compilation = new Compilation(compilation);
        compilation.addSource("some.pkg.MoreStuff").
                addLine("package some.pkg;").
                addLine("@" + A.class.getCanonicalName() + " public class MoreStuff {}");
        compilation.doCompile(null, "-source", "6");
        assertEquals(Collections.emptyList(), Utils.filterSupportedSourceVersionWarnings(compilation.getDiagnostics()));
        assertEquals("some.pkg.MoreStuff\nsome.pkg.Stuff\n", Utils.getGeneratedResource(compilation, "META-INF/annotations/" + A.class.getName()));
    }

    @Indexed @Retention(RetentionPolicy.RUNTIME) @Inherited public @interface B {}
    @B public static abstract class Super {}
    @Test public void subclass() {
        Compilation compilation = new Compilation();
        compilation.addSource("some.pkg.Stuff").
                addLine("package some.pkg;").
                addLine("public class Stuff extends " + Super.class.getCanonicalName() + " {}");
        compilation.doCompile(null, "-source", "6");
        assertEquals(Collections.emptyList(), Utils.filterSupportedSourceVersionWarnings(compilation.getDiagnostics()));
        /* XXX #7188605 currently broken on JDK 6; perhaps need to use a ElementScanner6 on roundEnv.rootElements whose visitType checks for annotations
        assertEquals("some.pkg.Stuff\n", Utils.getGeneratedResource(compilation, "META-INF/annotations/" + B.class.getName()));
        */
    }

}
