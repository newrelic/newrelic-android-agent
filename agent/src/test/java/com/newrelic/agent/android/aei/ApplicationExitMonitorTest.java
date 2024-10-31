/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.aei;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.os.Build;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.NewRelic;
import com.newrelic.agent.android.NewRelicTest;
import com.newrelic.agent.android.NullAgentImpl;
import com.newrelic.agent.android.SpyContext;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.analytics.AnalyticsEventCategory;
import com.newrelic.agent.android.analytics.AnalyticsValidator;
import com.newrelic.agent.android.analytics.ApplicationExitEvent;
import com.newrelic.agent.android.analytics.EventManager;
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
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@RunWith(RobolectricTestRunner.class)
public class ApplicationExitMonitorTest {
    private AgentLog logger;
    private SpyContext spyContext;

    ApplicationExitMonitor applicationExitMonitor;
    ArrayList<ApplicationExitInfo> applicationExitInfoList;
    EventManager eventMgr;

    @Before
    public void setUp() throws Exception {
        logger = Mockito.spy(new ConsoleAgentLog());
        logger.setLevel(AgentLog.DEBUG);
        AgentLogManager.setAgentLog(logger);

        spyContext = new SpyContext();
        applicationExitInfoList = new ArrayList<>();

        AgentConfiguration agentConfig = new AgentConfiguration();
        agentConfig.setEnableAnalyticsEvents(true);
        agentConfig.setAnalyticsAttributeStore(new StubAnalyticsAttributeStore());

        AnalyticsControllerImpl.initialize(agentConfig, new NullAgentImpl());
        eventMgr = AnalyticsControllerImpl.getInstance().getEventManager();

        resetMocks();

        applicationExitInfoList.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_ANR));
        applicationExitInfoList.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_CRASH_NATIVE));
        applicationExitInfoList.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_CRASH));
        applicationExitInfoList.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_ANR));
        applicationExitInfoList.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_INITIALIZATION_FAILURE));
        applicationExitInfoList.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE));

    }

    @After
    public void tearDown() throws Exception {
        AnalyticsControllerImpl.shutdown();
        Streams.list(applicationExitMonitor.reportsDir).forEach(file -> file.delete());
    }

    @Test
    public void harvestApplicationExitInfo() {
        List<File> artifacts = applicationExitMonitor.getArtifacts();
        Assert.assertEquals(0, artifacts.size());

        applicationExitMonitor.harvestApplicationExitInfo();

        artifacts = applicationExitMonitor.getArtifacts();
        Assert.assertEquals(6, artifacts.size());
    }

    @Test
    public void shouldNotHarvestRecordedApplicationExitInfo() {
        List<File> artifacts = applicationExitMonitor.getArtifacts();
        Assert.assertEquals(0, artifacts.size());

        applicationExitMonitor.harvestApplicationExitInfo();

        artifacts = applicationExitMonitor.getArtifacts();
        Assert.assertEquals(6, artifacts.size());
        Assert.assertEquals(6, eventMgr.getEventsRecorded());
        Assert.assertEquals(6, eventMgr.getQueuedEvents().size());

        // call again with same data
        applicationExitMonitor.harvestApplicationExitInfo();

        artifacts = applicationExitMonitor.getArtifacts();
        Assert.assertEquals(6, artifacts.size());
        Assert.assertEquals(6, eventMgr.getEventsRecorded());
        Assert.assertEquals(6, eventMgr.getQueuedEvents().size());
    }

    @Test
    public void createMobileApplicationExitEvents() {
        Mockito.when(applicationExitMonitor.am.getHistoricalProcessExitReasons(spyContext.getContext().getPackageName(), 0, 0)).thenReturn(applicationExitInfoList);

        applicationExitMonitor.harvestApplicationExitInfo();

        Assert.assertEquals(6, eventMgr.getEventsRecorded());
        Collection<AnalyticsEvent> pendingEvents = eventMgr.getQueuedEvents();
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
            Assert.assertNotNull(NewRelicTest.getAttributeByName(attributeSet, AnalyticsAttribute.APP_EXIT_IMPORTANCE_STRING_ATTRIBUTE));
            Assert.assertNotNull(NewRelicTest.getAttributeByName(attributeSet, AnalyticsAttribute.APP_EXIT_PROCESS_NAME_ATTRIBUTE));
        }
    }

    @Config(sdk = {Build.VERSION_CODES.Q})
    @Test
    public void shouldNotCreateEventsForUnsupportedSDK() {
        applicationExitInfoList.clear();
        applicationExitMonitor.harvestApplicationExitInfo();

        Collection<AnalyticsEvent> pendingEvents = eventMgr.getQueuedEvents();
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
        eventAttributes.put(AnalyticsAttribute.APP_EXIT_IMPORTANCE_STRING_ATTRIBUTE, applicationExitMonitor.getImportanceAsString(exitInfo.getImportance()));
        eventAttributes.put(AnalyticsAttribute.APP_EXIT_DESCRIPTION_ATTRIBUTE, applicationExitMonitor.toValidAttributeValue(exitInfo.getDescription()));
        eventAttributes.put(AnalyticsAttribute.APP_EXIT_PROCESS_NAME_ATTRIBUTE, applicationExitMonitor.toValidAttributeValue(exitInfo.getProcessName()));

        NewRelic.recordCustomEvent(AnalyticsEvent.EVENT_TYPE_MOBILE_APPLICATION_EXIT, applicationExitMonitor.packageName, eventAttributes);
        Collection<AnalyticsEvent> pendingEvents = eventMgr.getQueuedEvents();
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
        eventAttributes.put(AnalyticsAttribute.APP_EXIT_IMPORTANCE_STRING_ATTRIBUTE, applicationExitMonitor.getImportanceAsString(exitInfo.getImportance()));
        eventAttributes.put(AnalyticsAttribute.APP_EXIT_DESCRIPTION_ATTRIBUTE, null);
        eventAttributes.put(null, exitInfo.getProcessName());

        Set<AnalyticsAttribute> analyticsAttributes = analyticsValidator.toValidatedAnalyticsAttributes(eventAttributes);
        Assert.assertTrue(eventAttributes.size() > analyticsAttributes.size());
        Mockito.verify(logger, times(2)).warn(anyString());     // null attr names or values

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
    @SuppressWarnings("deprecation")
    public void testApplicationExitImportance() throws IOException {
        Mockito.when(applicationExitMonitor.am.getHistoricalProcessExitReasons(spyContext.getContext().getPackageName(), 0, 0)).thenReturn(applicationExitInfoList);

        applicationExitInfoList.clear();
        applicationExitInfoList.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_ANR, ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND));
        applicationExitInfoList.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_CRASH_NATIVE, ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE));
        applicationExitInfoList.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_LOW_MEMORY, ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE_PRE_26));
        applicationExitInfoList.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_DEPENDENCY_DIED, ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE));
        applicationExitInfoList.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_INITIALIZATION_FAILURE, ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING));
        applicationExitInfoList.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_SIGNALED, ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING_PRE_28));
        applicationExitMonitor.harvestApplicationExitInfo();

        for (AnalyticsEvent event : eventMgr.getQueuedEvents()) {
            Assert.assertEquals(event.getEventType(), AnalyticsEvent.EVENT_TYPE_MOBILE_APPLICATION_EXIT);
            Assert.assertEquals(event.getCategory(), AnalyticsEventCategory.ApplicationExit);
            Assert.assertEquals(event.getName(), applicationExitMonitor.packageName);

            AnalyticsAttribute appStateAttr = NewRelicTest.getAttributeByName(event.getAttributeSet(), AnalyticsAttribute.APP_EXIT_APP_STATE_ATTRIBUTE);
            Assert.assertNotNull(appStateAttr);
            Assert.assertTrue(appStateAttr.valueAsString().equals("foreground"));
        }

        // reset app state, event states
        eventMgr.empty();
        int pid = applicationExitMonitor.getCurrentProcessId();
        applicationExitInfoList.clear();
        applicationExitInfoList.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_CRASH, ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE, pid));
        applicationExitInfoList.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_ANR, ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND, pid));
        applicationExitInfoList.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_SIGNALED, ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED, pid));
        applicationExitMonitor.harvestApplicationExitInfo();

        for (AnalyticsEvent event : eventMgr.getQueuedEvents()) {
            Assert.assertEquals(event.getEventType(), AnalyticsEvent.EVENT_TYPE_MOBILE_APPLICATION_EXIT);
            Assert.assertEquals(event.getCategory(), AnalyticsEventCategory.ApplicationExit);
            Assert.assertEquals(event.getName(), applicationExitMonitor.packageName);

            AnalyticsAttribute appStateAttr = NewRelicTest.getAttributeByName(event.getAttributeSet(), AnalyticsAttribute.APP_EXIT_APP_STATE_ATTRIBUTE);
            Assert.assertNotNull(appStateAttr);
            Assert.assertTrue(appStateAttr.valueAsString().equals("background"));
        }
    }

    @Test
    public void testEnabledStateFromAgentConfiguration() {
        FeatureFlag.enableFeature(FeatureFlag.ApplicationExitReporting);

        AgentConfiguration agentConfiguration = AgentConfiguration.getInstance();
        Assert.assertTrue(agentConfiguration.getApplicationExitConfiguration().isEnabled());

        agentConfiguration.getApplicationExitConfiguration().setEnabled(false);
        Assert.assertFalse(agentConfiguration.getApplicationExitConfiguration().isEnabled());
    }

    @Test
    public void testSupportabilityMetrics() {
        FeatureFlag.enableFeature(FeatureFlag.ApplicationExitReporting);

        applicationExitMonitor.harvestApplicationExitInfo();
        Assert.assertNotNull(StatsEngine.SUPPORTABILITY.getStatsMap());

        for (ApplicationExitInfo aei : applicationExitInfoList) {
            Assert.assertTrue(StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_AEI_EXIT_STATUS + aei.getStatus()));
            Assert.assertTrue(StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_AEI_EXIT_BY_REASON + applicationExitMonitor.getReasonAsString(aei.getReason())));
            Assert.assertTrue(StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_AEI_EXIT_BY_IMPORTANCE + applicationExitMonitor.getImportanceAsString(aei.getImportance())));
        }
    }

    @Test
    public void aeiHarvestShouldTriggerEventHarvest() {
        FeatureFlag.enableFeature(FeatureFlag.ApplicationExitReporting);
        EventManager eventManager = AnalyticsControllerImpl.getInstance().getEventManager();

        applicationExitMonitor.harvestApplicationExitInfo();
        Assert.assertFalse(eventManager.getQueuedEvents().isEmpty());
        Assert.assertTrue(eventManager.isTransmitRequired());

        Assert.assertNotNull(eventManager.getQueuedEvents()); // flush data reset flag
        applicationExitInfoList.clear();
        applicationExitMonitor.harvestApplicationExitInfo();
        Assert.assertFalse(eventMgr.isTransmitRequired());
    }

    @Test
    public void getImportanceStringTest() {
        String str1 = applicationExitMonitor.getImportanceAsString(100);
        Assert.assertEquals("Foreground", str1);

        String str2 = applicationExitMonitor.getImportanceAsString(125);
        Assert.assertEquals("Foreground service", str2);

        String str3 = applicationExitMonitor.getImportanceAsString(325);
        Assert.assertEquals("Top sleeping", str3);

        String str4 = applicationExitMonitor.getImportanceAsString(200);
        Assert.assertEquals("Visible", str4);

        String str5 = applicationExitMonitor.getImportanceAsString(230);
        Assert.assertEquals("Perceptible", str5);

        String str6 = applicationExitMonitor.getImportanceAsString(350);
        Assert.assertEquals("Can't save state", str6);

        String str7 = applicationExitMonitor.getImportanceAsString(300);
        Assert.assertEquals("Service", str7);

        String str8 = applicationExitMonitor.getImportanceAsString(400);
        Assert.assertEquals("Cached", str8);

        String str9 = applicationExitMonitor.getImportanceAsString(1000);
        Assert.assertEquals("Gone", str9);
    }

    @Test
    public void getReasonAsString() {
        Assert.assertEquals("Unknown", applicationExitMonitor.getReasonAsString(ApplicationExitInfo.REASON_UNKNOWN));
        Assert.assertEquals("Exit self", applicationExitMonitor.getReasonAsString(ApplicationExitInfo.REASON_EXIT_SELF));
        Assert.assertEquals("Signaled", applicationExitMonitor.getReasonAsString(ApplicationExitInfo.REASON_SIGNALED));
        Assert.assertEquals("Low memory", applicationExitMonitor.getReasonAsString(ApplicationExitInfo.REASON_LOW_MEMORY));
        Assert.assertEquals("Crash", applicationExitMonitor.getReasonAsString(ApplicationExitInfo.REASON_CRASH));
        Assert.assertEquals("Native crash", applicationExitMonitor.getReasonAsString(ApplicationExitInfo.REASON_CRASH_NATIVE));
        Assert.assertEquals("ANR", applicationExitMonitor.getReasonAsString(ApplicationExitInfo.REASON_ANR));
        Assert.assertEquals("Initialization failure", applicationExitMonitor.getReasonAsString(ApplicationExitInfo.REASON_INITIALIZATION_FAILURE));
        Assert.assertEquals("Permission change", applicationExitMonitor.getReasonAsString(ApplicationExitInfo.REASON_PERMISSION_CHANGE));
        Assert.assertEquals("Excessive resource usage", applicationExitMonitor.getReasonAsString(ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE));
        Assert.assertEquals("User requested", applicationExitMonitor.getReasonAsString(ApplicationExitInfo.REASON_USER_REQUESTED));
        Assert.assertEquals("User stopped", applicationExitMonitor.getReasonAsString(ApplicationExitInfo.REASON_USER_STOPPED));
        Assert.assertEquals("Dependency died", applicationExitMonitor.getReasonAsString(ApplicationExitInfo.REASON_DEPENDENCY_DIED));
        Assert.assertEquals("Other", applicationExitMonitor.getReasonAsString(ApplicationExitInfo.REASON_OTHER));
        Assert.assertEquals("Freezer", applicationExitMonitor.getReasonAsString(14));
        Assert.assertEquals("Package state changed", applicationExitMonitor.getReasonAsString(15));
        Assert.assertEquals("Package updated", applicationExitMonitor.getReasonAsString(16));
    }

    @Test
    public void shouldPersistSessionMapper() throws Exception {
        loadSessionMapper();

        Assert.assertTrue(applicationExitMonitor.sessionMapper.flush());
        File mapper = getSessionMapperFile();
        Assert.assertNotNull(mapper);
        Assert.assertTrue(mapper.exists() && mapper.length() > 0);

        applicationExitMonitor.sessionMapper.delete();
        Assert.assertFalse(mapper.exists());

        applicationExitMonitor.sessionMapper.flush();
        Assert.assertTrue(mapper.exists() && mapper.length() > 0);
    }

    @Test
    public void shouldProvideHistoricSessionId() {
        loadSessionMapper();

        applicationExitMonitor.harvestApplicationExitInfo();

        final Collection<AnalyticsEvent> pendingEvents = eventMgr.getQueuedEvents();
        Assert.assertEquals(applicationExitInfoList.size(), pendingEvents.size());
        Assert.assertEquals(6, pendingEvents.stream()
                .filter(analyticsEvent -> AnalyticsEvent.EVENT_TYPE_MOBILE_APPLICATION_EXIT == analyticsEvent.getEventType())
                .count());

        // this assumes the AEI recs are in the same order as pending events
        Iterator<ApplicationExitInfo> aeiIt = applicationExitInfoList.iterator();
        pendingEvents.forEach(analyticsEvent -> {
            AnalyticsAttribute sessionIdAttr = analyticsEvent.getAttributeSet().stream()
                    .filter(attribute -> attribute.getName().equals(AnalyticsAttribute.SESSION_ID_ATTRIBUTE))
                    .findFirst()
                    .orElse(null);
            Assert.assertNotNull(sessionIdAttr);
            Assert.assertTrue(applicationExitMonitor.sessionMapper.mapper.values().contains(sessionIdAttr.getStringValue()));
            ApplicationExitInfo aei = aeiIt.next();
            Assert.assertTrue(applicationExitMonitor.sessionMapper.get(aei.getPid()).equals(sessionIdAttr.getStringValue()));
        });
    }

    @Test
    public void shouldProvideSessionIdWithEmptyMapper() {
        applicationExitMonitor.sessionMapper.clear();
        applicationExitMonitor.harvestApplicationExitInfo();

        Collection<AnalyticsEvent> pendingEvents = eventMgr.getQueuedEvents();
        Assert.assertNotEquals(0, pendingEvents.size());

        AnalyticsEvent event = pendingEvents.iterator().next();
        Collection<AnalyticsAttribute> attributeSet = event.getAttributeSet();

        Assert.assertNull(attributeSet.stream()
                .filter(attribute -> attribute.getName().equals(AnalyticsAttribute.APP_EXIT_SESSION_ID_ATTRIBUTE))
                .findFirst().orElse(null));

        Assert.assertNull(attributeSet.stream()
                .filter(attribute -> attribute.getName().equals(AnalyticsAttribute.SESSION_ID_ATTRIBUTE))
                .findFirst().orElse(null));

        String currentSessionId = AgentConfiguration.getInstance().getSessionID();
        Assert.assertEquals(currentSessionId, applicationExitMonitor.sessionMapper.getOrDefault(12345, currentSessionId));
    }

    @Test
    public void reconcileMetadata() throws IOException {
        loadSessionMapper();

        // harvest leaves a set of artifacts
        applicationExitMonitor.harvestApplicationExitInfo();
        int artifactsSize = applicationExitMonitor.getArtifacts().size();
        Assert.assertEquals("no artifacts are removed", artifactsSize, applicationExitMonitor.getArtifacts().size());

        // ART deletes a few records
        applicationExitInfoList.remove(2);
        applicationExitInfoList.remove(3);
        applicationExitMonitor.reconcileMetadata(applicationExitInfoList);

        Assert.assertEquals(applicationExitInfoList.size(), applicationExitMonitor.getArtifacts().size());
        Assert.assertTrue(artifactsSize > applicationExitMonitor.getArtifacts().size());
        Assert.assertEquals(artifactsSize - 2, applicationExitMonitor.getArtifacts().size());

        // ART adds a few new records:
        artifactsSize = applicationExitMonitor.getArtifacts().size();

        applicationExitInfoList.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_ANR));
        applicationExitInfoList.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_CRASH));
        applicationExitInfoList.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_USER_STOPPED));
        applicationExitMonitor.harvestApplicationExitInfo();

        Assert.assertEquals(applicationExitInfoList.size(), applicationExitMonitor.getArtifacts().size());
        Assert.assertTrue(artifactsSize < applicationExitMonitor.getArtifacts().size());
        Assert.assertEquals(artifactsSize + 3, applicationExitMonitor.getArtifacts().size());
    }

    @Test
    public void replaceAEISessionId() {
        applicationExitMonitor.harvestApplicationExitInfo();
        List<AnalyticsEvent> aeiEvents = eventMgr.getQueuedEvents().stream()
                .filter(aeiEvent -> aeiEvent.getType().equals(AnalyticsEvent.EVENT_TYPE_MOBILE_APPLICATION_EXIT))
                .collect(Collectors.toList());

        aeiEvents.forEach(aeiEvent -> {
            Collection<AnalyticsAttribute> attrSet = aeiEvent.getMutableAttributeSet();

            Assert.assertFalse(attrSet.stream()
                    .filter(attribute -> attribute.getName().equals(AnalyticsAttribute.APP_EXIT_SESSION_ID_ATTRIBUTE))
                    .findFirst().isPresent());

            Assert.assertTrue(attrSet.stream()
                    .filter(attribute -> attribute.getName().equals(AnalyticsAttribute.SESSION_ID_ATTRIBUTE))
                    .findFirst().isPresent());
        });
    }

    @Test
    public void fullLifecycle() throws Exception {
        // events = eventMgr.getQueuedEvents().stream().filter(aeiEvent -> AnalyticsEvent.EVENT_TYPE_MOBILE_APPLICATION_EXIT == aeiEvent.getEventType()).collect(Collectors.toList());

        // clear session state
        applicationExitMonitor.sessionMapper.delete();
        applicationExitMonitor.sessionMapper.clear();

        Assert.assertEquals(6, applicationExitInfoList.size());
        Assert.assertTrue(applicationExitMonitor.getArtifacts().isEmpty());
        Assert.assertEquals(0, applicationExitMonitor.sessionMapper.size());
        Assert.assertFalse(applicationExitMonitor.sessionMapper.mapStore.exists());

        // session 0: agent is started w/AEI enabled and 6 historic AEI (2 ANR) records reported by ART
        applicationExitMonitor.harvestApplicationExitInfo();
        Assert.assertEquals(1, applicationExitMonitor.sessionMapper.size());
        Assert.assertEquals(6, applicationExitMonitor.getArtifacts().size());
        Assert.assertEquals(6, eventMgr.size());
        Assert.assertEquals(2, eventMgr.getQueuedEvents().stream().filter(aeiEvent -> aeiEvent.getAttributeSet().stream().anyMatch(attribute -> {
            return attribute.getName().equals(AnalyticsAttribute.APP_EXIT_REASON_ATTRIBUTE) && attribute.getDoubleValue() == 6.f;
        })).count());

        // session 1: AEI enabled, no new AEI records
        resetMocks();
        applicationExitMonitor.harvestApplicationExitInfo();
        Assert.assertEquals(2, applicationExitMonitor.sessionMapper.size());
        Assert.assertEquals(6, applicationExitMonitor.getArtifacts().size());
        Assert.assertEquals(0, eventMgr.size());

        // session 2: AEI disabled, no new AEI records
        resetMocks();
        // applicationExitMonitor.harvestApplicationExitInfo(); // not called when disabled
        Assert.assertEquals(2, applicationExitMonitor.sessionMapper.size());
        Assert.assertEquals(6, applicationExitMonitor.getArtifacts().size());
        Assert.assertEquals(0, eventMgr.size());

        // session 3: AEI disabled, 1 new AEI (1 ANR) records
        resetMocks();
        applicationExitInfoList.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_ANR, applicationExitMonitor.getCurrentProcessId()));
        // applicationExitMonitor.harvestApplicationExitInfo(); // not called when disabled
        Assert.assertEquals(2, applicationExitMonitor.sessionMapper.size());
        Assert.assertEquals(6, applicationExitMonitor.getArtifacts().size());
        Assert.assertEquals(0, eventMgr.size());

        // session 4: AEI re-enabled, 1 new (0 ANR) records and 2 removed from ART report
        applicationExitInfoList.remove(2);
        applicationExitInfoList.remove(5);
        applicationExitInfoList.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_CRASH, applicationExitMonitor.getCurrentProcessId()));
        resetMocks();
        applicationExitMonitor.harvestApplicationExitInfo();
        Assert.assertEquals(3, applicationExitMonitor.sessionMapper.size());
        Assert.assertEquals(6, applicationExitMonitor.getArtifacts().size());
        Assert.assertEquals(1, eventMgr.size());
        Assert.assertEquals(0, eventMgr.getQueuedEvents().stream().filter(aeiEvent -> aeiEvent.getAttributeSet().stream().anyMatch(attribute -> {
            return attribute.getName().equals(AnalyticsAttribute.APP_EXIT_REASON_ATTRIBUTE) && attribute.getDoubleValue() == 6.f;
        })).count());

        // session 5: AEI enabled, 1 new ANR record and 3 records removed from ART report
        applicationExitInfoList.remove(1);
        applicationExitInfoList.remove(0);
        applicationExitInfoList.remove(3);
        applicationExitInfoList.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_ANR, applicationExitMonitor.getCurrentProcessId()));
        resetMocks();
        applicationExitMonitor.harvestApplicationExitInfo();
        Assert.assertEquals(4, applicationExitMonitor.sessionMapper.size());
        Assert.assertEquals(4, applicationExitMonitor.getArtifacts().size());
        Assert.assertEquals(1, eventMgr.size());
        Assert.assertEquals(1, eventMgr.getQueuedEvents().stream()
                .filter(aeiEvent -> aeiEvent.getAttributeSet().stream().anyMatch(attribute -> attribute.getName().equals(AnalyticsAttribute.APP_EXIT_REASON_ATTRIBUTE) && attribute.getDoubleValue() == 6.f))
                .filter(aeiEvent -> aeiEvent.getAttributeSet().stream().anyMatch(attribute -> attribute.getName().equals(AnalyticsAttribute.SESSION_ID_ATTRIBUTE)))
                .count());

        // session 6: AEI enabled, ALL records removed from ART report
        Assert.assertEquals(4, applicationExitMonitor.sessionMapper.size());
        Assert.assertEquals(4, applicationExitMonitor.getArtifacts().size());
        applicationExitInfoList.clear();
        resetMocks();
        applicationExitMonitor.harvestApplicationExitInfo();
        Assert.assertEquals(4, applicationExitMonitor.sessionMapper.size());
        Assert.assertEquals(0, applicationExitMonitor.getArtifacts().size());
        Assert.assertEquals(0, eventMgr.size());

        // session 7: AEI enabled, 1 new ANR record
        Assert.assertEquals(4, applicationExitMonitor.sessionMapper.size());
        Assert.assertEquals(0, applicationExitMonitor.getArtifacts().size());
        applicationExitInfoList.add(provideApplicationExitInfo(ApplicationExitInfo.REASON_ANR, applicationExitMonitor.getCurrentProcessId()));
        resetMocks();
        applicationExitMonitor.harvestApplicationExitInfo();
        Assert.assertEquals(5, applicationExitMonitor.sessionMapper.size());
        Assert.assertEquals(1, applicationExitMonitor.getArtifacts().size());
        Assert.assertEquals(1, eventMgr.size());
        Assert.assertEquals(1, eventMgr.getQueuedEvents().stream()
                .filter(aeiEvent -> aeiEvent.getAttributeSet().stream().anyMatch(attribute -> attribute.getName().equals(AnalyticsAttribute.APP_EXIT_REASON_ATTRIBUTE) && attribute.getDoubleValue() == 6.f))
                .filter(aeiEvent -> aeiEvent.getAttributeSet().stream().anyMatch(attribute -> attribute.getName().equals(AnalyticsAttribute.SESSION_ID_ATTRIBUTE)))
                .count());

        // session 8: AEI enabled and no new AEI records
        resetMocks();
        applicationExitMonitor.harvestApplicationExitInfo();
        Assert.assertEquals(6, applicationExitMonitor.sessionMapper.size());
        Assert.assertEquals(1, applicationExitMonitor.getArtifacts().size());
        Assert.assertEquals(0, eventMgr.size());
    }


    static AtomicInteger pidCtr = new AtomicInteger(5000);

    void resetMocks() {
        applicationExitMonitor = Mockito.spy(new ApplicationExitMonitor(spyContext.getContext()));
        applicationExitMonitor.reportsDir.deleteOnExit();

        Mockito.when(applicationExitMonitor.getCurrentProcessId()).thenReturn(pidCtr.incrementAndGet());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Only supported in Android 11+
            Mockito.when(applicationExitMonitor.am.getHistoricalProcessExitReasons(spyContext.getContext().getPackageName(), 0, 0)).thenReturn(applicationExitInfoList);
        }

        eventMgr.empty();
        AgentConfiguration.getInstance().provideSessionId();
    }

    private ApplicationExitInfo provideApplicationExitInfo(int reasonCode) throws IOException {
        ApplicationExitInfo applicationExitInfo = provideApplicationExitInfo(reasonCode, ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND, pidCtr.incrementAndGet());

        return applicationExitInfo;
    }

    private ApplicationExitInfo provideApplicationExitInfo(int reasonCode, int pid) throws IOException {
        ApplicationExitInfo applicationExitInfo = provideApplicationExitInfo(reasonCode, ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND, pid);

        return applicationExitInfo;
    }

    private ApplicationExitInfo provideApplicationExitInfo(int reasonCode, int importance, int pid) throws IOException {
        ApplicationExitInfo applicationExitInfo = Mockito.mock(ApplicationExitInfo.class);

        Mockito.when(applicationExitInfo.getReason()).thenReturn(reasonCode);
        Mockito.when(applicationExitInfo.getDescription()).thenReturn("user request after error: Input dispatching timed out (adf8e62 com.newrelic.android.test.ApplicationExitMonitor/com.newrelic.android.test.ApplicationExitMonitor.MainActivity (server) is not responding. Waited 5005ms for MotionEvent)");
        Mockito.when(applicationExitInfo.getTraceInputStream()).thenReturn(ApplicationExitMonitor.class.getResource("/applicationExitInfo/ApplicationExitInfo.trace").openStream());
        Mockito.when(applicationExitInfo.getRealUid()).thenReturn(667);     // the neighbor of the beast
        Mockito.when(applicationExitInfo.getPackageUid()).thenReturn(69);
        Mockito.when(applicationExitInfo.getDefiningUid()).thenReturn(42);
        Mockito.when(applicationExitInfo.getTimestamp()).thenReturn(System.currentTimeMillis());
        Mockito.when(applicationExitInfo.getImportance()).thenReturn(importance);

        Mockito.when(applicationExitInfo.getPid()).thenReturn(pid);

        return applicationExitInfo;
    }

    private File getSessionMapperFile() {
        return Streams.list(applicationExitMonitor.reportsDir)
                .filter(file -> file.isFile() && file.getName().equals(ApplicationExitMonitor.SESSION_ID_MAPPING_STORE)).
                findFirst().orElse(null);
    }

    void loadSessionMapper() {
        // seed the session mapper
        applicationExitInfoList.forEach(aei ->
                applicationExitMonitor.sessionMapper.mapper.putIfAbsent(aei.getPid(), UUID.randomUUID().toString())
        );
        applicationExitMonitor.sessionMapper.flush();
    }

    int getRandomHistoricalPid() {
        List<Integer> pids = new ArrayList<>(applicationExitMonitor.sessionMapper.mapper.keySet());
        int rando = (int) (Math.random() * pids.size());

        return Integer.parseInt(String.valueOf(pids.get(rando)));
    }
}