/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.androidx.navigation;

import android.os.Bundle;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.compose.runtime.Composer;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.NavController;
import androidx.navigation.NavHostController;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigator;

import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.instrumentation.InstrumentationDelegate;
import com.newrelic.agent.android.instrumentation.ReplaceCallSite;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Navigation classes are final so can't use WrapReturn
 * Use ReplaceCallSite to inject delegate calls
 */

public class NavigationController extends InstrumentationDelegate {
    private static final AtomicReference<NavigationController> instance = new AtomicReference<NavigationController>(null);

    public static NavigationController getInstance() {
        NavigationController.instance.compareAndSet(null, new NavigationController());
        return NavigationController.instance.get();
    }

    @ReplaceCallSite(isStatic = true) // , scope = "androidx.navigation.NavController")
    static public void navigate$default(@NonNull NavController navController, @NonNull String route, NavOptions options, Navigator.Extras extras, int i, Object o) {
        log.debug("navigate$default(NavController navController, String route, NavOptions options, Navigator.Extras extras, int i, Object o)");
        executor.submit(() -> {
            Map<String, Object> attrs = new HashMap<String, Object>() {{
                put("span", "navigate");
                put("route", route);
                if (options != null) {
                    put("restoreState", options.shouldRestoreState());
                    put("popUpToInclusive", options.isPopUpToInclusive());
                    put("popUpToSaveState", options.shouldPopUpToSaveState());
                    if (options.getPopUpToRoute() != null) {
                        put("options.popUpToRoute", options.getPopUpToRoute());
                    }
                    if (-1 != options.getEnterAnim()) {
                        put("options.enterAnim", options.getEnterAnim());
                    }
                    if (-1 != options.getExitAnim()) {
                        put("options.exitAnim", options.getExitAnim());
                    }
                    if (-1 != options.getPopEnterAnim()) {
                        put("options.popEnterAnim", options.getPopEnterAnim());
                    }
                    if (-1 != options.getPopExitAnim()) {
                        put("options.popExitAnim", options.getPopExitAnim());
                    }
                }
                if (extras != null) {
                    put("extras", extras);
                }
            }};
            AnalyticsControllerImpl.getInstance().recordBreadcrumb("Compose", attrs);
        });
        navController.navigate(route, options, extras);
    }

    @ReplaceCallSite(isStatic = true)    // (scope = "androidx.navigation.compose.NavHostControllerKt")
    static public void invoke(NavHostController navHostController, NavBackStackEntry navBackStackEntry, Composer composer, int cnt) {
        navHostController.popBackStack();
    }

    @ReplaceCallSite  // (scope = "androidx.navigation.NavController")
    static public void navigate(NavController navController, int resId, Bundle bundle, NavOptions options, Navigator.Extras extras) {
        log.debug("navigate(NavController navController, int resId, Bundle bundle, NavOptions options, Navigator.Extras extras)");
        navController.navigate(resId, bundle, options, extras);
        executor.submit(() -> {
            Map<String, Object> attrs = new HashMap<String, Object>() {{
                put("span", "navigate");
                put("resId", resId);
                if (options.getPopUpToRoute() != null) {
                    put("options.popUpToRoute", options.getPopUpToRoute());
                }
                if (-1 != options.getEnterAnim()) {
                    put("options.enterAnim", options.getEnterAnim());
                }
                if (-1 != options.getExitAnim()) {
                    put("options.exitAnim", options.getExitAnim());
                }
                if (-1 != options.getPopEnterAnim()) {
                    put("options.popEnterAnim", options.getPopEnterAnim());
                }
                if (-1 != options.getPopExitAnim()) {
                    put("options.popExitAnim", options.getPopExitAnim());
                }
                put("extras", extras == null ? "null" : extras.toString());
            }};
            AnalyticsControllerImpl.getInstance().recordBreadcrumb("Compose", attrs);
        });
    }

    @ReplaceCallSite  //   // (scope = "androidx.navigation.NavController")
    static public boolean navigateUp(NavController navController) {
        log.debug("navigateUp(NavController navController)");
        boolean rc = navController.navigateUp();
        executor.submit(() -> {
            Map<String, Object> attrs = new HashMap<String, Object>() {{
                put("span", "navigateUp");
            }};
            AnalyticsControllerImpl.getInstance().recordBreadcrumb("Compose", attrs);
        });

        return rc;
    }

    @ReplaceCallSite(isStatic = true) // , scope = "androidx.navigation.NavController")
    static public void popBackStack$default(@NonNull NavController navController, @NonNull String route, boolean inclusive, boolean saveState, int i, Object unused) {
        executor.submit(() -> {
            Map<String, Object> attrs = new HashMap<String, Object>() {{
                put("span", "popBackStack");
                put("inclusive", inclusive);
                put("saveState", saveState);
            }};
            AnalyticsControllerImpl.getInstance().recordBreadcrumb("Compose", attrs);
        });
        navController.popBackStack(route, inclusive, saveState);
    }

    @ReplaceCallSite  // (scope = "androidx.navigation.NavController")
    static public boolean popBackStack(NavController navController, @IdRes int destinationId, boolean inclusive, boolean saveState) {
        log.debug("popBackStack(NavController navController, @IdRes int destinationId, boolean inclusive, boolean saveState)");
        boolean rc = navController.popBackStack(destinationId, inclusive, saveState);
        executor.submit(() -> {
            Map<String, Object> attrs = new HashMap<String, Object>() {{
                put("span", "popBackStack");
                put("destinationId", destinationId);
                put("inclusive", inclusive);
                put("saveState", saveState);
                put("result", rc);
            }};
            AnalyticsControllerImpl.getInstance().recordBreadcrumb("Compose", attrs);
        });

        return rc;
    }

    @ReplaceCallSite  // (scope = "androidx.navigation.NavController")
    static public boolean popBackStack(NavController navController, @NonNull String route, boolean inclusive, boolean saveState) {
        log.debug("popBackStack(NavController navController, @NonNull String route, boolean inclusive, boolean saveState) ");
        boolean rc = navController.popBackStack(route, inclusive, saveState);
        executor.submit(() -> {
            Map<String, Object> attrs = new HashMap<String, Object>() {{
                put("span", "popBackStack");
                put("route", route);
                put("inclusive", inclusive);
                put("saveState", saveState);
                put("result", rc);
            }};
            AnalyticsControllerImpl.getInstance().recordBreadcrumb("Compose", attrs);
        });

        return rc;
    }

    @ReplaceCallSite  // (scope = "androidx.navigation.NavController")
    static public boolean popBackStack(NavHostController navHostController) {
        log.debug("boolean popBackStack(NavHostController navHostController)");
        boolean rc = navHostController.popBackStack();
        executor.submit(() -> {
            Map<String, Object> attrs = new HashMap<String, Object>() {{
                put("span", "popBackStack");
                put("result", rc);
            }};
            AnalyticsControllerImpl.getInstance().recordBreadcrumb("Compose", attrs);
        });

        return rc;
    }

}
