package com.newrelic.agent.android.sessionReplay.internal;

import android.annotation.SuppressLint;
import android.os.Build;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Enables replacing WindowManagerGlobal.mViews with a custom ArrayList implementation.
 *
 * Inspired from https://github.com/android/android-test/blob/master/espresso/core/java/androidx/test/espresso/base/RootsOracle.java
 */
public class WindowManagerSpy {

    private static final String TAG = "WindowManagerSpy";

    private static Class<?> windowManagerClass;
    private static Object windowManagerInstance;
    private static Field mViewsField;

    static {
        String className = "android.view.WindowManagerGlobal";
        try {
            windowManagerClass = Class.forName(className);
        } catch (Throwable ignored) {
            Log.w(TAG, ignored);
            windowManagerClass = null;
        }

        if (windowManagerClass != null) {
            String methodName = "getInstance";
            try {
                Method method = windowManagerClass.getMethod(methodName);
                windowManagerInstance = method.invoke(null);
            } catch (Throwable ignored) {
                Log.w(TAG, ignored);
                windowManagerInstance = null;
            }

            try {
                mViewsField = windowManagerClass.getDeclaredField("mViews");
                mViewsField.setAccessible(true);
            } catch (NoSuchFieldException ignored) {
                Log.w(TAG, ignored);
                mViewsField = null;
            }
        }
    }

    @SuppressLint({"PrivateApi", "ObsoleteSdkInt", "DiscouragedPrivateApi"})
    public static void swapWindowManagerGlobalMViews(SwapFunction swap) {
        if (Build.VERSION.SDK_INT < 19) {
            return;
        }
        try {
            if (windowManagerInstance != null && mViewsField != null) {
                @SuppressWarnings("unchecked")
                ArrayList<View> mViews = (ArrayList<View>) mViewsField.get(windowManagerInstance);
                mViewsField.set(windowManagerInstance, swap.swap(mViews));
            }
        } catch (Throwable ignored) {
            Log.w(TAG, ignored);
        }
    }

    public static View[] windowManagerMViewsArray() {
        if (Build.VERSION.SDK_INT >= 19) {
            return new View[0];
        }
        try {
            if (windowManagerInstance != null && mViewsField != null) {
                return (View[]) mViewsField.get(windowManagerInstance);
            }
        } catch (Throwable ignored) {
            Log.w(TAG, ignored);
        }
        return new View[0];
    }

    public interface SwapFunction {
        ArrayList<View> swap(ArrayList<View> mViews);
    }
}
