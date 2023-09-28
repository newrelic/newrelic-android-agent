/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.androidx.navigation;

import android.os.Bundle;

import androidx.compose.runtime.Composer;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.NavController;
import androidx.navigation.NavHostController;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigator;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.instrumentation.InstrumentationDelegate;
import com.newrelic.agent.android.instrumentation.ReplaceCallSite;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Navigation classes are final so can't use WrapReturn
 * Use ReplaceCallSite to inject delegate calls
 */

public class NavigationController extends InstrumentationDelegate {

    private static Set<FeatureFlag> requiredFeatures = new HashSet<FeatureFlag>() {{
        add(FeatureFlag.Jetpack);
    }};

    @ReplaceCallSite(isStatic = true)
    static public void navigate$default(NavController navController, String route, NavOptions options, Navigator.Extras extras, int i, Object o) {
        navController.navigate(route, options, extras);

        submit(requiredFeatures, () -> {
            log.debug("navigate$default(NavController, String, NavOptions, Navigator.Extras, int, Object)");
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
            analyticsController.recordBreadcrumb("Compose", attrs);
        });
    }

    @ReplaceCallSite(isStatic = true)
    static public void invoke(NavHostController navHostController, NavBackStackEntry navBackStackEntry, Composer composer, int cnt) {
        navHostController.navigate(navBackStackEntry.getDestination().getId(), navBackStackEntry.getArguments());

        executor.submit(() -> {
            log.debug("invoke(NavController, NavBackStackEntry, Composer, int)");
            Map<String, Object> attrs = new HashMap<String, Object>() {{
                put("span", "invoke");
                put("navBackStackEntry.id", navBackStackEntry.getDestination().getId());
                if (navBackStackEntry.getArguments() != null) {
                    put("navBackStackEntry.arguments", navBackStackEntry.getArguments().toString());
                }
                put("composer.rememberedValue", composer.rememberedValue());
            }};
            analyticsController.recordBreadcrumb("Compose", attrs);
        });
    }

    @ReplaceCallSite
    static public void navigate(NavController navController, int resId, Bundle bundle, NavOptions options, Navigator.Extras extras) {
        navController.navigate(resId, bundle, options, extras);

        submit(requiredFeatures, () -> {
            log.debug("navigate(NavController, int, Bundle, NavOptions, Navigator.Extras)");
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
            analyticsController.recordBreadcrumb("Compose", attrs);
        });
    }

    @ReplaceCallSite
    static public boolean navigateUp(NavController navController) {
        boolean rc = navController.navigateUp();

        submit(requiredFeatures, () -> {
            log.debug("navigateUp(NavController)");
            Map<String, Object> attrs = new HashMap<String, Object>() {{
                put("span", "navigateUp");
                put("result", rc);
            }};
            analyticsController.recordBreadcrumb("Compose", attrs);
        });

        return rc;
    }

    @ReplaceCallSite(isStatic = true)
    static public void popBackStack$default(NavController navController, String route, boolean inclusive, boolean saveState, int i, Object unused) {
        navController.popBackStack(route, inclusive, saveState);

        submit(requiredFeatures, () -> {
            log.debug("popBackStack$default(NavController, String, boolean, boolean, int, Object)");
            Map<String, Object> attrs = new HashMap<String, Object>() {{
                put("span", "popBackStack");
                put("route", route);
                put("inclusive", inclusive);
                put("saveState", saveState);
            }};
            analyticsController.recordBreadcrumb("Compose", attrs);
        });

    }

    @ReplaceCallSite
    static public boolean popBackStack(NavController navController, int destinationId, boolean inclusive, boolean saveState) {
        boolean rc = navController.popBackStack(destinationId, inclusive, saveState);

        submit(requiredFeatures, () -> {
            log.debug("popBackStack(NavController, int, boolean, boolean)");
            Map<String, Object> attrs = new HashMap<String, Object>() {{
                put("span", "popBackStack");
                put("destinationId", destinationId);
                put("inclusive", inclusive);
                put("saveState", saveState);
                put("result", rc);
            }};
            analyticsController.recordBreadcrumb("Compose", attrs);
        });

        return rc;
    }

    @ReplaceCallSite
    static public boolean popBackStack(NavController navController, String route, boolean inclusive, boolean saveState) {
        boolean rc = navController.popBackStack(route, inclusive, saveState);

        submit(requiredFeatures, () -> {
            log.debug("popBackStack(NavController, String, boolean, boolean) ");
            Map<String, Object> attrs = new HashMap<String, Object>() {{
                put("span", "popBackStack");
                put("route", route);
                put("inclusive", inclusive);
                put("saveState", saveState);
                put("result", rc);
            }};
            analyticsController.recordBreadcrumb("Compose", attrs);
        });

        return rc;
    }

    @ReplaceCallSite
    static public boolean popBackStack(NavHostController navHostController) {
        boolean rc = navHostController.popBackStack();

        submit(requiredFeatures, () -> {
            log.debug("boolean popBackStack(NavHostController)");
            Map<String, Object> attrs = new HashMap<String, Object>() {{
                put("span", "popBackStack");
                put("result", rc);
            }};
            analyticsController.recordBreadcrumb("Compose", attrs);
        });

        return rc;
    }

}
