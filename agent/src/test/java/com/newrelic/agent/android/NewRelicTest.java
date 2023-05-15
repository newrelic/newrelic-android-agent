/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import static com.newrelic.agent.android.NewRelic.agentConfiguration;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.newrelic.agent.android.agentdata.AgentDataReporter;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsAttributeStore;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.analytics.AnalyticsEventCategory;
import com.newrelic.agent.android.analytics.EventListener;
import com.newrelic.agent.android.analytics.EventManager;
import com.newrelic.agent.android.analytics.EventManagerImpl;
import com.newrelic.agent.android.analytics.EventTransformAdapter;
import com.newrelic.agent.android.analytics.NetworkEventTransformer;
import com.newrelic.agent.android.analytics.NetworkRequestEvent;
import com.newrelic.agent.android.background.ApplicationStateEvent;
import com.newrelic.agent.android.background.ApplicationStateMonitor;
import com.newrelic.agent.android.distributedtracing.DistributedTracing;
import com.newrelic.agent.android.distributedtracing.TraceConfiguration;
import com.newrelic.agent.android.distributedtracing.TraceContext;
import com.newrelic.agent.android.distributedtracing.TraceListener;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestAdapter;
import com.newrelic.agent.android.harvest.HarvestData;
import com.newrelic.agent.android.harvest.HttpTransaction;
import com.newrelic.agent.android.harvest.HttpTransactions;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.measurement.consumer.CustomMetricConsumer;
import com.newrelic.agent.android.metric.Metric;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.metric.MetricUnit;
import com.newrelic.agent.android.payload.NullPayloadStore;
import com.newrelic.agent.android.payload.Payload;
import com.newrelic.agent.android.payload.PayloadController;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.test.mock.Providers;
import com.newrelic.agent.android.test.spy.AgentDataReporterSpy;
import com.newrelic.agent.android.tracing.Trace;
import com.newrelic.agent.android.tracing.TraceMachine;
import com.newrelic.agent.android.util.Constants;
import com.newrelic.agent.android.util.NetworkFailure;

import junit.framework.Assert;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
public class NewRelicTest {

    private NewRelic nrInstance;

    private static final String APP_TOKEN = "ab20dfe5-96d2-4c6d-b975-3fe9d8778dfc";
    private static final String APP_VERSION = "1.2.3.4";
    private static final String APP_BUILD = "1.001";
    private static final String APP_URL = "http://swear.net";
    private static final ApplicationFramework APP_FRAMEWORK = ApplicationFramework.ReactNative;
    private static final String APP_FRAMEWORK_VERSION = "9.8.7";

    private SpyContext spyContext;
    private AnalyticsControllerImpl analyticsController;
    private EventManager eventManager;

    @BeforeClass
    public static void classSetUp() {
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
    }

    @Before
    public void removeFinalModifiers() throws Exception {
        Field field = Agent.class.getDeclaredField("MONO_INSTRUMENTATION_FLAG");
        field.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        field.set(String.class, "YES");
    }

    @Before
    public void setUp() {
        spyContext = new SpyContext();
        nrInstance = NewRelic.withApplicationToken(APP_TOKEN).withLogLevel(AgentLog.DEBUG);
        Assert.assertNotNull(nrInstance);

        NewRelic.enableFeature(FeatureFlag.HttpResponseBodyCapture);
        NewRelic.enableFeature(FeatureFlag.CrashReporting);
        NewRelic.enableFeature(FeatureFlag.AnalyticsEvents);
        NewRelic.enableFeature(FeatureFlag.InteractionTracing);
        NewRelic.enableFeature(FeatureFlag.DefaultInteractions);
    }

    @Before
    public void setupAnalyticsController() {
        AgentConfiguration agentConfig = new AgentConfiguration();
        agentConfig.setEnableAnalyticsEvents(true);
        agentConfig.setAnalyticsAttributeStore(new StubAnalyticsAttributeStore());

        analyticsController = AnalyticsControllerImpl.getInstance();
        AnalyticsControllerImpl.initialize(agentConfig, new NullAgentImpl());

        eventManager = analyticsController.getEventManager();
        eventManager.empty();
        eventManager.setEventListener((EventListener) eventManager);
    }

    @Before
    public void setUpHarvester() {
        TestHarvest testHarvest = new TestHarvest();
        TestHarvest.setInstance(testHarvest);
        TestHarvest.initialize(new AgentConfiguration());
        Measurements.initialize();
    }

    @Before
    public void setUpPayloadController() {
        agentConfiguration.setPayloadStore(new NullPayloadStore<Payload>());
        PayloadController.initialize(agentConfiguration);
    }

    @Before
    public void setupDistributedTracing() {
        DistributedTracing.getInstance().setConfiguration(new TraceConfiguration(Providers.provideHarvestConfiguration()));
    }

    @After
    public void tearDown() {
        AnalyticsControllerImpl.shutdown();
        Measurements.shutdown();
        PayloadController.shutdown();
    }

    @Test
    public void testBuilder() {
        NewRelic instance = NewRelic.withApplicationToken(APP_TOKEN).
                usingCollectorAddress(APP_URL).
                usingCrashCollectorAddress(APP_URL).
                withLogLevel(AgentLog.DEBUG).
                withApplicationVersion(APP_VERSION).
                withApplicationBuild(APP_BUILD);

        Assert.assertEquals("Should set app token", APP_TOKEN, agentConfiguration.getApplicationToken());
        Assert.assertEquals("Should set crash collector address", APP_URL, agentConfiguration.getCrashCollectorHost());
        Assert.assertEquals("Should set crash collector address", APP_URL, agentConfiguration.getCrashCollectorHost());
        Assert.assertEquals("Should set debug agent logging", AgentLog.DEBUG, instance.logLevel);
        Assert.assertEquals("Should set app version", agentConfiguration.getCustomApplicationVersion(), APP_VERSION);
        Assert.assertEquals("Should set app build", agentConfiguration.getCustomBuildIdentifier(), APP_BUILD);
    }

