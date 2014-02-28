package org.jvnet.hudson.annotation_indexer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Kohsuke Kawaguchi
 */
public class Index {
    /**
     * Lists up all the elements annotated by the given annotation and of the given {@link AnnotatedElement} subtype.
     */
    public static <T extends AnnotatedElement> Iterable<T> list(Class<? extends Annotation> type, ClassLoader cl, final Class<T> subType) throws IOException {
        final Iterable<AnnotatedElement> base = list(type,cl);
        return new Iterable<T>() {
            public Iterator<T> iterator() {
                return new SubtypeIterator<AnnotatedElement,T>(base.iterator(), subType);
            }
        };
    }

    /**
     * Lists up all the elements annotated by the given annotation.
     */
    public static Iterable<AnnotatedElement> list(final Class<? extends Annotation> type, final ClassLoader cl) throws IOException {
// To allow annotations defined by 3rd parties to be indexable, skip this check
//        if (!type.isAnnotationPresent(Indexed.class))
//            throw new IllegalArgumentException(type+" doesn't have @Indexed");

        final Set<String> ids = new TreeSet<String>();

        final Enumeration<URL> res = cl.getResources("META-INF/annotations/"+type.getName());
        while (res.hasMoreElements()) {
            URL url = res.nextElement();
            InputStream is = url.openStream();
            try {
                BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                String line;
                while ((line = r.readLine()) != null) {
                    ids.add(line);
                }
            } finally {
                is.close();
            }
        }

        return new Iterable<AnnotatedElement>() {
            public Iterator<AnnotatedElement> iterator() {
                return new Iterator<AnnotatedElement>() {
                    /**
                     * Next element to return.
                     */
                    private AnnotatedElement next;

                    private final Iterator<String> iditr = ids.iterator();

                    private final List<AnnotatedElement> lookaheads = new LinkedList<AnnotatedElement>();

                    public boolean hasNext() {
                        fetch();
                        return next!=null;
                    }

                    public AnnotatedElement next() {
                        fetch();
                        AnnotatedElement r = next;
                        next = null;
                        return r;
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }

                    private void fetch() {
                        while (next==null) {
                            if (!lookaheads.isEmpty()) {
                                next = lookaheads.remove(0);
                                return;
                            }

                            if (!iditr.hasNext())   return;
                            String name = iditr.next();

                            try {
                                Class<?> c = cl.loadClass(name);

                                if (c.isAnnotationPresent(type))
                                    lookaheads.add(c);
                                listAnnotatedElements(c.getDeclaredMethods());
                                listAnnotatedElements(c.getDeclaredFields());
                                listAnnotatedElements(c.getDeclaredConstructors());
                            } catch (ClassNotFoundException e) {
                                LOGGER.log(Level.FINE, "Failed to load: "+name,e);
                            } catch (LinkageError x) {
                                LOGGER.log(Level.WARNING, "Failed to load " + name, x);
                            }
                        }
                    }

                    private void listAnnotatedElements(AnnotatedElement[] elements) {
                        for (AnnotatedElement m : elements) {
                            // this means we don't correctly handle
                            if (m.isAnnotationPresent(type))
                                lookaheads.add(m);
                        }
                    }
                };
            }
        };
    }

    private Index() {}

    private static final Logger LOGGER = Logger.getLogger(Index.class.getName());

}
