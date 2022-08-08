package org.jvnet.hudson.annotation_indexer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

class Utils {
    public static String getGeneratedResource(List<JavaFileObject> generated, String filename) {
        JavaFileObject fo = generated.stream()
                .filter(it -> it.getName().equals("/" + StandardLocation.CLASS_OUTPUT + "/" + filename))
                .findFirst()
                .orElse(null);
        if (fo == null) {
            return null;
        }
        try {
            return fo.getCharContent(true).toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Utils() {}

}