    @Test
    public void testWithApplicationToken() {
        Assert.assertTrue("Should return NewRelic instance", nrInstance instanceof NewRelic);
        Assert.assertEquals("Should set app token", APP_TOKEN, agentConfiguration.getApplicationToken());
    }

    @Test
    public void testUsingCollectorAddress() {
        nrInstance.usingCollectorAddress(APP_URL);
        Assert.assertEquals("Should set collector address", APP_URL, agentConfiguration.getCollectorHost());
    }

    @Test
    public void testUsingCrashCollectorAddress() {
        nrInstance.usingCrashCollectorAddress(APP_URL);
        Assert.assertEquals("Should set crash collector address", APP_URL, agentConfiguration.getCrashCollectorHost());
    }

    @Test
    public void testWithLoggingEnabled() {
        nrInstance.withLoggingEnabled(false);
        Assert.assertFalse("Should disable logging", nrInstance.loggingEnabled);
    }

    @Test
    public void testWithLogLevel() {
        nrInstance.withLogLevel(AgentLog.AUDIT);
        Assert.assertEquals("Should set audit-level agent logging", AgentLog.AUDIT, nrInstance.logLevel);
    }

    @Test
    public void testDefaultFeatureFlag() {
        Assert.assertTrue("Should enable Http Response body capture", FeatureFlag.featureEnabled(FeatureFlag.HttpResponseBodyCapture));
        Assert.assertTrue("Should enable crash reporting", FeatureFlag.featureEnabled(FeatureFlag.CrashReporting));
        Assert.assertTrue("Should enable analytics events", FeatureFlag.featureEnabled(FeatureFlag.AnalyticsEvents));
        Assert.assertTrue("Should enable default interactions", FeatureFlag.featureEnabled(FeatureFlag.DefaultInteractions));
    }

    @Test
    public void testWithCrashReportingEnabled() {
        nrInstance.withCrashReportingEnabled(true);
        Assert.assertTrue("Should enable JIT crash reporting", FeatureFlag.featureEnabled(FeatureFlag.CrashReporting));
    }

    @Test
    public void testWithApplicationVersion() {
        nrInstance.withApplicationVersion(APP_VERSION);
        Assert.assertEquals("Should set app version", agentConfiguration.getCustomApplicationVersion(), APP_VERSION);
    }

    @Test
    public void testWithApplicationFramework() {
        nrInstance.withApplicationFramework(APP_FRAMEWORK, APP_FRAMEWORK_VERSION);
        Assert.assertEquals("Should set app framework", agentConfiguration.getApplicationFramework(), APP_FRAMEWORK);
        Assert.assertEquals("Should set app framework version", agentConfiguration.getApplicationFrameworkVersion(), APP_FRAMEWORK_VERSION);

        nrInstance.withApplicationFramework(APP_FRAMEWORK, null);
        Assert.assertEquals("Should set app framework", agentConfiguration.getApplicationFramework(), APP_FRAMEWORK);
        Assert.assertNull("Should allow optional framework version", agentConfiguration.getApplicationFrameworkVersion());

        nrInstance.withApplicationVersion("7.6.5");
        Assert.assertEquals("Should set app framework", agentConfiguration.getApplicationFramework(), APP_FRAMEWORK);
        Assert.assertNull("Should allow optional framework version", agentConfiguration.getApplicationFrameworkVersion());
    }

    @Test
    public void testWithLaunchActivityName() {
        nrInstance.withLaunchActivityName("TestActivity");
        Assert.assertEquals("TestActivity", agentConfiguration.getLaunchActivityClassName());
    }

    @Test
    public void testEnableFeature() {
        NewRelic.enableFeature(FeatureFlag.CrashReporting);
        Assert.assertTrue("Should enable feature flag", FeatureFlag.featureEnabled(FeatureFlag.CrashReporting));
    }

    @Test
    public void testDisableFeature() {
        NewRelic.disableFeature(FeatureFlag.CrashReporting);
        Assert.assertFalse("Should disable feature flag", FeatureFlag.featureEnabled(FeatureFlag.CrashReporting));
    }


    // @Test
    public void testIsStarted() {
        Assert.assertFalse("Agent should not be started", NewRelic.isStarted());
        nrInstance.start(spyContext.getContext());
        Assert.assertTrue("Should start agent", NewRelic.isStarted());
    }

    @Test
    public void testShutdown() throws Exception {
        //Manually shutdown agent, as agent cannot be started
        NewRelic.isShutdown = true;
        Agent.getImpl().stop();

        //check all the harvestData
        HarvestData harvestData = Harvest.getInstance().getHarvestData();
        Assert.assertEquals(harvestData.getHttpTransactions().count(), 0);
        Assert.assertEquals(harvestData.getActivityTraces().count(), 0);
        Assert.assertEquals(harvestData.getAnalyticsEvents().size(), 0);
        Assert.assertEquals(harvestData.getAgentHealth().asJsonArray().size(), 0);
        Assert.assertEquals(harvestData.getDataToken().getAccountId(), 0);
        Assert.assertEquals(harvestData.getDataToken().getAgentId(), 0);
    }

    @Test
    public void testAgentStatesBetweenStartShutdown() throws Exception {
        //Manually stop agent
        NewRelic.started = false;
        NewRelic.shutdown();
        Assert.assertFalse(NewRelic.isShutdown);

        //Manually start agent, as cannot call NewRelic.start()
        NewRelic.started = true;
        NewRelic.shutdown();

        Assert.assertFalse(NewRelic.started);
        Assert.assertTrue(NewRelic.isShutdown);
    }

    @Test
    public void testHarvestBetweenForegroundBackground() throws Exception {
        //setup application foreground and background
        ApplicationStateEvent e = new ApplicationStateEvent(ApplicationStateMonitor.getInstance());
        AgentConfiguration agentConfig = new AgentConfiguration();
        agentConfig.setApplicationToken(APP_TOKEN);
        AndroidAgentImpl agentImpl = new AndroidAgentImpl(spyContext.getContext(), agentConfig);
        Agent.setImpl(agentImpl);

        //Manually start the Harvest
        NewRelic.started = true;
        Harvest.start();
        NewRelic.shutdown();

        agentImpl.start();
        agentImpl.applicationBackgrounded(e);
        agentImpl.applicationForegrounded(e);

        Harvest instance = Harvest.getInstance();
        Assert.assertNull(instance.getHarvestData());
        Assert.assertNull(instance.getHarvestConnection());

        //app goes to foreground/background after HarvestData is null
        agentImpl.applicationBackgrounded(e);
        agentImpl.applicationForegrounded(e);
    }

