package com.newrelic.agent.android.util;

import android.content.Context;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ComposeChecker {

    public static boolean isComposeUsed(Context context) {

        try {
            Class.forName("androidx.compose.ui.platform.ComposeView");
            return true; // All classes loaded successfully
        } catch (ClassNotFoundException e) {
            return false; // At least one class was not found
        } catch (Throwable e) {
            return false; // Any other error
        }
    }
}