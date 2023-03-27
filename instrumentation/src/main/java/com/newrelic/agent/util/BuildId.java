/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.util;

import com.google.common.base.Strings;
import com.newrelic.agent.InstrumentationAgent;

import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class BuildId {
    public static final String BUILD_ID_KEY = "NewRelic.buildId";
    public static final String DEFAULT_VARIANT = "release";

    private static AtomicReference<Map<String, String>> variantBuildIds = new AtomicReference<>(null);
    private static Logger log = InstrumentationAgent.LOGGER;
    private static boolean variantMapsEnabled = true;

    static {
        invalidate();
    }

    static String autoBuildId() {
        return UUID.randomUUID().toString();
    }

    /*
     * The build ID needs to be reset each time the plugin starts, but the jvm may hold the static
     * value across invocations. Allow the plugin to reset the value, so the next request of
     * getBuildId(<variant>) will generate a new id.
     */
    public static void invalidate() {
        System.clearProperty(BUILD_ID_KEY);

        variantBuildIds.set(null);
        getDefaultBuildId();
    }

    public static String getDefaultBuildId() {

        variantBuildIds.compareAndSet(null, new HashMap<String, String>());

        // It is reset when the plugin initializes
        String buildId = variantBuildIds.get().get(DEFAULT_VARIANT);
        if (Strings.isNullOrEmpty(buildId)) {
            // cache the value for later use, such as uploading maps after Dexguard
            buildId = autoBuildId();
            variantBuildIds.get().put(DEFAULT_VARIANT, buildId);
        }

        buildId = variantBuildIds.get().get(DEFAULT_VARIANT);
        if (Strings.isNullOrEmpty(buildId)) {
            log.error("NewRelic has detected an invalid build ID. Please clean and rebuild the project.");
        }

        return buildId;
    }

    /*
     * Returns per-variant buildId.
     *
     * @param variantName If variant is 'default', return the global buildId
     *
     * @return Variant's buildId
     */
    public static String getBuildId(String variantName) {
        if (!variantMapsEnabled || Strings.isNullOrEmpty(variantName)) {
            return getDefaultBuildId();
        }

        variantName = variantName.toLowerCase();
        String buildId = variantBuildIds.get().get(variantName);

        if (Strings.isNullOrEmpty(buildId)) {
            buildId = autoBuildId();
            variantBuildIds.get().put(variantName, buildId);
        }

        return variantBuildIds.get().get(variantName);
    }

    /*
     * Returns all variant buildId as a map
     *
     * @return All collected variant buildIds
     */
    public static Map<String, String> getVariantBuildIds() {
        return variantBuildIds.get();
    }

    /*
     * Sets a global override on per-variant build Ids
     */
    public static void setVariantMapsEnabled(boolean variantMapsEnabled) {
        BuildId.variantMapsEnabled = variantMapsEnabled;
        log.debug("Variant buildIds have been " + (BuildId.variantMapsEnabled ? "enabled" : "disabled"));
    }

}