    /**
     * Needs PowerMock to test final/static classes:
     **/
    @Test
    public void testStartInteraction() throws Exception {
        String id = NewRelic.startInteraction("fuzzy");
        Assert.assertEquals("Should equal interaction name", "fuzzy", TraceMachine.getCurrentTrace().displayName);
        Assert.assertEquals("Interaction ID should be a Type-4 generated UUID", id, UUID.fromString(id).toString());
    }

    @Test
    public void testEndInteraction() {
        String id = NewRelic.startInteraction("fuzzy");
        Assert.assertTrue("Tracing should be active", TraceMachine.isTracingActive());
        NewRelic.endInteraction(id);
        Assert.assertFalse("Tracing should be inactive", TraceMachine.isTracingActive());
    }

    @Test
    public void testSetInteractionName() throws Exception {
        TraceMachine.startTracing("greasy");
        Trace trace = TraceMachine.getCurrentTrace();
        Assert.assertEquals("Should set interaction name", "Display greasy", trace.displayName);
        NewRelic.setInteractionName("fuzzy");
        Assert.assertEquals("Should set interaction name", "fuzzy", trace.displayName);
    }

    @Test
    public void testRecordMetric() {
        final String metricCategory = "Cheese";
        final MetricUnit metricUnit = MetricUnit.BYTES_PER_SECOND;

        TestCustomMetricConsumer customConsumer = new TestCustomMetricConsumer();
        Measurements.addMeasurementConsumer(customConsumer);

        NewRelic.recordMetric("Wensleydale", metricCategory);
        NewRelic.recordMetric("Gorgonzola", metricCategory, 4, 4f, 4f, metricUnit, metricUnit);

        HarvestData harvestData = TestHarvest.getInstance().getHarvestData();
        customConsumer.onHarvest();

        final List<Metric> metrics = harvestData.getMetrics().getMetrics().getAll();

        Assert.assertTrue("Should contain at least 2 metrics", 2 <= metrics.size());
        Assert.assertNotNull("Should find cheddar", findMetricByName(metrics, "Wensleydale"));
        Assert.assertNotNull("Should find mold", findMetricByName(metrics, "Gorgonzola"));

        try {
            NewRelic.recordMetric(null, metricCategory);
            Assert.fail("Should throw on null metric name");
        } catch (IllegalArgumentException e) {
        }

        try {
            NewRelic.recordMetric(metricCategory, null);
            Assert.fail("Should throw on null metric category");
        } catch (IllegalArgumentException e) {
        }

    }

