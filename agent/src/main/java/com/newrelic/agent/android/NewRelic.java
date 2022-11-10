/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import android.content.Context;
import android.text.TextUtils;

import com.newrelic.agent.android.agentdata.AgentDataController;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.EventListener;
import com.newrelic.agent.android.api.common.TransactionData;
import com.newrelic.agent.android.api.v1.Defaults;
import com.newrelic.agent.android.distributedtracing.DistributedTracing;
import com.newrelic.agent.android.distributedtracing.TraceContext;
import com.newrelic.agent.android.distributedtracing.TraceListener;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.AndroidAgentLog;
import com.newrelic.agent.android.logging.NullAgentLog;
import com.newrelic.agent.android.measurement.http.HttpTransactionMeasurement;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.metric.MetricUnit;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.tracing.TraceMachine;
import com.newrelic.agent.android.tracing.TracingInactiveException;
import com.newrelic.agent.android.util.Constants;
import com.newrelic.agent.android.util.NetworkFailure;
import com.newrelic.agent.android.hybrid.StackTrace;
import com.newrelic.agent.android.hybrid.data.DataController;

import org.apache.http.Header;
import org.apache.http.HttpResponse;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;


/**
 * To bootstrap the New Relic Android agent, add the following line to your
 * application's initialization (usually your main activity's onCreate callback):
 *
 * <code>
 * NewRelic.withApplicationToken("{your mobile app token}").start(this.getApplication());
 * </code>
 */
public final class NewRelic {
    private static final String UNKNOWN_HTTP_REQUEST_TYPE = "unknown";

    protected static final AgentLog log = AgentLogManager.getAgentLog();
    protected static final AgentConfiguration agentConfiguration = new AgentConfiguration();
    protected static boolean started = false;

    protected boolean loggingEnabled = true;
    protected int logLevel = AgentLog.INFO;

    protected NewRelic(String token) {
        agentConfiguration.setApplicationToken(token);
    }

    /**
     * Specifies your application token.  This is created through the New Relic application.
     */
    public static NewRelic withApplicationToken(String token) {
        return new NewRelic(token);
    }

