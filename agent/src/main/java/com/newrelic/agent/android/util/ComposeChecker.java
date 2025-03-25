package com.newrelic.agent.android.util;

import android.content.Context;

import java.util.Arrays;
import java.util.List;

public class ComposeChecker {

    public static boolean isComposeUsed(Context context) {
        List<String> composeClasses = Arrays.asList(
                "androidx.compose.ui.platform.ComposeView",
                "androidx.compose.runtime.Composable"
                // Add more Compose classes as needed
        );

        try {
            for (String className : composeClasses) {
                context.getClassLoader().loadClass(className);
            }
            return true; // All classes loaded successfully
        } catch (ClassNotFoundException e) {
            return false; // At least one class was not found
        } catch (Throwable e){
            return false; // Any other error
        }
    }
}