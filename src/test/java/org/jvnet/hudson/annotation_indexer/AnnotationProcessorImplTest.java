package org.jvnet.hudson.annotation_indexer;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Iterator;
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
        compilation.doCompile(null, "-source", "8");
        assertEquals(Collections.emptyList(), Utils.filterObsoleteSourceVersionWarnings(compilation.getDiagnostics()));
        assertEquals("some.pkg.Stuff" + System.getProperty("line.separator"), Utils.getGeneratedResource(compilation, "META-INF/services/annotations/some.api.A"));
    }

    @Indexed @Retention(RetentionPolicy.RUNTIME) public @interface A {}
    @Test public void separate() {
        Compilation compilation = new Compilation();
        compilation.addSource("some.pkg.Stuff").
                addLine("package some.pkg;").
                addLine("@" + A.class.getCanonicalName() + " public class Stuff {}");
        compilation.doCompile(null, "-source", "8");
        assertEquals(Collections.emptyList(), Utils.filterObsoleteSourceVersionWarnings(compilation.getDiagnostics()));
        assertEquals("some.pkg.Stuff" + System.getProperty("line.separator"), Utils.getGeneratedResource(compilation, "META-INF/services/annotations/" + A.class.getName()));
    }

    @Test public void incremental() {
        Compilation compilation = new Compilation();
        compilation.addSource("some.pkg.Stuff").
                addLine("package some.pkg;").
                addLine("@" + A.class.getCanonicalName() + " public class Stuff {}");
        compilation.doCompile(null, "-source", "8");
        assertEquals(Collections.emptyList(), Utils.filterObsoleteSourceVersionWarnings(compilation.getDiagnostics()));
        assertEquals("some.pkg.Stuff" + System.getProperty("line.separator"), Utils.getGeneratedResource(compilation, "META-INF/services/annotations/" + A.class.getName()));
        compilation = new Compilation(compilation);
        compilation.addSource("some.pkg.MoreStuff").
                addLine("package some.pkg;").
                addLine("@" + A.class.getCanonicalName() + " public class MoreStuff {}");
        compilation.doCompile(null, "-source", "8");
        assertEquals(Collections.emptyList(), Utils.filterObsoleteSourceVersionWarnings(compilation.getDiagnostics()));
        assertEquals("some.pkg.MoreStuff" + System.getProperty("line.separator") + "some.pkg.Stuff" + System.getProperty("line.separator"), Utils.getGeneratedResource(compilation, "META-INF/services/annotations/" + A.class.getName()));
    }

    @Indexed @Retention(RetentionPolicy.RUNTIME) @Inherited public @interface B {}
    @B public static abstract class Super {}
    @Test public void subclass() {
        Compilation compilation = new Compilation();
        compilation.addSource("some.pkg.Stuff").
                addLine("package some.pkg;").
                addLine("public class Stuff extends " + Super.class.getCanonicalName() + " {}");
        compilation.doCompile(null, "-source", "8");
        assertEquals(Collections.emptyList(), Utils.filterObsoleteSourceVersionWarnings(compilation.getDiagnostics()));
        /* XXX #7188605 currently broken on JDK 6; perhaps need to use a ElementScanner6 on roundEnv.rootElements whose visitType checks for annotations
        assertEquals("some.pkg.Stuff\n", Utils.getGeneratedResource(compilation, "META-INF/services/annotations/" + B.class.getName()));
        */
    }

    @Indexed @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) public @interface C {}
    public interface Inaccessible {}
    public static class Problematic {@C public Inaccessible bad() {return null;}}
    public static class Fine {@C public String good() {return null;}}
    public static class StillOK {@C public void whatever() {}}
    @Test public void linkageErrorRobustness() throws Exception {
        ClassLoader cl = new URLClassLoader(new URL[] {Index.class.getProtectionDomain().getCodeSource().getLocation(), AnnotationProcessorImplTest.class.getProtectionDomain().getCodeSource().getLocation()}, AnnotationProcessorImplTest.class.getClassLoader().getParent()) {
            @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (name.endsWith("$Inaccessible")) {
                    throw new ClassNotFoundException("this is intentionally denied");
                }
                return super.findClass(name);
            }
        };
        // Have to call Index.list using reflection because we have to pass C.class reflectively (otherwise it is not present on the marked classes), and C.class has to have Indexed.class:
        Method indexList = cl.loadClass(Index.class.getName()).getMethod("list", Class.class, ClassLoader.class, Class.class);
        @SuppressWarnings("unchecked") Iterator<Method> it = ((Iterable<Method>) indexList.invoke(null, cl.loadClass(C.class.getName()), cl, Method.class)).iterator();
        assertTrue(it.hasNext());
        Method m = it.next();
        assertEquals("good", m.getName());
        assertTrue(it.hasNext());
        m = it.next();
        assertEquals("whatever", m.getName());
        assertFalse(it.hasNext());
    }

    @Indexed @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.CONSTRUCTOR) public @interface OnConst {}
    public static class Stuff {@OnConst public Stuff() {}}
    @Test public void constructors() throws Exception {
        Iterator<AnnotatedElement> it = Index.list(OnConst.class, Stuff.class.getClassLoader()).iterator();
        assertTrue(it.hasNext());
        Constructor<?> c = (Constructor<?>) it.next();
        assertEquals(Stuff.class, c.getDeclaringClass());
        assertFalse(it.hasNext());
    }


    @Indexed @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.PACKAGE) public @interface OnPackage {}
    @Test public void packageinfo() throws IOException {
        Iterator<AnnotatedElement> it = Index.list(OnPackage.class, Stuff.class.getClassLoader()).iterator();
        assertTrue(it.hasNext());
        final Package p = (Package) it.next();
        assertEquals("org.jvnet.hudson.annotation_indexer", p.getName());
    }

}
