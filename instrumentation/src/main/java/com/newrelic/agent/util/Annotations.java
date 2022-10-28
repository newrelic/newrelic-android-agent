/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.util;

import com.newrelic.agent.Constants;
import com.newrelic.agent.compile.visitor.ClassAnnotationVisitor;
import com.newrelic.agent.compile.visitor.MethodAnnotationVisitor;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

public class Annotations {

    public static Collection<ClassAnnotation> getClassAnnotations(@SuppressWarnings("rawtypes") Class annotationClass, String packageSearchPath, Set<URL> classpathURLs) {
        String annotationDescription = 'L' + annotationClass.getName().replace('.', '/') + ';';
        System.out.println("getClassAnnotations: annotationClass[" + annotationClass.getSimpleName() + "] packageSearchPath[" + packageSearchPath + "]  classpathURLs[" + classpathURLs + "]");

        Map<String, URL> fileNames = getMatchingFiles(packageSearchPath, classpathURLs);

        Collection<ClassAnnotation> list = new ArrayList<>();
        for (Entry<String, URL> entry : fileNames.entrySet()) {
            String fileName = entry.getKey();
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                Streams.copy(Annotations.class.getResourceAsStream('/' + fileName), out, true);
                ClassReader cr = new ClassReader(out.toByteArray());
                Collection<ClassAnnotation> annotations = ClassAnnotationVisitor.getAnnotations(cr, annotationDescription);
                list.addAll(annotations);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return list;
    }

    public static Collection<MethodAnnotation> getMethodAnnotations(@SuppressWarnings("rawtypes") Class annotationClass, String packageSearchPath, Set<URL> classpathURLs) {
        String annotationDescription = Type.getType(annotationClass).getDescriptor();
        System.out.println("getClassAnnotations: annotationClass[" + annotationClass.getSimpleName() + "] packageSearchPath[" + packageSearchPath + "]  classpathURLs[" + classpathURLs + "]");

        Map<String, URL> fileNames = getMatchingFiles(packageSearchPath, classpathURLs);

        Collection<MethodAnnotation> list = new ArrayList<>();
        for (Entry<String, URL> entry : fileNames.entrySet()) {
            String fileName = entry.getKey();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                Streams.copy(Annotations.class.getResourceAsStream('/' + fileName), out, true);
                ClassReader cr = new ClassReader(out.toByteArray());
                Collection<MethodAnnotation> annotations = MethodAnnotationVisitor.getAnnotations(cr, annotationDescription);
                list.addAll(annotations);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }

        return list;
    }

    private static Map<String, URL> getMatchingFiles(String packageSearchPath,
                                                     Set<URL> classpathURLs) {
        if (!packageSearchPath.endsWith("/")) {
            packageSearchPath = packageSearchPath + "/";
        }
        Map<String, URL> fileNames = getMatchingFileNames(Constants.NR_CLASS_PATTERN, classpathURLs);
        for (String file : fileNames.keySet().toArray(new String[0])) {
            if (!file.startsWith(packageSearchPath)) {
                fileNames.remove(file);
            }
        }
        return fileNames;
    }

    static Map<String, URL> getMatchingFileNames(final Pattern pattern, final Collection<URL> urls) {
        Map<String, URL> names = new HashMap<>();

        for (URL url : urls) {
            try {
                url = fixUrl(url);
                File file = new File(URLDecoder.decode(url.getFile(), "UTF-8"));
                if (file.isDirectory()) {
                    List<File> files = PatternFileMatcher.getMatchingFiles(file, pattern);
                    for (File f : files) {
                        String path = f.getAbsolutePath();
                        path = path.substring(file.getAbsolutePath().length() + 1);
                        names.put(path, url);
                    }
                } else if (file.isFile()) {
                    try (JarFile jarFile = new JarFile(file)) {
                        for (Enumeration<JarEntry> entries = jarFile.entries(); entries.hasMoreElements(); ) {
                            JarEntry jarEntry = entries.nextElement();
                            if (pattern.matcher(jarEntry.getName()).matches()) {
                                names.put(jarEntry.getName(), url);
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        System.exit(1);
                    }
                }

            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                System.exit(1);
                /* unreachable, but the compiler complains otherwise ... */
                return names;
            }

        }
        return names;
    }

    /*
     * When we run through Ant we get funny jar:file: urls that we have trouble using.
     */
    private static URL fixUrl(URL url) {
        String protocol = url.getProtocol();

        if ("jar".equals(protocol)) {
            try {
                String urlString = url.toString().substring(4);
                int index = urlString.indexOf("!/");
                if (index > 0) {
                    urlString = urlString.substring(0, index);
                }
                url = new URL(urlString);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return url;
    }

    /*
     * Recursively finds files matching a pattern.
     */
    static class PatternFileMatcher {
        private final FileFilter filter;
        private final List<File> files = new ArrayList<>();

        public static List<File> getMatchingFiles(File directory, Pattern pattern) {
            PatternFileMatcher matcher = new PatternFileMatcher(pattern);
            directory.listFiles(matcher.filter);
            return matcher.files;
        }

        private PatternFileMatcher(final Pattern pattern) {
            super();
            this.filter = new FileFilter() {
                @Override
                public boolean accept(File f) {
                    if (f.isDirectory()) {
                        f.listFiles(this);
                    }
                    boolean match = pattern.matcher(f.getAbsolutePath()).matches();
                    if (match) {
                        files.add(f);
                    }
                    return match;
                }
            };
        }
    }

}
