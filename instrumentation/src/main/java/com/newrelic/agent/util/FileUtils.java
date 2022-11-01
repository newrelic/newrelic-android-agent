/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.util;

import java.io.File;
import java.util.regex.Pattern;

public class FileUtils {

    public static boolean isSupportJar(File archiveFile) {
        //
        // Don't instrument anything from system (support) libraries
        //
        boolean matches = false;
        try {
            String canonicalPath = archiveFile.getCanonicalPath().toLowerCase();
            matches |= (Pattern.matches("^.*\\/jre\\/lib\\/rt\\.jar$", canonicalPath)); // java runtime
        } catch (Exception e) {
        }

        return matches;
    }

    public static boolean isArchive(String fileName) {
        String lowerPath = fileName.toLowerCase();
        return (lowerPath.endsWith(".zip")) || (lowerPath.endsWith(".jar")) || (lowerPath.endsWith(".aar"));
    }

    public static boolean isArchive(File f) {
        return isArchive(f.getAbsolutePath());
    }

    public static boolean isClass(String filename) {
        return filename.toLowerCase().endsWith(".class") &&
                !isKotlinModuleInfoClass(filename);
    }

    static boolean isKotlinModuleInfoClass(String filename) {
        return (filename.matches(".*module[-_]info.class"));
    }

    public static boolean isKotlinModule(String filename) {
        return filename.toLowerCase().endsWith(".kotlin_module");
    }

    public static boolean isClass(File f) {
        return !f.isDirectory() && isClass(f.getAbsolutePath());
    }

}
