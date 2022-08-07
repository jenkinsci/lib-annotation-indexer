package org.jvnet.hudson.annotation_indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.karuslabs.elementary.Results;
import com.karuslabs.elementary.junit.JavacExtension;
import com.karuslabs.elementary.junit.annotations.Inline;
import com.karuslabs.elementary.junit.annotations.Options;
import com.karuslabs.elementary.junit.annotations.Processors;
import java.io.IOException;
import java.lang.annotation.ElementType;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(JavacExtension.class)
@Options("-Werror")
@Processors(AnnotationProcessorImpl.class)
class AnnotationProcessorImplTest {

    @Inline(
            name = "some.api.A",
            source = {
                "package some.api;",
                "@org.jvnet.hudson.annotation_indexer.Indexed @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface A {}",
            })
    @Inline(
            name = "some.pkg.Stuff",
            source = {
                "package some.pkg;",
                "@some.api.A public class Stuff {}",
            })
    @Test
    void allInOne(Results results) {
        assertEquals(Collections.emptyList(), results.diagnostics);
        assertEquals("some.pkg.Stuff" + System.getProperty("line.separator"), Utils.getGeneratedResource(results.sources, "META-INF/services/annotations/some.api.A"));
    }

    @Inline(
            name = "some.pkg.A",
            source = {
                "package some.pkg;",
                "@org.jvnet.hudson.annotation_indexer.Indexed @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface A {}",
            })
    @Inline(
            name = "some.pkg.Stuff",
            source = {
                "package some.pkg;",
                "@A public class Stuff {}",
            })
    @Test
    void separate(Results results) {
        assertEquals(Collections.emptyList(), results.diagnostics);
        assertEquals("some.pkg.Stuff" + System.getProperty("line.separator"), Utils.getGeneratedResource(results.sources, "META-INF/services/annotations/some.pkg.A"));
    }

    @Inline(
            name = "some.pkg.A",
            source = {
                "package some.pkg;",
                "@org.jvnet.hudson.annotation_indexer.Indexed @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface A {}",
            })
    @Inline(
            name = "some.pkg.Stuff",
            source = {
                "package some.pkg;",
                "@A public class Stuff {}",
            })
    @Inline(
            name = "some.pkg.MoreStuff",
            source = {
                "package some.pkg;",
                "@A public class MoreStuff {}",
            })
    @Test
    void multiple(Results results) {
        assertEquals(Collections.emptyList(), results.diagnostics);
        assertEquals("some.pkg.MoreStuff" + System.getProperty("line.separator") + "some.pkg.Stuff" + System.getProperty("line.separator"), Utils.getGeneratedResource(results.sources, "META-INF/services/annotations/some.pkg.A"));
    }

    @Inline(
            name = "some.pkg.B",
            source = {
                "package some.pkg;",
                "@org.jvnet.hudson.annotation_indexer.Indexed @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @java.lang.annotation.Inherited public @interface B {}",
            })
    @Inline(
            name = "some.pkg.Super",
            source = {
                "package some.pkg;",
                "@B public abstract class Super {}",
            })
    @Inline(
            name = "some.pkg.Stuff",
            source = {
                "package some.pkg;",
                "public class Stuff extends Super {}",
            })
    @Test
    void subclass(Results results) {
        assertEquals(Collections.emptyList(), results.diagnostics);
        /* XXX #7188605 currently broken on JDK 6; perhaps need to use a ElementScanner6 on roundEnv.rootElements whose visitType checks for annotations
        assertEquals("some.pkg.Stuff\n", Utils.getGeneratedResource(results.sources, "META-INF/services/annotations/some.pkg.B"));
        */
    }

    @Indexed @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) public @interface C {}
    public interface Inaccessible {}
    public static class Problematic {@C public Inaccessible bad() {return null;}}
    public static class Fine {@C public String good() {return null;}}
    public static class StillOK {@C public void whatever() {}}
    @Test void linkageErrorRobustness() throws Exception {
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
    @Test void constructors() throws Exception {
        Iterator<AnnotatedElement> it = Index.list(OnConst.class, Stuff.class.getClassLoader()).iterator();
        assertTrue(it.hasNext());
        Constructor<?> c = (Constructor<?>) it.next();
        assertEquals(Stuff.class, c.getDeclaringClass());
        assertFalse(it.hasNext());
    }


    @Indexed @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.PACKAGE) public @interface OnPackage {}
    @Test void packageinfo() throws IOException {
        Iterator<AnnotatedElement> it = Index.list(OnPackage.class, Stuff.class.getClassLoader()).iterator();
        assertTrue(it.hasNext());
        final Package p = (Package) it.next();
        assertEquals("org.jvnet.hudson.annotation_indexer", p.getName());
    }

}
