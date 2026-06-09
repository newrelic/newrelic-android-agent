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
        // Check for coroutines (most KMP apps use this for async operations)
        boolean hasCoroutines = checkForClass("kotlinx.coroutines.Job");

        // Check for serialization (many KMP apps use this for JSON parsing)
        boolean hasSerialization = checkForClass("kotlinx.serialization.KSerializer");

        // Check for Ktor client (popular KMP networking library)
        boolean hasKtor = checkForClass("io.ktor.client.HttpClient");

        return hasCoroutines || hasSerialization || hasKtor;
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