    private Metric findMetricByName(List<Metric> metrics, String name) {
        for (Metric m : metrics) {
            if (m.getName().toLowerCase().contains(name.toLowerCase())) {
                return m;
            }
        }
        return null;
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testNoticeHttpTransaction() {
        Map<String, String> map = new HashMap<String, String>();

        // test all the decprecated overloads
        testDeprecatedMethod("noticeHttpTransaction", "public", String.class, int.class, long.class, long.class, long.class, long.class);
        testDeprecatedMethod("noticeHttpTransaction", "public", String.class, int.class, long.class, long.class, long.class, long.class, String.class);
        testDeprecatedMethod("noticeHttpTransaction", "public", String.class, int.class, long.class, long.class, long.class, long.class, String.class, map.getClass());
        testDeprecatedMethod("noticeHttpTransaction", "public", String.class, int.class, long.class, long.class, long.class, long.class, String.class, map.getClass(), String.class);
        testDeprecatedMethod("noticeHttpTransaction", "public", String.class, int.class, long.class, long.class, long.class, long.class, String.class, map.getClass(), String.class, HttpResponse.class);
        testDeprecatedMethod("noticeHttpTransaction", "public", String.class, int.class, long.class, long.class, long.class, long.class, String.class, map.getClass(), URLConnection.class);

        HarvestData harvestData = TestHarvest.getInstance().getHarvestData();
        HttpTransactions transactions = harvestData.getHttpTransactions();
        long now = System.currentTimeMillis();
        long later = now + 1000;

        NewRelic.noticeHttpTransaction(APP_URL, "GET", HttpStatus.SC_CREATED, now, later, 100, 200, "Created");
        NewRelic.noticeHttpTransaction(APP_URL, "GET", HttpStatus.SC_ACCEPTED, now, later, 100, 200, "Accepted", map);
        NewRelic.noticeHttpTransaction(APP_URL, "PUT", HttpStatus.SC_NO_CONTENT, now, later, 100, 200, "No content", map, "appData");
        NewRelic.noticeHttpTransaction(APP_URL, "GET", HttpStatus.SC_MOVED_TEMPORARILY, now, later, 100, 200, "Moved temporarily", map, provideUrlConnection());

        TaskQueue.synchronousDequeue();
        Assert.assertEquals("Should contain 4 transaction", 4, transactions.count());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testNoticeNetworkFailure() {
        testDeprecatedMethod("noticeNetworkFailure", "public", String.class, long.class, long.class, NetworkFailure.class);

        long now = System.currentTimeMillis();
        long later = now + 1000;

        NewRelic.noticeNetworkFailure(APP_URL, now, later, NetworkFailure.exceptionToNetworkFailure(new RuntimeException("networkFailure")));
        TaskQueue.synchronousDequeue();
        HarvestData harvestData = TestHarvest.getInstance().getHarvestData();
        HttpTransactions transactions = harvestData.getHttpTransactions();

        Assert.assertEquals("Should contain 1 transaction", 1, transactions.count());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testNoticeHttpTransactionWithDistributedTracing() {
        Map<String, String> map = new HashMap<String, String>();
        HarvestData harvestData = TestHarvest.getInstance().getHarvestData();
        HttpTransactions transactions = harvestData.getHttpTransactions();
        long now = System.currentTimeMillis();
        long later = now + 1000;
        Map<String, String> attributes = new HashMap<String, String>() {{
            put("url", "");
            put("statusCode", "0");
        }};
        TraceContext traceContext = NewRelic.noticeDistributedTrace(attributes);
        Map<String, Object> traceAttributes = traceContext.asTraceAttributes();

        NewRelic.enableFeature(FeatureFlag.DistributedTracing);

        NewRelic.noticeHttpTransaction(APP_URL, "GET", HttpStatus.SC_CREATED, now, later, 100, 200, "Created");
        NewRelic.noticeHttpTransaction(APP_URL, "GET", HttpStatus.SC_ACCEPTED, now, later, 100, 200, "Accepted", map);
        NewRelic.noticeHttpTransaction(APP_URL, "GET", HttpStatus.SC_NO_CONTENT, now, later, 100, 200, "No content", map, "appData");
        NewRelic.noticeHttpTransaction(APP_URL, "GET", HttpStatus.SC_MOVED_TEMPORARILY, now, later, 100, 200, "Moved temporarily", map, provideUrlConnection());
        TaskQueue.synchronousDequeue();

        Assert.assertEquals("Should contain 4 transaction", 4, transactions.count());
        for (HttpTransaction txn : transactions.getHttpTransactions()) {
            Assert.assertNull(txn.getTraceContext());
        }

        Assert.assertEquals("Should contain 4 network request events", 4, eventManager.size());
        for (AnalyticsEvent event : eventManager.getQueuedEvents()) {
            Assert.assertEquals("Should contain NetworkRequestEvent", "MobileRequest", event.getEventType());
            Assert.assertNull(getAttributeByName(event.getAttributeSet(), DistributedTracing.NR_ID_ATTRIBUTE));
            Assert.assertNull(getAttributeByName(event.getAttributeSet(), DistributedTracing.NR_GUID_ATTRIBUTE));
            Assert.assertNull(getAttributeByName(event.getAttributeSet(), DistributedTracing.NR_TRACE_ID_ATTRIBUTE));
        }

        eventManager.empty();
        harvestData.reset();

        // Status codes >= 400 (SC_BAD_REQUEST) should also record an HttpError
        NewRelic.noticeHttpTransaction(APP_URL, "GET", HttpStatus.SC_INTERNAL_SERVER_ERROR, now, later, 100, 200, "Not found", map, "appData");
        TaskQueue.synchronousDequeue();

        Assert.assertEquals("Should record error request event", 1, eventManager.size());

        for (AnalyticsEvent event : eventManager.getQueuedEvents()) {
            Assert.assertEquals("Should contain NetworkErrorEvent", "MobileRequestError", event.getEventType());
            Assert.assertNull(getAttributeByName(event.getAttributeSet(), DistributedTracing.NR_ID_ATTRIBUTE));
            Assert.assertNull(getAttributeByName(event.getAttributeSet(), DistributedTracing.NR_GUID_ATTRIBUTE));
            Assert.assertNull(getAttributeByName(event.getAttributeSet(), DistributedTracing.NR_TRACE_ID_ATTRIBUTE));
        }

        eventManager.empty();
        harvestData.reset();

        // Request *with* TraceContext provided
        NewRelic.noticeHttpTransaction(APP_URL, "GET", HttpStatus.SC_NO_CONTENT, now, later, 100, 200, "No content", map, "appData", traceAttributes);
        TaskQueue.synchronousDequeue();

        Assert.assertEquals("Should contain 1 transaction", 1, transactions.count());
        Assert.assertEquals("Should contain 3 trace Attributes", 3, traceAttributes.size());
        for (HttpTransaction txn : transactions.getHttpTransactions()) {
            Assert.assertEquals(traceContext.asTraceAttributes().get(DistributedTracing.NR_ID_ATTRIBUTE), traceAttributes.get(DistributedTracing.NR_ID_ATTRIBUTE));
            Assert.assertEquals(traceContext.asTraceAttributes().get(DistributedTracing.NR_GUID_ATTRIBUTE), traceAttributes.get(DistributedTracing.NR_GUID_ATTRIBUTE));
            Assert.assertEquals(traceContext.asTraceAttributes().get(DistributedTracing.NR_TRACE_ID_ATTRIBUTE), traceAttributes.get(DistributedTracing.NR_TRACE_ID_ATTRIBUTE));
        }

        for (AnalyticsEvent event : eventManager.getQueuedEvents()) {
            Assert.assertEquals("Should contain NetworkErrorEvent", "MobileRequest", event.getEventType());
            Assert.assertNotNull(getAttributeByName(event.getAttributeSet(), DistributedTracing.NR_ID_ATTRIBUTE));
            Assert.assertNotNull(getAttributeByName(event.getAttributeSet(), DistributedTracing.NR_GUID_ATTRIBUTE));
            Assert.assertNotNull(getAttributeByName(event.getAttributeSet(), DistributedTracing.NR_TRACE_ID_ATTRIBUTE));
        }

        eventManager.empty();
        harvestData.reset();

        // Status codes >= 400 (SC_BAD_REQUEST) should also record an HttpError
        NewRelic.noticeHttpTransaction(APP_URL, "GET", HttpStatus.SC_INTERNAL_SERVER_ERROR, now, later, 100, 200, "Not found", map, "appData", traceAttributes);
        TaskQueue.synchronousDequeue();

        Assert.assertEquals("Should record error request event", 1, eventManager.size());

        for (AnalyticsEvent event : eventManager.getQueuedEvents()) {
            Assert.assertEquals("Should contain NetworkErrorEvent", "MobileRequestError", event.getEventType());
            Assert.assertNotNull(getAttributeByName(event.getAttributeSet(), DistributedTracing.NR_ID_ATTRIBUTE));
            Assert.assertNotNull(getAttributeByName(event.getAttributeSet(), DistributedTracing.NR_GUID_ATTRIBUTE));
            Assert.assertNotNull(getAttributeByName(event.getAttributeSet(), DistributedTracing.NR_TRACE_ID_ATTRIBUTE));
        }

        NewRelic.disableFeature(FeatureFlag.DistributedTracing);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testNoticeNetworkFailureWithDistributedTracing() {
        long now = System.currentTimeMillis();
        long later = now + 1000;
        TraceContext traceContext = NewRelic.noticeDistributedTrace(null);
        Map<String, Object> traceAttributes = traceContext.asTraceAttributes();
        NewRelic.enableFeature(FeatureFlag.DistributedTracing);

        NewRelic.noticeNetworkFailure(APP_URL, "get", now, later, NetworkFailure.exceptionToNetworkFailure(new RuntimeException("networkFailure")), "", traceAttributes);
        TaskQueue.synchronousDequeue();

        HarvestData harvestData = TestHarvest.getInstance().getHarvestData();
        HttpTransactions transactions = harvestData.getHttpTransactions();

        Assert.assertEquals("Should contain 1 transaction", 1, transactions.count());

        Assert.assertEquals("Should contain 1 MobileRequestError event", 1, eventManager.size());
        Assert.assertEquals("Should contain 3 trace Attributes", 3, traceAttributes.size());
        for (HttpTransaction txn : transactions.getHttpTransactions()) {
            Assert.assertEquals(traceContext.asTraceAttributes().get(DistributedTracing.NR_ID_ATTRIBUTE), traceAttributes.get(DistributedTracing.NR_ID_ATTRIBUTE));
            Assert.assertEquals(traceContext.asTraceAttributes().get(DistributedTracing.NR_GUID_ATTRIBUTE), traceAttributes.get(DistributedTracing.NR_GUID_ATTRIBUTE));
            Assert.assertEquals(traceContext.asTraceAttributes().get(DistributedTracing.NR_TRACE_ID_ATTRIBUTE), traceAttributes.get(DistributedTracing.NR_TRACE_ID_ATTRIBUTE));
        }

        for (AnalyticsEvent event : eventManager.getQueuedEvents()) {
            Assert.assertEquals("Should contain NetworkErrorEvent", "MobileRequestError", event.getEventType());
            Assert.assertNotNull(getAttributeByName(event.getAttributeSet(), DistributedTracing.NR_ID_ATTRIBUTE));
            Assert.assertNotNull(getAttributeByName(event.getAttributeSet(), DistributedTracing.NR_GUID_ATTRIBUTE));
            Assert.assertNotNull(getAttributeByName(event.getAttributeSet(), DistributedTracing.NR_TRACE_ID_ATTRIBUTE));
        }

        NewRelic.disableFeature(FeatureFlag.DistributedTracing);
    }

    @Test
    public void noticeHttpTransactionFromAttributes() {
        final long tNow = System.currentTimeMillis();
        final long tLater = tNow + 1000;

        NewRelic.noticeHttpTransaction(new HashMap<String, Object>() {{
            put("url", APP_URL);
            put("httpMethod", "get");
            put("statusCode", String.valueOf(HttpStatus.SC_CREATED));
            put("startTimeMs", String.valueOf(tNow));
            put("endTimeMs", String.valueOf(tLater));
            put("bytesSent", String.valueOf("requestBody".length()));
            put("bytesReceived", String.valueOf("responseBody".length()));
            put("responseBody", "responseBody");
            put("appData", "appData");
            put("carrierType", Agent.getActiveNetworkCarrier());
            put("wanType", Agent.getActiveNetworkWanType());
        }});

        TaskQueue.synchronousDequeue();
        HarvestData harvestData = TestHarvest.getInstance().getHarvestData();
        HttpTransactions transactions = harvestData.getHttpTransactions();

        Assert.assertEquals("Should contain 1 transaction", 1, transactions.count());

        HttpTransaction transaction = transactions.getHttpTransactions().iterator().next();
        Assert.assertEquals("Should contain url", APP_URL, transaction.getUrl());
        Assert.assertEquals("Should contain method", "get", transaction.getHttpMethod());
        Assert.assertEquals("Should contain status code", HttpStatus.SC_CREATED, transaction.getStatusCode());
        Assert.assertEquals("Should contain request time", 1.0, transaction.getTotalTime());
        Assert.assertEquals("Should contain bytes send", "requestBody".length(), transaction.getBytesSent());
        Assert.assertEquals("Should contain bytes received", "responseBody".length(), transaction.getBytesReceived());
        Assert.assertEquals("Should contain response body", "responseBody", transaction.getResponseBody());
        Assert.assertEquals("Should contain app data body", "appData", transaction.getAppData());
        Assert.assertEquals("Should contain carrier type", Agent.getActiveNetworkCarrier(), transaction.getCarrier());
        Assert.assertEquals("Should contain wan type", Agent.getActiveNetworkWanType(), transaction.getWanType());
    }

    @Test
    public void noticeNetworkFailureFromAttributes() {
        final long tNow = System.currentTimeMillis();
        final long tLater = tNow + 1000;
        final NetworkFailure failure = NetworkFailure.exceptionToNetworkFailure(new MalformedURLException("networkFailure"));

        NewRelic.noticeNetworkFailure(new HashMap<String, Object>() {{
            put("url", APP_URL);
            put("httpMethod", "get");
            put("errorCode", String.valueOf(failure.getErrorCode()));
            put("startTimeMs", String.valueOf(tNow));
            put("endTimeMs", String.valueOf(tLater));
            put("message", failure.toString());
        }});

        TaskQueue.synchronousDequeue();
        HarvestData harvestData = TestHarvest.getInstance().getHarvestData();
        HttpTransactions transactions = harvestData.getHttpTransactions();

        Assert.assertEquals("Should contain 1 transaction", 1, transactions.count());
    }

    @Test
    public void testCrashNow() {
        try {
            NewRelic.crashNow();
            Assert.fail("crashNow should throw RuntimeException");
        } catch (RuntimeException e) {
            Assert.assertTrue("Should contain NR courtesy", e.getMessage().toLowerCase().contains("courtesy"));
        }

        try {
            NewRelic.crashNow("Kapow!");
            Assert.fail("crashNow should throw RuntimeException");
        } catch (RuntimeException e) {
            Assert.assertTrue("Should contain use text", e.getMessage().toLowerCase().contains("kapow!"));
        }
    }

    @Test
    public void testSetAttribute() {
        NewRelic.setAttribute("name", "value");
        Set<AnalyticsAttribute> attributes = analyticsController.getUserAttributes();
        AnalyticsAttribute attribute = attributes.iterator().next();
        Assert.assertEquals("Should contain name attribute", "name", attribute.getName());
        Assert.assertEquals("Should contain attribute value", "value", attribute.getStringValue());
    }

    @Test
    public void testIncrementAttribute() {
        NewRelic.setAttribute("name", 1f);
        NewRelic.incrementAttribute("name");
        NewRelic.incrementAttribute("name");
        AnalyticsAttribute attribute = analyticsController.getUserAttributes().iterator().next();
        Assert.assertEquals("Should increment attribute to 3", 3d, attribute.getDoubleValue());

        NewRelic.incrementAttribute("name", 2f);
        attribute = analyticsController.getUserAttributes().iterator().next();
        Assert.assertEquals("Should increment attribute to 5", 5d, attribute.getDoubleValue());

        NewRelic.incrementAttribute("name", -5f);
        attribute = analyticsController.getUserAttributes().iterator().next();
        Assert.assertEquals("Should increment attribute to 0", 0d, attribute.getDoubleValue());
    }

    @Test
    public void testRemoveAttribute() {
        NewRelic.setAttribute("name", 1f);
        Assert.assertEquals("Should contain 1 attribute", 1, analyticsController.getUserAttributes().size());
        NewRelic.removeAttribute("name");
        Assert.assertEquals("Should contain 0 attribute", 0, analyticsController.getUserAttributes().size());
    }

    @Test
    public void testRemoveAllAttributes() {
        NewRelic.setAttribute("attr1", 1);
        NewRelic.setAttribute("attr2", 2);
        Assert.assertEquals("Should contain 2 attribute", 2, analyticsController.getUserAttributes().size());
        NewRelic.removeAllAttributes();
        Assert.assertEquals("Should contain 0 attribute", 0, analyticsController.getUserAttributes().size());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testRecordEvent() {
        final Map<String, Object> attributeMap = new HashMap<String, Object>();
        final String eventName = "Question";
        final String attrName = "MeaningOfEverything";

        attributeMap.put(attrName, 42);

        EventManager eventMgr = analyticsController.getEventManager();
        Collection<AnalyticsEvent> events = eventMgr.getQueuedEvents();
        Assert.assertEquals("Should record 0 event", 0, eventMgr.getEventsRecorded());

        if (events.iterator().hasNext()) {
            AnalyticsEvent event = events.iterator().next();
            Assert.assertEquals("Should set event name", eventName, event.getName());
            Assert.assertEquals("Should set event category", AnalyticsEventCategory.Custom, event.getCategory());

            boolean bFound = false;
            for (AnalyticsAttribute a : event.getAttributeSet()) {
                if (a.getName() == attrName) {
                    bFound = true;
                }
            }
            Assert.assertTrue("Should find attribute", bFound);
        }
    }

    @Test
    public void testSetMaxEventPoolSize() {
        NewRelic.setMaxEventPoolSize(11);
        Assert.assertEquals("Should set max buffer time", EventManagerImpl.DEFAULT_MIN_EVENT_BUFFER_SIZE, analyticsController.getMaxEventPoolSize());
    }

    @Test
    public void testSetMaxEventBufferTime() {
        NewRelic.setMaxEventBufferTime(13);
        Assert.assertEquals("Should set max buffer time", EventManagerImpl.DEFAULT_MIN_EVENT_BUFFER_TIME, analyticsController.getMaxEventBufferTime());
    }

    @Test
    public void testCurrentSessionId() {
        String sessionId = NewRelic.currentSessionId();
        Assert.assertNotNull("Should return non-null session ID", sessionId);
        Assert.assertEquals("Should be a Type-4 generated UUID", sessionId, UUID.fromString(sessionId).toString());
    }

    private Method testDeprecatedMethod(final String methodName, final String modifier, Class<?>... parameterTypes) {
        Method method = null;
        try {
            method = NewRelic.class.getMethod(methodName, parameterTypes);
            Assert.assertNotNull(method);

            Annotation[] annotations = method.getAnnotations();
            Assert.assertTrue(annotations.length > 0);

            method = NewRelic.class.getDeclaredMethod(methodName, parameterTypes);

            // Test the method contains the deprecated annotation
            boolean isDeprecated = false;
            for (Annotation annotation : annotations) {
                isDeprecated = annotation.annotationType().getName().equals("java.lang.Deprecated");
                if (isDeprecated) {
                    break;
                }
            }
            Assert.assertTrue("Should contain @Deprecated annotation", isDeprecated);

            // Test that shutdown is public/private/protected
            String modifiers = Modifier.toString(method.getModifiers());
            Assert.assertTrue("Modifier should be public", modifiers.toLowerCase().contains(modifier));

        } catch (NoSuchMethodException e) {
            // Method has been removed
            Assert.assertNull(method);
        }

        return method;
    }

    @Test
    public void testSetUserId() {
        Assert.assertFalse("Should not set null user ID", NewRelic.setUserId(null));
        Assert.assertFalse("Should not set empty user ID", NewRelic.setUserId(""));
        Assert.assertTrue("Should set valid user ID", NewRelic.setUserId("validUserId"));

        AnalyticsAttribute attr = AnalyticsControllerImpl.getInstance().getAttribute(AnalyticsAttribute.USER_ID_ATTRIBUTE);
        Assert.assertTrue("Should contain 'userId' attr", attr.getName().equals(AnalyticsAttribute.USER_ID_ATTRIBUTE));
        Assert.assertTrue("Should contain 'userId' value", attr.getStringValue().equals("validUserId"));
    }

    @Test
    public void testRecordCustomEvent() {
        String eventType = "MyCustomType";
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(AnalyticsAttribute.SESSION_ID_ATTRIBUTE, "1");

        Assert.assertTrue("A custom event should be recorded",
                NewRelic.recordCustomEvent(eventType, map));
    }

    @Test
    public void testRecordCustomEventWithBadData() {
        String eventType = "MyCustomType";
        Map<String, Object> map = new HashMap<>();
        Map<String, Object> nullMap = null;
        Collection<?> badCollection = new HashSet<Object>();

        Assert.assertTrue("Should create an event with a null map",
                NewRelic.recordCustomEvent(eventType, nullMap));

        map.put(AnalyticsAttribute.CARRIER_ATTRIBUTE, badCollection);
        Assert.assertTrue("Should create event, but filter out attributes that are not a string, float or boolean",
                NewRelic.recordCustomEvent(eventType, map));
    }

    @Test
    public void testRecordCustomEventTypeName() {
        Map<String, Object> map = new HashMap<>();

        Assert.assertTrue("Should create an event with a valid event type name",
                NewRelic.recordCustomEvent("M y C ú s t ó m T y p é : 987 _ _ ", map));

        Assert.assertFalse("Cannot create an event with an invalid event type name",
                NewRelic.recordCustomEvent("M y C ú s t ó m T y p e : @ # $ % ...", map));

        Assert.assertFalse("MobileBreadcrumb is a reserved Event Type",
                NewRelic.recordCustomEvent("MobileBreadcrumb", map));
    }

    @Test
    public void testRecordBreadCrumbWithAttributes() {
        Map<String, Object> breadMap = new HashMap<>();
        breadMap.put("Foo", "bar");
        Collection<?> badCollection = new HashSet<Object>();

        Assert.assertTrue("MobileBreadcrumb event should be recorded", NewRelic.recordBreadcrumb("Name.foo", breadMap));

        breadMap.put(AnalyticsAttribute.MEM_USAGE_MB_ATTRIBUTE, 1);
        breadMap.put(AnalyticsAttribute.CARRIER_ATTRIBUTE, badCollection);
        Assert.assertTrue("Should create event, but filter out attributes that are not a string or float",
                NewRelic.recordBreadcrumb("Foo.bar", breadMap));
    }

    @Test
    public void testHandledException() {
        AgentDataReporter agentDataReporter = AgentDataReporterSpy.initialize(agentConfiguration);
        Assert.assertTrue("Should queue exception for delivery", NewRelic.recordHandledException(new Exception("testException")));
        verify(agentDataReporter).storeAndReportAgentData(any(Payload.class));
    }

    @Test
    public void testHandledExceptionWithAttributes() {
        AgentDataReporter agentDataReporter = AgentDataReporterSpy.initialize(agentConfiguration);

        HashMap<String, Object> exceptionAttributes = new HashMap<>();
        exceptionAttributes.put("module", "junit");
        exceptionAttributes.put("fakedException", true);
        exceptionAttributes.put("numberOfDaysSinceNewJSFramework", 0);
        Assert.assertTrue("Should queue exception with attributes for delivery", NewRelic.recordHandledException(new Exception("testException"), exceptionAttributes));
        verify(agentDataReporter, times(1)).storeAndReportAgentData(any(Payload.class));

        // verify null attribute set also
        Assert.assertTrue("Should queue exception with null attributes for delivery", NewRelic.recordHandledException(new Exception("testException"), null));
        verify(agentDataReporter, times(2)).storeAndReportAgentData(any(Payload.class));
    }

    @Test
    public void testRecordBreadCrumb() {
        Assert.assertTrue("MobileBreadcrumb event should be recorded", NewRelic.recordBreadcrumb("Name.foo"));
    }

    @Test
    public void testRecordNamedCustomEventType() {
        Map<String, Object> map = new HashMap<String, Object>();
        final String testName = "KrazyUnicódeEvént";
        Assert.assertTrue("Should create an event with a valid event type and name",
                NewRelic.recordCustomEvent("M y C ú s t ó m T y p é : 987 _ _ ", testName, map));

        Collection<AnalyticsEvent> events = eventManager.getQueuedEvents();
        AnalyticsEvent event = events.iterator().next();
        Assert.assertEquals(testName, event.getName());
        Assert.assertTrue(event.getAttributeSet().contains(new AnalyticsAttribute(AnalyticsAttribute.EVENT_NAME_ATTRIBUTE, testName)));
    }

    @Test
    public void testDistributedTraceListener() {
        final String visitedAttr = "visited";
        final TraceListener listener = new TraceListener() {
            @Override
            public void onTraceCreated(Map<String, String> requestContext) {
                requestContext.put(visitedAttr, String.valueOf(true));
            }

            @Override
            public void onSpanCreated(Map<String, String> requestContext) {
            }
        };

        TraceContext traceContext;
        FeatureFlag.disableFeature(FeatureFlag.DistributedTracing);
        nrInstance.withDistributedTraceListener(listener);
        traceContext = DistributedTracing.getInstance().startTrace(Providers.provideTransactionState());
        Assert.assertFalse(Boolean.valueOf(traceContext.getRequestContext().get(visitedAttr)));

        FeatureFlag.enableFeature(FeatureFlag.DistributedTracing);
        nrInstance.withDistributedTraceListener(null);
        traceContext = DistributedTracing.getInstance().startTrace(Providers.provideTransactionState());
        Assert.assertFalse(Boolean.valueOf(traceContext.getRequestContext().get(visitedAttr)));

        nrInstance.withDistributedTraceListener(listener);
        traceContext = DistributedTracing.getInstance().startTrace(Providers.provideTransactionState());
        Assert.assertTrue(Boolean.valueOf(traceContext.getRequestContext().get(visitedAttr)));
    }

    @Test
    public void testSetEventListener() throws Exception {
        final AtomicInteger eventsAdded = new AtomicInteger(0);
        final EventListener listener = new EventTransformAdapter() {
            @Override
            public boolean onEventAdded(AnalyticsEvent eventToBeAdded) {
                eventsAdded.incrementAndGet();
                return false;
            }
        };

        NewRelic.setEventListener(listener);
        NewRelic.recordCustomEvent("custom1", "custom", null);
        NewRelic.recordCustomEvent("custom2", "custom", null);
        NewRelic.recordCustomEvent("custom3", "custom", null);

        Assert.assertEquals(3, eventsAdded.get());
    }

    @Test
    public void testSetEventListenerAcrossAppLifecycle() throws Exception {
        final AtomicInteger managerStarted = new AtomicInteger(0);
        final AtomicInteger managerStopped = new AtomicInteger(0);
        final EventListener listener = new EventTransformAdapter() {
            @Override
            public void onStart(EventManager eventManager) {
                super.onStart(eventManager);
                managerStarted.incrementAndGet();
            }

            @Override
            public void onShutdown() {
                super.onShutdown();
                managerStopped.incrementAndGet();
            }
        };

        NewRelic.setEventListener(listener);
        Assert.assertEquals(managerStarted.get(), 0);
        Assert.assertEquals(listener, ((EventManagerImpl) eventManager).getListener());

        // b/g
        eventManager.shutdown();
        Assert.assertEquals(0, managerStarted.get());
        Assert.assertEquals(1, managerStopped.get());
        Assert.assertEquals(listener, ((EventManagerImpl) eventManager).getListener());

        // f/g
        eventManager.initialize(agentConfiguration);
        Assert.assertEquals(1, managerStarted.get());
        Assert.assertEquals(listener, ((EventManagerImpl) eventManager).getListener());

        // b/g
        eventManager.shutdown();
        Assert.assertEquals(2, managerStopped.get());
        Assert.assertEquals(listener, ((EventManagerImpl) eventManager).getListener());
    }

    @Test
    public void testEventTransformer() {
        HashMap<String, String> transforms = new HashMap<String, String>() {{
            put(/*pattern:*/ "^http(s{0,1}):\\/\\/(http).*/(\\d)\\d*", /*replace:*/ "https://httpbin.org/status/418");
        }};
        NetworkEventTransformer transformer = new NetworkEventTransformer(transforms);
        NewRelic.setEventListener(transformer);

        NetworkRequestEvent event = NetworkRequestEvent.createNetworkEvent(Providers.provideHttpTransaction());
        Assert.assertEquals("http://httpstat.us/200",
                getAttributeByName(event.getAttributeSet(), AnalyticsAttribute.REQUEST_URL_ATTRIBUTE).getStringValue());

        EventManager eventMgr = analyticsController.getEventManager();
        eventMgr.addEvent(event);

        Collection<AnalyticsEvent> events = eventMgr.getQueuedEvents();
        Collection<AnalyticsAttribute> attrs = events.iterator().next().getAttributeSet();
        Assert.assertEquals("https://httpbin.org/status/418",
                getAttributeByName(attrs, AnalyticsAttribute.REQUEST_URL_ATTRIBUTE).getStringValue());
    }

    @Test
    public void testWithDeviceID() {
        File f = spyContext.getContext().getFilesDir();
        nrInstance.withDeviceID("baad-f00d");
        Assert.assertEquals("Should set custom device ID", "baad-f00d", agentConfiguration.getDeviceID());

        nrInstance.withDeviceID("baad-f00d.baad-f00d.baad-f00d.baad-f00d.baad-f00d.baad-f00d.baad-f00d.baad-f00d.baad-f00d.");
        Assert.assertEquals("Should trim custom device ID", 40, agentConfiguration.getDeviceID().length());
        Assert.assertTrue(StatsEngine.get().getStatsMap().containsKey(MetricNames.METRIC_UUID_TRUNCATED));

        nrInstance.withDeviceID(null);
        Assert.assertEquals("Should trim custom device ID to a single space", AgentConfiguration.DEFAULT_DEVICE_UUID, agentConfiguration.getDeviceID());

        nrInstance.withDeviceID("       rightAligned");
        Assert.assertEquals("Should trim custom device ID", "rightAligned", agentConfiguration.getDeviceID());

        nrInstance.withDeviceID("       centerAligned      ");
        Assert.assertEquals("Should trim custom device ID", "centerAligned", agentConfiguration.getDeviceID());

        nrInstance.withDeviceID("             ");
        Assert.assertEquals("Should trim custom device ID to a single space", AgentConfiguration.DEFAULT_DEVICE_UUID, agentConfiguration.getDeviceID());
    }


    private static class StubAnalyticsAttributeStore implements AnalyticsAttributeStore {

        @Override
        public boolean store(AnalyticsAttribute attribute) {
            return true;
        }

        @Override
        public List<AnalyticsAttribute> fetchAll() {
            return new ArrayList<AnalyticsAttribute>();
        }

        @Override
        public int count() {
            return 0;
        }

        @Override
        public void clear() {
        }

        @Override
        public void delete(AnalyticsAttribute attribute) {
        }
    }

    private static class TestHarvest extends Harvest {
        public TestHarvest() {
        }

        public HarvestData getHarvestData() {
            return harvestData;
        }
    }

    private class TestCustomMetricConsumer extends CustomMetricConsumer {
        @Override
        protected void addMetric(Metric newMetric) {
            super.addMetric(newMetric);
        }
    }

    private HttpResponse provideHttpResponse() {
        ProtocolVersion proto = new ProtocolVersion("HTTP", 1, 1);
        BasicStatusLine status = new BasicStatusLine(proto, 200, "test");
        return provideCrossProcessHeaderResponse(new BasicHttpResponse(status));
    }

    private HttpResponse provideCrossProcessHeaderResponse(HttpResponse response) {
        response.addHeader(Constants.Network.CROSS_PROCESS_ID_HEADER, "XPROCESS_ID");
        return response;
    }

    private URLConnection provideUrlConnection() {
        URLConnection urlConnection = null;
        try {
            final URL url = new URL(APP_URL);
            urlConnection = url.openConnection();
        } catch (IOException e) {
        }

        urlConnection.setRequestProperty(Constants.Network.CONTENT_TYPE_HEADER, Constants.Network.ContentType.URL_ENCODED);

        return urlConnection;
    }

    static AnalyticsAttribute getAttributeByName(Collection<AnalyticsAttribute> attributes, String name) {
        for (AnalyticsAttribute eventAttr : attributes) {
            if (eventAttr.getName().equalsIgnoreCase(name)) {
                return eventAttr;
            }
        }
        return null;
    }
}
