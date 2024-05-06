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
import com.newrelic.agent.android.background.ApplicationStateMonitor;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
            Assert.assertNotNull(NewRelicTest.getAttributeByName(attributeSet, AnalyticsAttribute.APP_EXIT_PID_ATTRIBUTE));
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
    }

    @Test
    public void userShouldNotCreateCustomAppExitEvents() throws IOException {
        final ApplicationExitInfo exitInfo = provideApplicationExitInfo(ApplicationExitInfo.REASON_ANR);
        final HashMap<String, Object> eventAttributes = new HashMap<>();
        final String traceReport = Streams.slurpString(exitInfo.getTraceInputStream());

        // should these be reserved attribute names for this event?
        eventAttributes.put(AnalyticsAttribute.APP_EXIT_TIMESTAMP_ATTRIBUTE, exitInfo.getTimestamp());
        eventAttributes.put(AnalyticsAttribute.APP_EXIT_PID_ATTRIBUTE, exitInfo.getPid());
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
        final String traceReport = Streams.slurpString(exitInfo.getTraceInputStream());
        final AnalyticsValidator analyticsValidator = new AnalyticsValidator();

        eventAttributes.put(AnalyticsAttribute.APP_EXIT_TIMESTAMP_ATTRIBUTE, exitInfo.getTimestamp());
        eventAttributes.put(AnalyticsAttribute.APP_EXIT_PID_ATTRIBUTE, exitInfo.getPid());
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

    // TODO @Test
    public void parseFullApplicationExitInfoTrace() throws IOException {
        final Pattern pattern = Pattern.compile(ApplicationExitMonitor.REGEX.FULL_REPORT);
        String report = Streams.slurpString(provideApplicationExitInfo(ApplicationExitInfo.REASON_ANR).getTraceInputStream());
        final Matcher matcher = parse(ApplicationExitMonitor.REGEX.FULL_REPORT, report);

        // TODO
    }

    // TODO @Test
    public void parseThreadCount() {
        Matcher matcher = parse(ApplicationExitMonitor.REGEX.THREAD_CNT, "DALVIK THREADS (33):");

        Assert.assertEquals(1, matcher.groupCount());
        Assert.assertNotNull(matcher.group("threadCnt"));
        Assert.assertEquals(33, Integer.valueOf(matcher.group("threadCnt")).intValue());
        logger.info("threadCnt: " + matcher.group("threadCnt"));
    }

    // TODO @Test
    public void parseThreadInfo() {
        final Matcher matcher = parse(ApplicationExitMonitor.REGEX.THREAD_STATE, "\"Profile Saver\" daemon prio=5 tid=18 Native");

        Assert.assertEquals(4, matcher.groupCount());
        Assert.assertNotNull(matcher.group("name"));
        Assert.assertNotNull(matcher.group("priority"));
        Assert.assertNotNull(matcher.group("tid"));
        Assert.assertNotNull(matcher.group("state"));

        logger.info("name: " + matcher.group("name"));
        logger.info("priority: " + matcher.group("priority"));
        logger.info("tid: " + matcher.group("tid"));
        logger.info("state: " + matcher.group("state"));
    }

    // TODO @Test
    public void parseNativeStackFrame() {
        final Matcher matcher = parse(ApplicationExitMonitor.REGEX.NATIVE_STACKFRAME,
                "   native: #00 pc 000000000053a6e0  /apex/com.android.art/lib64/libart.so " +
                        "(art::DumpNativeStack(std::__1::basic_ostream<char, std::__1::char_traits<char> >&, int, BacktraceMap*, char const*, art::ArtMethod*, void*, bool)+128) " +
                        "(BuildId: e24a1818231cfb1649cb83a5d2869598)");

        Assert.assertEquals(6, matcher.groupCount());
        Assert.assertNotNull(matcher.group("frameType"));
        Assert.assertNotNull(matcher.group("frameId"));
        Assert.assertNotNull(matcher.group("pc"));
        Assert.assertNotNull(matcher.group("module"));
        Assert.assertNotNull(matcher.group("method"));
        Assert.assertNotNull(matcher.group("buildId"));

        logger.info("frameType: " + matcher.group("frameType"));
        logger.info("frameId: " + matcher.group("frameId"));
        logger.info("pc: " + matcher.group("pc"));
        logger.info("module: " + matcher.group("module"));
        logger.info("method: " + matcher.group("method"));
        logger.info("buildId: " + matcher.group("buildId"));
    }

    // TODO @Test
    public void parseManagedStackFrame() {
        final Matcher matcher = parse(ApplicationExitMonitor.REGEX.MANAGED_STACKFRAME,
                "\"main\" prio=5 tid=1 Sleeping" +
                        "| group=\"main\" sCount=1 ucsCount=0 flags=1 obj=0x720253c0 self=0xb4000078250e6380" +
                        "| sysTid=4473 nice=-10 cgrp=top-app sched=0/0 handle=0x7963c134f8" +
                        "| state=S schedstat=( 45981599165 5534318422 2710835 ) utm=1467 stm=3130 core=0 HZ=100" +
                        "| stack=0x7fc3403000-0x7fc3405000 stackSize=8188KB" +
                        "| held mutexes=" +
                        "  at jdk.internal.misc.Unsafe.park(Native method) " +
                        "  - waiting on an unknown object " +
                        "  at java.util.concurrent.locks.LockSupport.park(LockSupport.java:194) " +
                        "  at java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject.await(AbstractQueuedSynchronizer.java:2081)" +
                        "  at java.util.concurrent.ScheduledThreadPoolExecutor$DelayedWorkQueue.take(ScheduledThreadPoolExecutor.java:1183)" +
                        "  at java.util.concurrent.ScheduledThreadPoolExecutor$DelayedWorkQueue.take(ScheduledThreadPoolExecutor.java:905)" +
                        "  at java.util.concurrent.ThreadPoolExecutor.getTask(ThreadPoolExecutor.java:1063)" +
                        "  at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1123)" +
                        "  at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:637)" +
                        "  at java.lang.Thread.run(Thread.java:1012)");

        Assert.assertTrue(matcher.find());
        Assert.assertNotEquals(0, matcher.start());
        Assert.assertEquals("2019", matcher.group());
        Assert.assertEquals(12, matcher.end());

        Assert.assertEquals(2, matcher.groupCount());
        Assert.assertNotNull(matcher.group("threadInfo"));
        Assert.assertNotNull(matcher.group("stackTrace"));

        logger.info("frameType: " + matcher.group("threadInfo"));
        logger.info("stackTrace: " + matcher.group("stackTrace"));
    }

    Matcher parse(final String regex, final String target) {
        final Pattern pattern = Pattern.compile(regex, Pattern.DOTALL | Pattern.MULTILINE);
        final Matcher m = pattern.matcher(target);

        Assert.assertTrue(m.lookingAt());

        return m;
    }
}