package org.jvnet.hudson.annotation_indexer;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeMap;
import javax.annotation.processing.SupportedSourceVersion;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import net.java.dev.hickory.testing.Compilation;

// XXX partial copy of class from Stapler; should be pushed up into Hickory ASAP
class Utils {

    /**
     * Filter out warnings about {@link SupportedSourceVersion}.
     * {@code metainf-services-1.1.jar} produces {@code warning: No SupportedSourceVersion annotation found on org.kohsuke.metainf_services.AnnotationProcessorImpl, returning RELEASE_6.} which is irrelevant to us.
     * (Development versions have already fixed this; when released and used here, delete this method.)
     */
    public static List<Diagnostic<? extends JavaFileObject>> filterSupportedSourceVersionWarnings(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        List<Diagnostic<? extends JavaFileObject>> r = new ArrayList<Diagnostic<? extends JavaFileObject>>();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics) {
            if (!d.getMessage(Locale.ENGLISH).contains("SupportedSourceVersion")) {
                r.add(d);
            }
        }
        return r;
    }

    private static JavaFileManager fileManager(Compilation compilation) {
        try {
            Field f = Compilation.class.getDeclaredField("jfm");
            f.setAccessible(true);
            return (JavaFileManager) f.get(compilation);
        } catch (Exception x) {
            throw new AssertionError(x);
        }
    }

    /**
     * Replacement for {@link Compilation#getGeneratedResource} that actually works.
     * https://code.google.com/p/jolira-tools/issues/detail?id=11
     */
    public static String getGeneratedResource(Compilation compilation, String filename) {
        try {
            FileObject fo = fileManager(compilation).getFileForOutput(StandardLocation.CLASS_OUTPUT, "", filename, null);
            if (fo == null) {
                return null;
            }
            return fo.getCharContent(true).toString();
        } catch (FileNotFoundException x) {
            return null;
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    private Utils() {}

}
