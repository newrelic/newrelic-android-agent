package com.newrelic.agent.android.sessionReplay.internal;

import android.annotation.SuppressLint;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.Window;

import java.lang.reflect.Field;

/**
 * Utilities to extract a {@link Window} out of a {@link View} if that view is a DecorView.
 */
@SuppressLint("PrivateApi")
public class WindowSpy {

    private static final String TAG = "WindowSpy";

    private static Class<?> decorViewClass;
    private static Field windowField;

    static {
        int sdkInt = Build.VERSION.SDK_INT;
        String decorViewClassName;
        if (sdkInt >= 24) {
            decorViewClassName = "com.android.internal.policy.DecorView";
        } else if (sdkInt == 23) {
            decorViewClassName = "com.android.internal.policy.PhoneWindow$DecorView";
        } else {
            decorViewClassName = "com.android.internal.policy.impl.PhoneWindow$DecorView";
        }
        try {
            decorViewClass = Class.forName(decorViewClassName);
        } catch (Throwable ignored) {
            Log.d(TAG, "Unexpected exception loading " + decorViewClassName + " on API " + sdkInt, ignored);
            decorViewClass = null;
        }

        if (decorViewClass != null) {
            String fieldName = sdkInt >= 24 ? "mWindow" : "this$0";
            try {
                windowField = decorViewClass.getDeclaredField(fieldName);
                windowField.setAccessible(true);
            } catch (NoSuchFieldException ignored) {
                Log.d(TAG, "Unexpected exception retrieving " + decorViewClass.getName() + "#" + fieldName + " on API " + sdkInt, ignored);
                windowField = null;
            }
        }
    }

    public static boolean attachedToPhoneWindow(View maybeDecorView) {
        return decorViewClass != null && decorViewClass.isInstance(maybeDecorView);
    }

    public static Window pullWindow(View maybeDecorView) {
        if (decorViewClass != null && decorViewClass.isInstance(maybeDecorView)) {
            try {
                return (Window) windowField.get(maybeDecorView);
            } catch (IllegalAccessException ignored) {
                Log.d(TAG, "Unexpected exception accessing window field", ignored);
            }
        }
        return null;
    }
}