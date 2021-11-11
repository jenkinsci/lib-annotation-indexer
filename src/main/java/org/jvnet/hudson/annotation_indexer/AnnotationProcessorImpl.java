package org.jvnet.hudson.annotation_indexer;

import org.kohsuke.MetaInfServices;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import static javax.tools.StandardLocation.CLASS_OUTPUT;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Creates indices of {@link Indexed} annotations.
 *
 * @author Kohsuke Kawaguchi
 */
@SupportedAnnotationTypes("*")
@SuppressWarnings({"Since15"})
@MetaInfServices(Processor.class)
public class AnnotationProcessorImpl extends AbstractProcessor {
    /**
     * Use of an annotation.
     */
    private final class Use {
        /**
         * FQCN of the annotation.
         */
        final String annotationName;
        /**
         * Strings that designate FQCNs where annotations are used, either on a class or its members.
         */
        final Set<String> classes = new TreeSet<>();
        /**
         * Keeps track of elements that has the annotation.
         */
        final Set<Element> originatingElements = new HashSet<>();

        private Use(String annotationName) {
            this.annotationName = annotationName;
            try {
                classes.addAll(loadExisting());
            } catch (IOException x) {
                processingEnv.getMessager().printMessage(Kind.ERROR, x.toString());
            }
        }

        void add(Element elt) {
            originatingElements.add(elt);

            TypeElement t;
            switch (elt.getKind()) {
            case CLASS:
            case INTERFACE:
            case ANNOTATION_TYPE:
            case ENUM:
                t = (TypeElement) elt;
                break;
            case METHOD:
            case FIELD:
            case CONSTRUCTOR:
                t = (TypeElement) elt.getEnclosingElement();
                break;
            case PACKAGE:
                classes.add(((PackageElement)elt).getQualifiedName().toString()+".*");
                return;

            default:
//                throw new AssertionError(elt.getKind());
                return;
            }
            classes.add(getElementUtils().getBinaryName(t).toString());
        }

        String getIndexFileName() {
            return "META-INF/services/annotations/" + annotationName;
        }

        /**
         * Loads existing index, if it exists.
         */
        List<String> loadExisting() throws IOException {
            List<String> elements = new ArrayList<>();
            try {
                FileObject in = processingEnv.getFiler().getResource(CLASS_OUTPUT, "", getIndexFileName());
                // Read existing annotations, for incremental compilation.
                BufferedReader is = new BufferedReader(new InputStreamReader(in.openInputStream(), StandardCharsets.UTF_8));
                try {
                    String line;
                    while ((line=is.readLine())!=null)
                        elements.add(line);
                } finally {
                    is.close();
                }
            } catch (FileNotFoundException | NoSuchFileException x) {
                // OK, created for the first time
            }
            return elements;
        }

        void write() {
            try {
                FileObject out = processingEnv.getFiler().createResource(CLASS_OUTPUT,
                        "", getIndexFileName(),
                        originatingElements.toArray(new Element[originatingElements.size()]));

                PrintWriter w = new PrintWriter(new OutputStreamWriter(out.openOutputStream(), StandardCharsets.UTF_8));
                try {
                    for (String el : classes)
                        w.println(el);
                } finally {
                    w.close();
                }
            } catch (IOException x) {
                processingEnv.getMessager().printMessage(Kind.ERROR, x.toString());
            }
        }
    }

    protected Elements getElementUtils() {
        return processingEnv.getElementUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver() || roundEnv.errorRaised())
            // TODO we should not write until processingOver
            return false;

        execute(annotations, roundEnv);
        return false;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    protected void execute(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // map from indexable annotation names, to actual uses
        Map<String,Use> output = new HashMap<>();
        scan(annotations, roundEnv, output);
        for (Use u : output.values())
            u.write();
    }

    protected AnnotationMirror findAnnotationOn(Element e, String name) {
        for (AnnotationMirror a : getElementUtils().getAllAnnotationMirrors(e))
            if (getElementUtils().getBinaryName((TypeElement) a.getAnnotationType().asElement()).contentEquals(name))
                return a;
        return null;
    }

    private void scan(Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv, Map<String,Use> output) {
        for (TypeElement ann : annotations) {
            if (!isIndexing(ann))
                continue;   // not indexed


            AnnotationMirror retention = findAnnotationOn(ann, Retention.class.getName());
            if (retention == null) {
                processingEnv.getMessager().printMessage(Kind.WARNING, "Specify @Retention(RUNTIME)", ann);
            } else {
                // XXX check that it is RUNTIME?
            }

            String annName = getElementUtils().getBinaryName(ann).toString();
            Use o = output.get(annName);
            if (o==null)
                output.put(annName,o=new Use(annName));

            for (Element elt : roundEnv.getElementsAnnotatedWith(ann)) {
                AnnotationMirror marked = findAnnotationOn(elt,annName);
                assert marked != null;

                // TODO: validator support

                o.add(elt);
            }
        }
    }

    /**
     * Given a {@link TypeElement} that represents the annotation class,
     * determines whether to index this annotation.
     */
    protected boolean isIndexing(TypeElement ann) {
        AnnotationMirror indexed = findAnnotationOn(ann,Indexed.class.getName());
        return indexed!=null;
    }


}
