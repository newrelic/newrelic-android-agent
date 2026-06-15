package com.newrelic.agent.android.util;

public class KmpChecker {

    public static boolean isKmpUsed() {
        // Check for Kotlin runtime
        if (!isKotlinPresent()) {
            return false;
        }

        // Check for common KMP libraries
        return hasKmpLibraries();
    }

    private static boolean isKotlinPresent() {
        try {
            Class.forName("kotlin.Unit");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Throwable e) {
            return false;
        }
    }

    private static boolean hasKmpLibraries() {
        // Check for Kotlin/Native concurrent primitives (only present in KMP projects with iOS/Native targets)
        boolean hasNativeConcurrent = checkForClass("kotlin.native.concurrent.SharedImmutable") ||
                                      checkForClass("kotlin.native.concurrent.FreezingException") ||
                                      checkForClass("kotlin.native.concurrent.AtomicReference");

        // Check for atomicfu (heavily used in KMP for shared state across platforms)
        boolean hasAtomicFu = checkForClass("kotlinx.atomicfu.AtomicInt") ||
                              checkForClass("kotlinx.atomicfu.AtomicRef");

        // Check for Kotlin/JS indicators (present in KMP projects targeting JS)
        boolean hasKotlinJs = checkForClass("kotlin.js.JsName") ||
                              checkForClass("kotlin.js.JsExport");

        // Check for common.stdlib markers (multiplatform standard library)
        boolean hasCommonStdlib = checkForClass("kotlin.native.concurrent.Worker");

        return hasNativeConcurrent || hasAtomicFu || hasKotlinJs || hasCommonStdlib;
    }

    private static boolean checkForClass(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Throwable e) {
            return false;
        }
    }
}
