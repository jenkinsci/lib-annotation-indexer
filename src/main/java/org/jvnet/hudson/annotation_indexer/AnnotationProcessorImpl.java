/*
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at http://www.sun.com/cddl/cddl.html
 * or http://www.netbeans.org/cddl.txt.
 *
 * When distributing Covered Code, include this CDDL Header Notice in each file
 * and include the License file at http://www.netbeans.org/cddl.txt.
 * If applicable, add the following below the CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * The Original Software is SezPoz. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 2008 Sun
 * Microsystems, Inc. All Rights Reserved.
 */
package org.jvnet.hudson.annotation_indexer;

import org.kohsuke.MetaInfServices;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import static javax.lang.model.SourceVersion.RELEASE_6;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
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
import java.lang.annotation.RetentionPolicy;
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
@SupportedSourceVersion(RELEASE_6)
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
         * Strings that designate where they are used
         */
        final Set<String> usages = new TreeSet<String>();
        /**
         * Keeps track of elements that has the annotation.
         */
        final Set<Element> originatingElements = new HashSet<Element>();

        private Use(String annotationName) {
            this.annotationName = annotationName;
        }

        void add(Element elt) {
            originatingElements.add(elt);

            switch (elt.getKind()) {
            case CLASS:
                usages.add(processingEnv.getElementUtils().getBinaryName((TypeElement) elt).toString());
                return;
            case METHOD:
            case FIELD:
                String className = processingEnv.getElementUtils().getBinaryName((TypeElement) elt.getEnclosingElement()).toString();
                String memberName = elt.getSimpleName().toString();
                usages.add(className+'#'+memberName+(elt.getKind()== ElementKind.METHOD ? "()" : ""));
                return;
            default:
                throw new AssertionError(elt.getKind());
            }
        }

        String getIndexFileName() {
            return "META-INF/annotations/" + annotationName;
        }

        /**
         * Loads existing index, if it exists.
         */
        List<String> loadExisting() throws IOException {
            List<String> elements = new ArrayList<String>();
            try {
                FileObject in = processingEnv.getFiler().getResource(CLASS_OUTPUT, "", getIndexFileName());
                // Read existing annotations, for incremental compilation.
                BufferedReader is = new BufferedReader(new InputStreamReader(in.openInputStream(),"UTF-8"));
                try {
                    String line;
                    while ((line=is.readLine())!=null)
                        elements.add(line);
                } finally {
                    is.close();
                }
            } catch (FileNotFoundException x) {
                // OK, created for the first time
            }
            return elements;
        }

        void write() {
            try {
                FileObject out = processingEnv.getFiler().createResource(CLASS_OUTPUT,
                        "", getIndexFileName(),
                        originatingElements.toArray(new Element[originatingElements.size()]));

                PrintWriter w = new PrintWriter(new OutputStreamWriter(out.openOutputStream(),"UTF-8"));
                try {
                    for (String el : usages)
                        w.println(el);
                } finally {
                    w.close();
                }
            } catch (IOException x) {
                processingEnv.getMessager().printMessage(Kind.ERROR, x.toString());
            }
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }
        for (Element indexable : roundEnv.getElementsAnnotatedWith(Indexed.class)) {
            Retention retention = indexable.getAnnotation(Retention.class);
            if (retention == null || retention.value() != RetentionPolicy.SOURCE) {
                processingEnv.getMessager().printMessage(Kind.WARNING, "should be marked @Retention(RetentionPolicy.SOURCE)", indexable);
            }
        }
        // map from indexable annotation names, to actual uses
        Map<String,Use> output = new HashMap<String,Use>();
        scan(annotations, roundEnv, output);
        for (Use u : output.values())
            u.write();
        return false;
    }

    private AnnotationMirror findAnnotationOn(Element e, String name) {
        for (AnnotationMirror a : processingEnv.getElementUtils().getAllAnnotationMirrors(e))
            if (processingEnv.getElementUtils().getBinaryName((TypeElement) a.getAnnotationType().asElement()).contentEquals(name))
                return a;
        return null;
    }

    private void scan(Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv, Map<String,Use> output) {
        for (TypeElement ann : annotations) {
            AnnotationMirror indexed = findAnnotationOn(ann,Indexed.class.getName());
            if (indexed == null)
                continue;   // not indexed

            String annName = processingEnv.getElementUtils().getBinaryName(ann).toString();
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


}