    /**
     * Specifies the address of the New Relic data collectors.
     */
    public NewRelic usingCollectorAddress(String address) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "usingCollectorAddress"));

        agentConfiguration.setCollectorHost(address);
        return this;
    }

    public NewRelic usingCrashCollectorAddress(String address) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "usingCrashCollectorAddress"));

        agentConfiguration.setCrashCollectorHost(address);
        return this;
    }

    /**
     * Enables and disables agent logging.  Logging is enabled by default.
     */
    public NewRelic withLoggingEnabled(boolean enabled) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "withLoggingEnabled/" + MetricNames.TAG_STATE)
                .replace(MetricNames.TAG_STATE, Boolean.toString(enabled)));

        this.loggingEnabled = enabled;
        return this;
    }

    /**
     * Specifies the log level.
     *
     * @param level One of:
     *              AgentLog.AUDIT
     *              AgentLog.DEBUG
     *              AgentLog.VERBOSE
     *              AgentLog.INFO
     *              AgentLog.WARNING
     *              AgentLog.ERROR
     */
    public NewRelic withLogLevel(int level) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "withLogLevel/" + MetricNames.TAG_STATE)
                .replace(MetricNames.TAG_STATE, Integer.toString(level)));

        logLevel = level;
        return this;
    }

    /**
     * Enable custom application version strings.
     *
     * @param appVersion The custom application version string to be reported.
     */
    public NewRelic withApplicationVersion(String appVersion) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "withApplicationVersion/" + MetricNames.TAG_STATE)
                .replace(MetricNames.TAG_STATE, appVersion));

        if (appVersion != null) {
            agentConfiguration.setCustomApplicationVersion(appVersion);
        }
        return this;
    }

    /**
     * Enable reporting of a custom application framework.
     *
     * @param applicationFramework The custom application framework to be reported.
     * @param frameworkVersion     The version of the application framework (optional).
     */
    public NewRelic withApplicationFramework(ApplicationFramework applicationFramework, final String frameworkVersion) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "withApplicationFramework")
                .replace(MetricNames.TAG_FRAMEWORK, (applicationFramework != null ? applicationFramework.name() : "<missing>"))
                .replace(MetricNames.TAG_FRAMEWORK_VERSION, (frameworkVersion != null ? frameworkVersion : "")));

        if (applicationFramework != null) {
            agentConfiguration.setApplicationFramework(applicationFramework);
        }

        agentConfiguration.setApplicationFrameworkVersion(frameworkVersion);
        return this;
    }

    /**
     * Enable just-in-time crash reporting.
     * <p>
     * Determines whether the agent will try to upload a crash report before
     * the app terminates. Otherwise, it will be reported on the next app launch.
     * <p>
     * Default is disabled.
     *
     * @param enabled Whether to enable just-in-time crash reporting
     **/
    public NewRelic withCrashReportingEnabled(boolean enabled) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "withCrashReportingEnabled/" + MetricNames.TAG_STATE)
                .replace(MetricNames.TAG_STATE, Boolean.toString(enabled)));

        agentConfiguration.setReportCrashes(enabled);
        return this;
    }

    /**
     * Set app launch time target activity
     */
    public NewRelic withLaunchActivityName(String className) {
        agentConfiguration.setLaunchActivityClassName(className);
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, MetricNames.METRIC_APP_LAUNCH + MetricNames.TAG_STATE)
                .replace(MetricNames.TAG_STATE, className));

        return this;
    }

    /**
     * Enable a NewRelic feature. Supported features are:
     * <p>
     * FeatureFlag.HttpResponseBodyCapture - Capture the HTTP response body for HTTP errors.
     *
     * @param featureFlag The FeatureFlag corresponding to the feature to enable.
     */
    public static void enableFeature(FeatureFlag featureFlag) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "enableFeature/" + MetricNames.TAG_STATE)
                .replace(MetricNames.TAG_STATE, featureFlag.name()));

        log.debug("Enable feature: " + featureFlag.name());
        FeatureFlag.enableFeature(featureFlag);
    }

    /**
     * Disable a NewRelic feature. Supported features are:
     * <p>
     * FeatureFlag.HttpResponseBodyCapture - Capture the HTTP response body for HTTP errors.
     *
     * @param featureFlag The FeatureFlag corresponding to the feature to disable.
     */
    public static void disableFeature(FeatureFlag featureFlag) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "disableFeature/" + MetricNames.TAG_STATE)
                .replace(MetricNames.TAG_STATE, featureFlag.name()));

        log.debug("Disable feature: " + featureFlag.name());
        FeatureFlag.disableFeature(featureFlag);
    }

    /**
     * Enable custom build identifier string.
     *
     * @param buildId The application build identifier string to be reported.
     */
    public NewRelic withApplicationBuild(String buildId) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "withApplicationBuild"));

        if (!TextUtils.isEmpty(buildId)) {
            agentConfiguration.setCustomBuildIdentifier(buildId);
        }
        return this;
    }

    /**
     * Inject a listener to Distributed Trace instrumentation.
     *
     * @param listener Implementation of Distributed Trace listener interface
     */
    public NewRelic withDistributedTraceListener(TraceListener listener) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "withDistributedTraceListener"));

        if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing)) {
            DistributedTracing.setDistributedTraceListener(listener);
        }
        return this;
    }

    /**
     * Override assigned random device ID with specific string
     *
     * @param deviceID String that uniquely identifies this device (40-char max).
     *                 May be empty or null.
     */
    public NewRelic withDeviceID(final String deviceID) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "withDeviceID"));
        agentConfiguration.setDeviceID(deviceID);
        return this;
    }

    /**
     * Starts the agent.  Be sure to call this, otherwise the agent won't do anything!
     *
     * @param context The application context
     */
    public void start(Context context) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "start"));

        if (started) {
            log.debug("NewRelic is already running.");
            return;
        }
        try {
            AgentLogManager.setAgentLog(loggingEnabled ? new AndroidAgentLog() : new NullAgentLog());
            log.setLevel(logLevel);

            boolean instantApp = InstantApps.isInstantApp(context);

            if (instantApp || isInstrumented()) {
                AndroidAgentImpl.init(context, agentConfiguration);
                started = true;

                if (log.getLevel() >= AgentLog.AUDIT) {
                    StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                    // the frame we're looking for is 3 levels up from current
                    if (stack.length > 3) {
                        StackTraceElement elem = stack[3];
                        log.audit("Agent started from " + elem.getClassName() + "." + elem.getMethodName() + ":" + elem.getLineNumber());
                    }
                }

            } else {
                logRecourse();
                return;
            }
        } catch (Throwable e) {
            log.error("Error occurred while starting the New Relic agent!", e);
            logRecourse();
        }
    }

    private void logRecourse() {
        log.error("Failed to detect New Relic instrumentation. " +
                "The current runtime variant may be excluded from instrumentation, " +
                "or instrumentation failed during your build process. " +
                "Please visit http://support.newrelic.com.");
    }

    /**
     * Check if the agent is currently running.
     *
     * @return true if the agent is running.
     */
    public static boolean isStarted() {
        return started;
    }

    /*
     * This method is used to test if instrumentation was run successfully at
     * compile time. If successful, this method will simply be re-written to
     * return true.
     */
    private boolean isInstrumented() {
        return false;
    }

    /****** Public APIs ******/

    /** Interaction traces **/

    /**
     * Starts tracing a custom interaction from an arbitrary point within the client app.
     * Note that slashes will be converted to periods in the name.
     *
     * @param actionName The name for this custom interaction trace.
     * @return The id of the interaction.
     */
    public static String startInteraction(String actionName) {
        checkNull(actionName, "startInteraction: actionName must be an action/method name.");
        log.debug("NewRelic.startInteraction invoked with actionName: " + actionName);

        TraceMachine.startTracing(actionName.replace("/", "."), true, FeatureFlag.featureEnabled(FeatureFlag.InteractionTracing));

        try {
            return TraceMachine.getActivityTrace().getId();
        } catch (TracingInactiveException e) {
            return null;
        }
    }

    /**
     * Starts tracing an interaction from an arbitrary point within an Activity.  Note that slashes
     * will be converted to periods in the name.
     *
     * @param activityContext The current Activity object.
     * @param actionName      The name for this custom interaction trace.  It will appear
     *                        as ActivityClassname#actionName.
     * @return The id of the interaction.
     */
    public static String startInteraction(Context activityContext, String actionName) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_DEPRECATED
                .replace(MetricNames.TAG_NAME, "startInteraction"));

        checkNull(activityContext, "startInteraction: context must be an Activity instance.");
        checkNull(actionName, "startInteraction: actionName must be an action/method name.");

        log.debug("NewRelic.startInteraction invoked with actionName: " + actionName);

        TraceMachine.startTracing(activityContext.getClass().getSimpleName() + "#" + actionName.replace("/", "."), false, FeatureFlag.featureEnabled(FeatureFlag.InteractionTracing));

        try {
            return TraceMachine.getActivityTrace().getId();
        } catch (TracingInactiveException e) {
            return null;
        }
    }

    /**
     * Ends an interaction trace.  This has no effect if no trace is currently running.
     *
     * @param id The id of the interaction you wish to end.  This ensures one doesn't end a new
     *           interaction started more recently.
     */
    public static void endInteraction(String id) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "endInteraction"));

        log.debug("NewRelic.endInteraction invoked. id: " + id);
        TraceMachine.endTrace(id);
    }

    /**
     * Sets and thus overrides the default interaction name derived from the current Activity class
     * name.  Make sure to call this at the beginning of your Activity's onCreate() method to ensure
     * it propagates to all subsequent trace segments.
     *
     * @param name The new name for this interaction.
     */
    public static void setInteractionName(String name) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "setInteractionName"));

        TraceMachine.setRootDisplayName(name);
    }

    /**
     * Starts tracing a method interaction from an arbitrary point within an Activity.
     * Note that slashes will be converted to periods in the name.
     *
     * @param actionName The name for this custom method interaction trace.
     * @return The id of the interaction.
     */
    public static void startMethodTrace(String actionName) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "startMethodTrace"));

        checkNull(actionName, "startMethodTrace: actionName must be an action/method name.");
        TraceMachine.enterMethod(actionName);
    }

    /**
     * Ends an interaction trace.  This has no effect if no trace is currently running.
     **/
    public static void endMethodTrace() {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "endMethodTrace"));

        log.debug("NewRelic.endMethodTrace invoked.");
        TraceMachine.exitMethod();
    }


    /**
     * Custom metrics
     */
    public static void recordMetric(String name, String category, int count, double totalValue, double exclusiveValue, MetricUnit countUnit, MetricUnit valueUnit) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "recordMetric"));

        log.debug("NewRelic.recordMetric invoked for name " + name + ", category: " + category +
                ", count: " + count + ", totalValue " + totalValue + ", exclusiveValue: " + exclusiveValue +
                ", countUnit: " + countUnit + ", valueUnit: " + valueUnit);
        checkNull(category, "recordMetric: category must not be null. If no MetricCategory is applicable, use MetricCategory.NONE.");

        checkEmpty(name, "recordMetric: name must not be empty.");

        if (!checkNegative(count, "recordMetric: count must not be negative.")) {
            Measurements.addCustomMetric(name, category, count, totalValue, exclusiveValue, countUnit, valueUnit);
        }
    }

    public static void recordMetric(String name, String category, double value) {
        recordMetric(name, category, 1, value, value, null, null);
    }

    public static void recordMetric(String name, String category) {
        recordMetric(name, category, 1f);
    }


    /**
     * Network Requests
     */

    /**
     * Record HTTP transaction passing arguments as a map of attributes
     *
     * @param attributes
     */
    @SuppressWarnings("unchecked")
    public static void noticeHttpTransaction(Map<String, Object> attributes) {

        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "noticeHttpTransaction(Map Attribute)"));
        try {
            noticeHttpTransaction(
                    (String) attributes.get("url"),
                    (String) attributes.get("httpMethod"),
                    Integer.parseInt((String) attributes.get("statusCode")),
                    Long.parseLong((String) attributes.get("startTimeMs")),
                    Long.parseLong((String) attributes.get("endTimeMs")),
                    Long.parseLong((String) attributes.get("bytesSent")),
                    Long.parseLong((String) attributes.get("bytesReceived")),
                    (String) attributes.get("responseBody"),
                    null,
                    (String) attributes.get("appData"),
                    (Map<String, Object>) attributes.get("traceAttributes"));

        } catch (NumberFormatException e) {
            log.error(e.getMessage());
            recordHandledException(e);
        }
    }

    public static void noticeHttpTransaction(String url, String httpMethod, int statusCode, long startTimeMs, long endTimeMs, long bytesSent, long bytesReceived) {
        _noticeHttpTransaction(url, httpMethod, statusCode, startTimeMs, endTimeMs, bytesSent, bytesReceived, null, null, null);
    }

    public static void noticeHttpTransaction(String url, String httpMethod, int statusCode, long startTimeMs, long endTimeMs, long bytesSent, long bytesReceived, String responseBody) {
        _noticeHttpTransaction(url, httpMethod, statusCode, startTimeMs, endTimeMs, bytesSent, bytesReceived, responseBody, null, null);
    }

    public static void noticeHttpTransaction(String url, String httpMethod, int statusCode, long startTimeMs, long endTimeMs, long bytesSent, long bytesReceived, String responseBody, Map<String, String> params) {
        _noticeHttpTransaction(url, httpMethod, statusCode, startTimeMs, endTimeMs, bytesSent, bytesReceived, responseBody, params, null);
    }

    public static void noticeHttpTransaction(String url, String httpMethod, int statusCode, long startTimeMs, long endTimeMs, long bytesSent, long bytesReceived, String responseBody, Map<String, String> params, String appData) {
        _noticeHttpTransaction(url, httpMethod, statusCode, startTimeMs, endTimeMs, bytesSent, bytesReceived, responseBody, params, appData);
    }

    public static void noticeHttpTransaction(String url, String httpMethod, int statusCode, long startTimeMs, long endTimeMs, long bytesSent, long bytesReceived, String responseBody, Map<String, String> params, URLConnection urlConnection) {
        if (urlConnection != null) {
            final String header = urlConnection.getHeaderField(Constants.Network.CROSS_PROCESS_ID_HEADER);

            if (header != null && header.length() > 0) {
                _noticeHttpTransaction(url, httpMethod, statusCode, startTimeMs, endTimeMs, bytesSent, bytesReceived, responseBody, params, header);
                return;
            }
        }

        _noticeHttpTransaction(url, httpMethod, statusCode, startTimeMs, endTimeMs, bytesSent, bytesReceived, responseBody, params, null);
    }

    static void _noticeHttpTransaction(String url, String httpMethod, int statusCode, long startTimeMs, long endTimeMs, long bytesSent, long bytesReceived, String responseBody, Map<String, String> params, String appData) {
        noticeHttpTransaction(url, httpMethod, statusCode, startTimeMs, endTimeMs, bytesSent, bytesReceived, responseBody, params, appData, null);
    }

    public static void noticeHttpTransaction(
            String url,
            String httpMethod,
            int statusCode,
            long startTimeMs,
            long endTimeMs,
            long bytesSent,
            long bytesReceived,
            String responseBody,
            Map<String, String> params,
            String appData,
            Map<String, Object> traceAttributes) {

        checkEmpty(url, "noticeHttpTransaction: url must not be empty.");
        checkEmpty(httpMethod, "noticeHttpTransaction: httpMethod must not be empty.");

        try {
            new URL(url);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("noticeHttpTransaction: URL is malformed: " + url);
        }

        float totalTime = endTimeMs - startTimeMs;

        if (!checkNegative((int) totalTime, "noticeHttpTransaction: the startTimeMs is later than the endTimeMs, resulting in a negative total time.")) {

            // Convert to fractional seconds.
            totalTime /= 1000.0;

            TransactionData transactionData = new TransactionData(url, httpMethod, Agent.getActiveNetworkCarrier(),
                    totalTime, statusCode, 0, bytesSent, bytesReceived, appData, Agent.getActiveNetworkWanType(), null,
                    responseBody, params, traceAttributes);

            TaskQueue.queue(new HttpTransactionMeasurement(transactionData));
        }
    }

    /**
     * Distributed Tracing
     */

    /**
     * Create a trace context in preparation for un-instrumented network transactions and errors.
     * Clients should use the headers provided by the trace context:
     * <p>
     * TraceContext traceContext = TraceContext.createTraceContext(null);
     * Set<TraceHeader> headers = traceContext.getHeaders();
     * for(Iterator<TraceHeader> it = headers.iterator(); it.hasNext(); ) {
     * TraceHeader header = it.next();
     * header.getHeaderName();
     * header.getHeaderValue();
     * }
     * <p>
     * The use traceContext when recording the transaction in static
     * noticeHttpTransaction/noticeNetworkFailure methods that take TraceContext as parameter.
     *
     * @param requestAttributes
     * @return Set<TraceHeader> containing all headers used by trace context.
     */
    public static TraceContext noticeDistributedTrace(Map<String, String> requestAttributes) {

        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "noticeDistributedTrace"));

        return TraceContext.createTraceContext(requestAttributes);
    }


    /* Utility methods */

    private static void checkNull(Object object, String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void checkEmpty(String string, String message) {
        checkNull(string, message);

        if (string.length() == 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private static boolean checkNegative(int number, String message) {
        if (number < 0) {
            log.error(message);
            if (FeatureFlag.featureEnabled(FeatureFlag.HandledExceptions)) {
                NewRelic.recordHandledException(new RuntimeException(message));
            }
            return true;
        }
        return false;
    }

    /**
     *  Crash reporting related methods
     */

    /**
     * Crashes the currently running app for crash reporting demonstration purposes.
     */
    public static void crashNow() {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "crashNow"));

        crashNow("This is a demonstration crash courtesy of New Relic");
    }

    /**
     * Crashes the currently running app for crash reporting demonstration purposes with a message.
     *
     * @param message The message with which to crash.
     */
    public static void crashNow(String message) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "crashNow(String)"));

        throw new RuntimeException(message);
    }


    /**
     * Custom Events related methods
     */

    /**
     * Sets a string attribute value.
     *
     * @param name  The attribute name
     * @param value The string attribute value
     * @return true if successful, false if the operation did not complete as anticipated.
     */
    public static boolean setAttribute(String name, String value) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "setAttribute(String,String)"));

        return AnalyticsControllerImpl.getInstance().setAttribute(name, value);
    }

    /**
     * Sets a numeric float attribute value.
     *
     * @param name  The attribute name
     * @param value The float attribute value
     * @return true if successful, false if the operation did not complete as anticipated.
     */
    public static boolean setAttribute(String name, double value) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "setAttribute(String,double)"));

        return AnalyticsControllerImpl.getInstance().setAttribute(name, value);
    }

    /**
     * Sets a boolean attribute value.
     *
     * @param name  The attribute name
     * @param value The boolean attribute value
     * @return true if successful, false if the operation did not complete as anticipated.
     */
    public static boolean setAttribute(String name, boolean value) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "setAttribute(String,boolean)"));
        return AnalyticsControllerImpl.getInstance().setAttribute(name, value);
    }

    /**
     * Increments the value of an attribute.
     *
     * @param name The attribute name
     * @return true if successful, false if the operation did not complete as anticipated.
     */
    public static boolean incrementAttribute(String name) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "incrementAttribute(String)"));
        return AnalyticsControllerImpl.getInstance().incrementAttribute(name, 1.00f);
    }

    /**
     * Increments the value of an attribute by a specified amount.
     *
     * @param name  The attribute name
     * @param value Amount by which to increment the value
     * @return true if successful, false if the operation did not complete as anticipated.
     */
    public static boolean incrementAttribute(String name, double value) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "incrementAttribute(String, double)"));
        return AnalyticsControllerImpl.getInstance().incrementAttribute(name, value);
    }

    /**
     * Removes an attribute.
     *
     * @param name The attribute name
     * @return true if successful, false if the operation did not complete as anticipated.
     */
    public static boolean removeAttribute(String name) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "removeAttribute"));
        return AnalyticsControllerImpl.getInstance().removeAttribute(name);
    }

    /**
     * Removes all accumulated attributes.
     *
     * @return true if successful, false if the operation did not complete as anticipated.
     */

    public static boolean removeAllAttributes() {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "removeAllAttribute"));

        return AnalyticsControllerImpl.getInstance().removeAllAttributes();
    }

    /**
     * Sets a user ID attribute.
     *
     * @param userId The user ID as string value
     * @return true if successful, false if the operation did not complete as anticipated.
     */
    public static boolean setUserId(String userId) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "setUserId"));

        return AnalyticsControllerImpl.getInstance().setAttribute(AnalyticsAttribute.USER_ID_ATTRIBUTE, userId);
    }

    /**
     * Records a custom analytic event with a specified eventType.
     *
     * @param eventType       The name of the custom event type to be recorded. The name should be
     *                        comprised of alphanumeric, ' ', '.', ':', or '_' characters.
     * @param eventAttributes A map of key-value pairs holding the event attributes.  The values must
     *                        be of type String, Double, or Boolean.
     * @return Returns true if the event was successfully recorded
     */
    public static boolean recordCustomEvent(String eventType, Map<String, Object> eventAttributes) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "recordCustomEvent"));

        if (null == eventAttributes) {
            eventAttributes = new HashMap<>();
        }
        return AnalyticsControllerImpl.getInstance().recordCustomEvent(eventType, eventAttributes);
    }

    /**
     * Records a custom analytic event with a specified eventType and name.
     *
     * @param eventType       The name of the custom event type to be recorded. The name should be
     *                        comprised of alphanumeric, ' ', '.', ':', or '_' characters.
     * @param eventName       The name of the custom event to be recorded.
     * @param eventAttributes A map of key-value pairs holding the event attributes.  The values must
     *                        be of type String, Double, or Boolean.
     */
    public static boolean recordCustomEvent(String eventType, String eventName, Map<String, Object> eventAttributes) {
        if (null == eventAttributes) {
            eventAttributes = new HashMap<>();
        }
        if (eventName != null && !eventName.isEmpty()) {
            eventAttributes.put(AnalyticsAttribute.EVENT_NAME_ATTRIBUTE, eventName);
        }
        return recordCustomEvent(eventType, eventAttributes);
    }

    /**
     * Records a MobileBreadcrumb event with a given name
     *
     * @param breadcrumbName Name of the breadcrumb to be recorded as a MobileBreadcrumb event.
     *                       The name should be comprised of alphanumeric, ' ', '.', ':', or '_' characters.
     * @return Returns true if the MobileBreadcrumb was successfully recorded
     */
    public static boolean recordBreadcrumb(String breadcrumbName) {
        return recordBreadcrumb(breadcrumbName, null);
    }

    /**
     * Records a MobileBreadcrumb event with a given name, and a map of key values
     *
     * @param breadcrumbName Name of the breadcrumb to be recorded as a MobileBreadcrumb event.
     *                       The name should be comprised of alphanumeric, ' ', '.', ':', or '_' characters.
     * @param attributes     A map of key-value pairs holding the event attributes. The values must
     *                       be of type String, Double, or Boolean.
     * @return Returns true if the MobileBreadcrumb was successfully recorded
     */
    public static boolean recordBreadcrumb(String breadcrumbName, Map<String, Object> attributes) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "recordBreadcrumb"));

        if (null == attributes) {
            attributes = new HashMap<>();
        }
        if (breadcrumbName != null && !breadcrumbName.isEmpty()) {
            attributes.put(AnalyticsAttribute.EVENT_NAME_ATTRIBUTE, breadcrumbName);
        }
        return AnalyticsControllerImpl.getInstance().recordBreadcrumb(breadcrumbName, attributes);
    }

    /**
     * Records a handled exception.
     *
     * @param exception Exception
     * @return Returns true if the exception was queued for delivery
     */
    public static boolean recordHandledException(Exception exception) {
        return recordHandledException(exception, null);
    }

    /**
     * Records a handled exception.
     *
     * @param exception           Exception
     * @param exceptionAttributes A map of key-value pairs containing optional exception attributes.
     *                            The values must be of type String, Double, or Boolean.
     * @return Returns true if the exception was queued for delivery
     */
    public static boolean recordHandledException(Exception exception, Map<String, Object> exceptionAttributes) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "recordHandledException"));

        if (exceptionAttributes == null) {
            exceptionAttributes = new HashMap<>();
        }
        return recordHandledException((Throwable) exception, exceptionAttributes);
    }

    /**
     * Records a throwable exception.
     *
     * @param throwable Throwable class instance
     * @return Returns true if the exception was queued for delivery
     */
    public static boolean recordHandledException(Throwable throwable) {
        return recordHandledException(throwable, null);
    }

    /**
     * Records a throwable exception.
     *
     * @param throwable  Throwable class instance
     * @param attributes A map of key-value pairs containing optional exception attributes.
     *                   The values must be of type String, Double, or Boolean.
     * @return Returns true if the exception was queued for delivery
     */
    public static boolean recordHandledException(Throwable throwable, Map<String, Object> attributes) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "recordThrowable"));

        if (attributes == null) {
            attributes = new HashMap<>();
        }
        return AgentDataController.sendAgentData(throwable, attributes);
    }

    /**
     * Set the maximum size of the event buffer.  When the limit is reached, the agent will transmit
     * the queue contents on the next harvest cycle.
     *
     * @param maxSize
     */
    public static void setMaxEventPoolSize(int maxSize) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "setMaxEventPoolSize"));

        AnalyticsControllerImpl.getInstance().setMaxEventPoolSize(maxSize);
    }

    /**
     * Sets the maximum time (in seconds) that the agent will store events in memory.
     * Once the oldest event in the queue exceeds this age, the entire queue will be transmitted
     * on the following harvest cycle.
     *
     * @param maxBufferTimeInSec
     */
    public static void setMaxEventBufferTime(int maxBufferTimeInSec) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "setMaxEventBufferTime"));

        AnalyticsControllerImpl.getInstance().setMaxEventBufferTime(maxBufferTimeInSec);
    }

    /**
     * Set callback interface to be invoked at key times by the event manager
     *
     * @param eventListener implementation
     */
    public static void setEventListener(EventListener eventListener) {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "setEventListener"));

        AnalyticsControllerImpl.getInstance().getEventManager().setEventListener(eventListener);
    }

    /**
     * Returns the ID for the current session.
     *
     * @return the current session ID.
     */
    public static String currentSessionId() {
        StatsEngine.notice().inc(MetricNames.SUPPORTABILITY_API
                .replace(MetricNames.TAG_NAME, "currentSessionId"));

        return agentConfiguration.getSessionID();
    }

    /**
     * Records a JSError exception.
     *
     * @param stackTrace
     */
    public static boolean recordJSErrorException(StackTrace stackTrace) {
        return DataController.sendAgentData(stackTrace);
    }
}
