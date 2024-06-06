/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import static android.os.Build.VERSION_CODES.Q;
import static com.newrelic.agent.android.analytics.AnalyticsEvent.EVENT_TYPE_MOBILE_APPLICATION_EXIT;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.os.Build;

import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.analytics.AnalyticsEventCategory;
import com.newrelic.agent.android.analytics.AnalyticsValidator;
import com.newrelic.agent.android.analytics.ApplicationExitEvent;
import com.newrelic.agent.android.analytics.EventManager;
import com.newrelic.agent.android.background.ApplicationStateMonitor;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.test.stub.StubAnalyticsAttributeStore;
import com.newrelic.agent.android.util.Streams;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RunWith(RobolectricTestRunner.class)
public class ApplicationExitMonitorTest {
    private AgentLog logger;
    private SpyContext spyContext;

    ApplicationExitMonitor applicationExitMonitor;
    List<ApplicationExitInfo> applicationExitInfos;

    @Before
    public void setUp() throws Exception {
        logger = Mockito.spy(new ConsoleAgentLog());
        AgentLogManager.setAgentLog(logger);

        spyContext = new SpyContext();
        applicationExitMonitor = new ApplicationExitMonitor(spyContext.getContext());
        applicationExitInfos = new ArrayList<>();

        applicationExitInfos.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_ANR));
        applicationExitInfos.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_CRASH_NATIVE));
        applicationExitInfos.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_CRASH));
        applicationExitInfos.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_ANR));
        applicationExitInfos.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_INITIALIZATION_FAILURE));
        applicationExitInfos.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Mockito.when(applicationExitMonitor.am.getHistoricalProcessExitReasons(spyContext.getContext().getPackageName(), 0, 0)).thenReturn(applicationExitInfos);
        }

        ApplicationStateMonitor.setInstance(new ApplicationStateMonitor());

        AgentConfiguration agentConfig = new AgentConfiguration();
        agentConfig.setEnableAnalyticsEvents(true);
        agentConfig.setAnalyticsAttributeStore(new StubAnalyticsAttributeStore());

        AnalyticsControllerImpl.initialize(agentConfig, new NullAgentImpl());
    }

    @After
    public void tearDown() throws Exception {
        AnalyticsControllerImpl.shutdown();
        Streams.list(applicationExitMonitor.reportsDir).forEach(file -> {
            Assert.assertTrue(file.delete());
        });
        FeatureFlag.disableFeature(FeatureFlag.ApplicationExitReporting);
    }

    @Test
    public void harvestApplicationExitInfo() throws InterruptedException {
        List<File> artifacts = Streams.list(applicationExitMonitor.reportsDir).collect(Collectors.toList());
        Assert.assertEquals(0, artifacts.size());

        applicationExitMonitor.harvestApplicationExitInfo();
        ApplicationStateMonitor.getInstance().getExecutor().shutdown();
        ApplicationStateMonitor.getInstance().getExecutor().awaitTermination(3, TimeUnit.SECONDS);

        artifacts = Streams.list(applicationExitMonitor.reportsDir).collect(Collectors.toList());
        Assert.assertEquals(6, artifacts.size());
    }

    @Test
    public void shouldNotHarvestRecordedApplicationExitInfo() throws InterruptedException {
        List<File> artifacts = Streams.list(applicationExitMonitor.reportsDir).collect(Collectors.toList());
        Assert.assertEquals(0, artifacts.size());

        applicationExitMonitor.harvestApplicationExitInfo();
        ApplicationStateMonitor.getInstance().getExecutor().shutdown();
        ApplicationStateMonitor.getInstance().getExecutor().awaitTermination(3, TimeUnit.SECONDS);

        artifacts = Streams.list(applicationExitMonitor.reportsDir).collect(Collectors.toList());
        Assert.assertEquals(6, artifacts.size());
        Assert.assertEquals(6, AnalyticsControllerImpl.getInstance().getEventManager().getEventsRecorded());
        Assert.assertEquals(6, AnalyticsControllerImpl.getInstance().getEventManager().getQueuedEvents().size());

        // call again with same data
        ApplicationStateMonitor.setInstance(new ApplicationStateMonitor());
        applicationExitMonitor.harvestApplicationExitInfo();
        ApplicationStateMonitor.getInstance().getExecutor().shutdown();
        ApplicationStateMonitor.getInstance().getExecutor().awaitTermination(3, TimeUnit.SECONDS);

        artifacts = Streams.list(applicationExitMonitor.reportsDir).collect(Collectors.toList());
        Assert.assertEquals(6, artifacts.size());
        Assert.assertEquals(6, AnalyticsControllerImpl.getInstance().getEventManager().getEventsRecorded());
        Assert.assertEquals(6, AnalyticsControllerImpl.getInstance().getEventManager().getQueuedEvents().size());
    }

    @Test
    public void createMobileApplicationExitEvents() throws InterruptedException {
        Mockito.when(applicationExitMonitor.am.getHistoricalProcessExitReasons(spyContext.getContext().getPackageName(), 0, 0)).thenReturn(applicationExitInfos);

        applicationExitMonitor.harvestApplicationExitInfo();
        ApplicationStateMonitor.getInstance().getExecutor().shutdown();
        ApplicationStateMonitor.getInstance().getExecutor().awaitTermination(3, TimeUnit.SECONDS);

        Assert.assertEquals(6, AnalyticsControllerImpl.getInstance().getEventManager().getEventsRecorded());
        Collection<AnalyticsEvent> pendingEvents = AnalyticsControllerImpl.getInstance().getEventManager().getQueuedEvents();
        Assert.assertEquals(6, pendingEvents.size());
        for (AnalyticsEvent event : pendingEvents) {
            Assert.assertEquals(event.getEventType(), AnalyticsEvent.EVENT_TYPE_MOBILE_APPLICATION_EXIT);
            Assert.assertEquals(event.getCategory(), AnalyticsEventCategory.ApplicationExit);
            Assert.assertEquals(event.getName(), applicationExitMonitor.packageName);

            Collection<AnalyticsAttribute> attributeSet = event.getAttributeSet();
            Assert.assertTrue(attributeSet.size() > 7);
            Assert.assertNotNull(NewRelicTest.getAttributeByName(attributeSet, AnalyticsAttribute.APP_EXIT_TIMESTAMP_ATTRIBUTE));
            Assert.assertNotNull(NewRelicTest.getAttributeByName(attributeSet, AnalyticsAttribute.APP_EXIT_DESCRIPTION_ATTRIBUTE));
            Assert.assertNotNull(NewRelicTest.getAttributeByName(attributeSet, AnalyticsAttribute.APP_EXIT_REASON_ATTRIBUTE));
            Assert.assertNotNull(NewRelicTest.getAttributeByName(attributeSet, AnalyticsAttribute.APP_EXIT_IMPORTANCE_ATTRIBUTE));
            Assert.assertNotNull(NewRelicTest.getAttributeByName(attributeSet, AnalyticsAttribute.APP_EXIT_PROCESS_NAME_ATTRIBUTE));
        }
    }

    @Config(sdk = {Q})
    @Test
    public void shouldNotCreateEventsForUnsupportSDK() throws InterruptedException {
        applicationExitInfos.clear();
        applicationExitMonitor.harvestApplicationExitInfo();

        ApplicationStateMonitor.getInstance().getExecutor().shutdown();
        ApplicationStateMonitor.getInstance().getExecutor().awaitTermination(3, TimeUnit.SECONDS);

        Collection<AnalyticsEvent> pendingEvents = AnalyticsControllerImpl.getInstance().getEventManager().getQueuedEvents();
        Assert.assertEquals("Should not create AppExit events for Android 10 and below", 0, pendingEvents.size());

        Mockito.verify(logger, times(1)).warn(anyString());

        Assert.assertTrue(StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_AEI_UNSUPPORTED_OS + Build.VERSION.SDK_INT));
    }

    @Test
    public void userShouldNotCreateCustomAppExitEvents() throws IOException {
        final ApplicationExitInfo exitInfo = provideApplicationExitInfo(ApplicationExitInfo.REASON_ANR);
        final HashMap<String, Object> eventAttributes = new HashMap<>();

        eventAttributes.put(AnalyticsAttribute.APP_EXIT_TIMESTAMP_ATTRIBUTE, exitInfo.getTimestamp());
        eventAttributes.put(AnalyticsAttribute.APP_EXIT_REASON_ATTRIBUTE, exitInfo.getReason());
        eventAttributes.put(AnalyticsAttribute.APP_EXIT_IMPORTANCE_ATTRIBUTE, exitInfo.getImportance());
        eventAttributes.put(AnalyticsAttribute.APP_EXIT_DESCRIPTION_ATTRIBUTE, applicationExitMonitor.toValidAttributeValue(exitInfo.getDescription()));
        eventAttributes.put(AnalyticsAttribute.APP_EXIT_PROCESS_NAME_ATTRIBUTE, applicationExitMonitor.toValidAttributeValue(exitInfo.getProcessName()));

        NewRelic.recordCustomEvent(EVENT_TYPE_MOBILE_APPLICATION_EXIT, applicationExitMonitor.packageName, eventAttributes);
        Collection<AnalyticsEvent> pendingEvents = AnalyticsControllerImpl.getInstance().getEventManager().getQueuedEvents();
        Assert.assertTrue("Should not create AppExit events as custom events", pendingEvents.isEmpty());
    }

    @Test
    public void validateApplicationExitEvent() throws IOException {
        final ApplicationExitInfo exitInfo = provideApplicationExitInfo(ApplicationExitInfo.REASON_ANR);
        final HashMap<String, Object> eventAttributes = new HashMap<>();
        final AnalyticsValidator analyticsValidator = new AnalyticsValidator();

        eventAttributes.put(AnalyticsAttribute.APP_EXIT_TIMESTAMP_ATTRIBUTE, exitInfo.getTimestamp());
        eventAttributes.put(AnalyticsAttribute.APP_EXIT_REASON_ATTRIBUTE, exitInfo.getReason());
        eventAttributes.put(AnalyticsAttribute.APP_EXIT_IMPORTANCE_ATTRIBUTE, exitInfo.getImportance());
        eventAttributes.put(AnalyticsAttribute.APP_EXIT_DESCRIPTION_ATTRIBUTE, null);
        eventAttributes.put(null, exitInfo.getProcessName());

        Set<AnalyticsAttribute> analyticsAttributes = analyticsValidator.toValidatedAnalyticsAttributes(eventAttributes);
        Assert.assertTrue(eventAttributes.size() > analyticsAttributes.size());
        Mockito.verify(logger, times(3)).warn(anyString());     // null attr names or values

        Assert.assertTrue(analyticsValidator.isValidEventName(applicationExitMonitor.packageName));
        Assert.assertTrue(analyticsValidator.isValidEventType(AnalyticsEvent.EVENT_TYPE_MOBILE_APPLICATION_EXIT));
        Assert.assertTrue(analyticsValidator.isReservedEventType(AnalyticsEvent.EVENT_TYPE_MOBILE_APPLICATION_EXIT));

        ApplicationExitEvent appExitEvent = new ApplicationExitEvent(applicationExitMonitor.packageName,
                analyticsValidator.toValidatedAnalyticsAttributes(eventAttributes));

        Assert.assertTrue(appExitEvent.isValid());
        Assert.assertEquals(AnalyticsEvent.EVENT_TYPE_MOBILE_APPLICATION_EXIT, analyticsValidator.toValidEventType(appExitEvent.getEventType()));
        Assert.assertNotEquals(AnalyticsEventCategory.Custom, analyticsValidator.toValidCategory(appExitEvent.getCategory()));
    }

    @Test
    public void testApplicationExitImportance() throws InterruptedException, IOException {
        Mockito.when(applicationExitMonitor.am.getHistoricalProcessExitReasons(spyContext.getContext().getPackageName(), 0, 0)).thenReturn(applicationExitInfos);

        applicationExitInfos.clear();
        applicationExitInfos.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_ANR, ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND));
        applicationExitInfos.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_CRASH_NATIVE, ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE));
        applicationExitInfos.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_LOW_MEMORY, ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE_PRE_26));
        applicationExitInfos.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_DEPENDENCY_DIED, ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE));
        applicationExitInfos.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_INITIALIZATION_FAILURE, ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING));
        applicationExitInfos.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_SIGNALED, ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING_PRE_28));
        applicationExitMonitor.harvestApplicationExitInfo();
        ApplicationStateMonitor.getInstance().getExecutor().shutdown();
        ApplicationStateMonitor.getInstance().getExecutor().awaitTermination(3, TimeUnit.SECONDS);

        Collection<AnalyticsEvent> pendingEvents = AnalyticsControllerImpl.getInstance().getEventManager().getQueuedEvents();
        for (AnalyticsEvent event : AnalyticsControllerImpl.getInstance().getEventManager().getQueuedEvents()) {
            Assert.assertEquals(event.getEventType(), AnalyticsEvent.EVENT_TYPE_MOBILE_APPLICATION_EXIT);
            Assert.assertEquals(event.getCategory(), AnalyticsEventCategory.ApplicationExit);
            Assert.assertEquals(event.getName(), applicationExitMonitor.packageName);

            AnalyticsAttribute appStateAttr = NewRelicTest.getAttributeByName(event.getAttributeSet(), AnalyticsAttribute.APP_EXIT_APP_STATE_ATTRIBUTE);
            Assert.assertNotNull(appStateAttr);
            Assert.assertTrue(appStateAttr.valueAsString().equals("foreground"));
        }

        // reset app state, event states
        ApplicationStateMonitor.setInstance(new ApplicationStateMonitor());
        AnalyticsControllerImpl.getInstance().getEventManager().empty();

        applicationExitInfos.clear();
        applicationExitInfos.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_CRASH, ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE));
        applicationExitInfos.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_ANR, ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND));
        applicationExitInfos.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_SIGNALED, ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED));
        applicationExitMonitor.harvestApplicationExitInfo();
        ApplicationStateMonitor.getInstance().getExecutor().shutdown();
        ApplicationStateMonitor.getInstance().getExecutor().awaitTermination(3, TimeUnit.SECONDS);

        for (AnalyticsEvent event : AnalyticsControllerImpl.getInstance().getEventManager().getQueuedEvents()) {
            Assert.assertEquals(event.getEventType(), AnalyticsEvent.EVENT_TYPE_MOBILE_APPLICATION_EXIT);
            Assert.assertEquals(event.getCategory(), AnalyticsEventCategory.ApplicationExit);
            Assert.assertEquals(event.getName(), applicationExitMonitor.packageName);

            AnalyticsAttribute appStateAttr = NewRelicTest.getAttributeByName(event.getAttributeSet(), AnalyticsAttribute.APP_EXIT_APP_STATE_ATTRIBUTE);
            Assert.assertNotNull(appStateAttr);
            Assert.assertTrue(appStateAttr.valueAsString().equals("background"));
        }
    }

    @Test
    public void testEnabledStateFromAgentConfiguration() throws InterruptedException {
        FeatureFlag.enableFeature(FeatureFlag.ApplicationExitReporting);

        AgentConfiguration agentConfiguration = AgentConfiguration.instance.get();
        Assert.assertTrue(agentConfiguration.getApplicationExitConfiguration().isEnabled());

        agentConfiguration.getApplicationExitConfiguration().enabled = false;
        Assert.assertFalse(agentConfiguration.getApplicationExitConfiguration().isEnabled());
    }

    @Test
    public void testSupportabilityMetrics() throws InterruptedException {
        FeatureFlag.enableFeature(FeatureFlag.ApplicationExitReporting);

        applicationExitMonitor.harvestApplicationExitInfo();
        ApplicationStateMonitor.getInstance().getExecutor().shutdown();
        ApplicationStateMonitor.getInstance().getExecutor().awaitTermination(3, TimeUnit.SECONDS);

        Assert.assertNotNull(StatsEngine.SUPPORTABILITY.getStatsMap());
        for (ApplicationExitInfo aei : applicationExitInfos) {
            Assert.assertTrue(StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_AEI_EXIT_STATUS + aei.getStatus()));
            Assert.assertTrue(StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_AEI_EXIT_BY_REASON + aei.getReason()));
            Assert.assertTrue(StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_AEI_EXIT_BY_IMPORTANCE + aei.getImportance()));
        }

        applicationExitInfos.stream().collect(Collectors.toSet());
    }

    @Test
    public void aeiHarvestShouldTriggerEventHarvest() throws InterruptedException {
        FeatureFlag.enableFeature(FeatureFlag.ApplicationExitReporting);
        EventManager eventManager = AnalyticsControllerImpl.getInstance().getEventManager();

        applicationExitMonitor.harvestApplicationExitInfo();
        ApplicationStateMonitor.getInstance().getExecutor().shutdown();
        ApplicationStateMonitor.getInstance().getExecutor().awaitTermination(1, TimeUnit.SECONDS);
        Assert.assertFalse(eventManager.getQueuedEvents().isEmpty());
        Assert.assertTrue(eventManager.isTransmitRequired());

        Assert.assertNotNull(eventManager.getQueuedEvents()); // flush data reset flag
        ApplicationStateMonitor.setInstance(new ApplicationStateMonitor());
        applicationExitInfos.clear();
        applicationExitMonitor.harvestApplicationExitInfo();
        ApplicationStateMonitor.getInstance().getExecutor().shutdown();
        ApplicationStateMonitor.getInstance().getExecutor().awaitTermination(1, TimeUnit.SECONDS);
        Assert.assertFalse(AnalyticsControllerImpl.getInstance().getEventManager().isTransmitRequired());
    }

    private ApplicationExitInfo provideApplicationExitInfo(int reasonCode) throws IOException {
        return provideApplicationExitInfo(reasonCode, ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);
    }

    private ApplicationExitInfo provideApplicationExitInfo(int reasonCode, int importance) throws IOException {
        ApplicationExitInfo applicationExitInfo = Mockito.mock(ApplicationExitInfo.class);

        Mockito.when(applicationExitInfo.getReason()).thenReturn(reasonCode);
        Mockito.when(applicationExitInfo.getPid()).thenReturn((int) (Math.random() * 9999) + 1);
        Mockito.when(applicationExitInfo.getDescription()).thenReturn("user request after error: Input dispatching timed out (adf8e62 com.newrelic.android.test.ApplicationExitMonitor/com.newrelic.android.test.ApplicationExitMonitor.MainActivity (server) is not responding. Waited 5005ms for MotionEvent)");
        Mockito.when(applicationExitInfo.getTraceInputStream()).thenReturn(ApplicationExitMonitor.class.getResource("/ApplicationExitInfo.trace").openStream());
        Mockito.when(applicationExitInfo.getRealUid()).thenReturn(667);     // the neighbor of the beast
        Mockito.when(applicationExitInfo.getPackageUid()).thenReturn(69);
        Mockito.when(applicationExitInfo.getDefiningUid()).thenReturn(42);
        Mockito.when(applicationExitInfo.getTimestamp()).thenReturn(System.currentTimeMillis());
        Mockito.when(applicationExitInfo.getImportance()).thenReturn(importance);

        return applicationExitInfo;
    }

}