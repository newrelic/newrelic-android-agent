package com.newrelic.agent.util;

import com.google.common.base.Strings;
import com.newrelic.agent.compile.Log;
import com.newrelic.agent.compile.RewriterAgent;
import com.newrelic.agent.compile.SystemErrLog;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class BuildId {
    public static final String BUILD_ID_KEY = "NewRelic.buildId";
    public static final String NR_AGENT_ARGS_KEY = "NewRelic.agentArgs";
    public static final String DEFAULT_VARIANT = "release";

    private static AtomicReference<Map<String, String>> variantBuildIds = new AtomicReference<>();
    private static Log log;
    private static boolean variantMapsEnabled = true;

    static {
        final String agentArgStr = System.getProperty(NR_AGENT_ARGS_KEY);
        Map<String, String> agentArgs = RewriterAgent.parseAgentArgs(agentArgStr);
        log = new SystemErrLog(agentArgs);
        invalidate();
    }

    static String autoBuildId() {
        return UUID.randomUUID().toString();
    }

    /**
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
            String buildIdProp = System.getProperty(BUILD_ID_KEY);
            // generate a new build ID when the property is null|empty.
            if (Strings.isNullOrEmpty(buildIdProp)) {
                buildId = autoBuildId();
                variantBuildIds.get().put(DEFAULT_VARIANT, buildId);
                // cache the value for later use, such as uploading maps after Dexguard
                System.setProperty(BUILD_ID_KEY, buildId);
            }
        }

        buildId = variantBuildIds.get().get(DEFAULT_VARIANT);
        if (Strings.isNullOrEmpty(buildId)) {
            log.error("NewRelic has detected an invalid build ID. Please clean and rebuild the project.");
        }

        return buildId;
    }

    /**
     * Returns per-variant buildId.
     *
     * @param variantName If variant is 'default', return the global buildId
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
            log.debug("buildId[" + variantName + "] = [" + buildId + "]");
        }

        return variantBuildIds.get().get(variantName);
    }

    /**
     * Returns all variant buildId as a map
     *
     * @return All collected variant buildIds
     */
    public static Map<String, String> getVariantBuildIds() {
        return variantBuildIds.get();
    }

    /**
     * Sets a global override on per-variant build Ids
     *
     * @param variantMapsEnabled
     */
    public static void setVariantMapsEnabled(boolean variantMapsEnabled) {
        BuildId.variantMapsEnabled = variantMapsEnabled;
        log.info("[newrelic.info] Variant buildIds have been " + (BuildId.variantMapsEnabled ? "enabled" : "disabled"));
    }

}
