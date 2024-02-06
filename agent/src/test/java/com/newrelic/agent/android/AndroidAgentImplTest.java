/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import static com.newrelic.agent.android.analytics.AnalyticsAttribute.ACTION_TYPE_ATTRIBUTE;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.analytics.AnalyticsEventCategory;
import com.newrelic.agent.android.analytics.AnalyticsEventStore;
import com.newrelic.agent.android.analytics.EventManager;
import com.newrelic.agent.android.background.ApplicationStateEvent;
import com.newrelic.agent.android.background.ApplicationStateMonitor;
import com.newrelic.agent.android.distributedtracing.UserActionType;
import com.newrelic.agent.android.harvest.ApplicationInformation;
import com.newrelic.agent.android.harvest.ConnectInformation;
import com.newrelic.agent.android.harvest.DeviceInformation;
import com.newrelic.agent.android.harvest.EnvironmentInformation;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestAdapter;
import com.newrelic.agent.android.harvest.HarvestTimer;
import com.newrelic.agent.android.harvest.Harvester;
import com.newrelic.agent.android.harvest.MachineMeasurements;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.metric.Metric;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.metric.MetricStore;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.test.mock.Providers;
import com.newrelic.agent.android.tracing.TraceMachine;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class AndroidAgentImplTest {

    private final static String ENABLED_APP_TOKEN_PROD = "AA9a2d52a0ed09d8ca54e6317d9c92074f2e9b307b";

    private SpyContext spyContext;
    private AndroidAgentImpl agentImpl;
    private AgentConfiguration agentConfig;
    private final int duration = 1000;
    private AnalyticsEventStore eventStore;

    @BeforeClass
    public static void classSetUp() throws Exception {
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
    }

    @Before
    public void setUp() throws Exception {
        spyContext = new SpyContext();
        agentConfig = new AgentConfiguration();
        agentConfig.setApplicationToken(ENABLED_APP_TOKEN_PROD);
    }

    @Before
    public void setUpAgent() throws Exception {
        agentImpl = new AndroidAgentImpl(spyContext.getContext(), agentConfig);
        Agent.setImpl(agentImpl);

        ApplicationInformation appInfo = Providers.provideApplicationInformation();
        DeviceInformation devInfo = Providers.provideDeviceInformation();

        // By default, SavedState is created as clone's of the app's ConnectInformation
        SavedState savedState = spy(agentImpl.getSavedState());
        ConnectInformation savedConnInfo = new ConnectInformation(appInfo, devInfo);
        when(savedState.getConnectionToken()).thenReturn(String.valueOf(agentConfig.getApplicationToken().hashCode()));
        when(savedState.getConnectInformation()).thenReturn(savedConnInfo);
        agentImpl.setSavedState(savedState);
        agentImpl.getSavedState().clear();
    }

    @After
    public void tearDown() throws Exception {
        Agent.stop();
    }

    @Test
    public void testUpdateSavedConnectInformation() throws Exception {
        agentStart();

        final ConnectInformation newConnectInformation = new ConnectInformation(
                agentImpl.getApplicationInformation(), agentImpl.getDeviceInformation());
        Assert.assertTrue("New connection info should be equal",
                newConnectInformation.equals(agentImpl.getSavedState().getConnectInformation()));
        Assert.assertFalse("Equal connectionInfo should not update config", agentImpl.updateSavedConnectInformation());
    }

    @Test
    public void testUpdateSavedConnectAppInformation() throws Exception {
        agentStart();
        agentImpl.getApplicationInformation().setAppVersion("1.2");
        Assert.assertFalse("New app info should not be equal",
                agentImpl.getSavedState().getConnectInformation().getApplicationInformation().equals(agentImpl.getDeviceInformation()));
        Assert.assertTrue("Should update connection info on app info change", agentImpl.updateSavedConnectInformation());
    }

    @Test
    public void testUpdateSavedConnectDeviceInformation() throws Exception {
        agentStart();

        agentImpl.getDeviceInformation().setDeviceId("newid");
        Assert.assertFalse("New device info should not be equal",
                agentImpl.getSavedState().getConnectInformation().getDeviceInformation().equals(agentImpl.getDeviceInformation()));
        Assert.assertTrue("Should update connection info on device info change", agentImpl.updateSavedConnectInformation());
    }

    @Test
    public void testShouldGenerateValidSessionDuration() throws Exception {
        class TestHarvestListener extends HarvestAdapter {
            public Metric sessionDurationMetric;

            @Override
            public void onHarvestBefore() {
                // Force StatsEngine to produce its Metrics, which should contain Session/Duration.
                StatsEngine.get().onHarvest();

                // StatsEngine queues its metrics. Run the queue to ensure they have been processed.
                TaskQueue.synchronousDequeue();

                MachineMeasurements metrics = Harvest.getInstance().getHarvestData().getMetrics();
                MetricStore store = metrics.getMetrics();
                sessionDurationMetric = store.get("Session/Duration");
            }
        }

        TestHarvestListener harvestListener = new TestHarvestListener();

        agentStart();
        Thread.sleep(duration);
        Harvest.addHarvestListener(harvestListener);
        Agent.stop();

        Assert.assertNotNull("Should contain duration metric", harvestListener.sessionDurationMetric);
        Assert.assertNotEquals("Duration should be non-zero", 0, harvestListener.sessionDurationMetric.getTotal(), 0);
    }

    @Test
    public void testShouldGenerateMetricsOnUpgrade() throws Exception {
        class TestHarvestListener extends HarvestAdapter {
            public Metric metric;

            @Override
            public void onHarvestBefore() {
                StatsEngine.get().onHarvest();
                TaskQueue.synchronousDequeue();
                MachineMeasurements metrics = Harvest.getInstance().getHarvestData().getMetrics();
                MetricStore store = metrics.getMetrics();
                metric = store.get(MetricNames.MOBILE_APP_UPGRADE);
            }
        }

        TestHarvestListener harvestListener = new TestHarvestListener();

        agentImpl.getApplicationInformation().setAppVersion("2.0");
        agentImpl.getApplicationInformation().setVersionCode(agentImpl.getApplicationInformation().getVersionCode() + 1);

        agentStart();
        Harvest.addHarvestListener(harvestListener);
        Assert.assertTrue("Should update Connection info on app version change", agentImpl.updateSavedConnectInformation());
        Thread.sleep(duration);
        Agent.stop();

        // Check that the upgrade metric exists
        Assert.assertNotNull("Should contain app upgrade metric", harvestListener.metric);
        Assert.assertNotNull("Should contain upgradeFrom attribute",
                AnalyticsControllerImpl.getInstance().getAttribute(AnalyticsAttribute.APP_UPGRADE_ATTRIBUTE));

        // Next, check that the 'upgradeFrom' attribute has been created and is the correct value
        Assert.assertFalse("Should contain upgradeFrom attribute",
                AnalyticsControllerImpl.getInstance().getAttribute(AnalyticsAttribute.APP_UPGRADE_ATTRIBUTE).getStringValue().isEmpty());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void shouldNotPermitAnalyticsIfAgentNotRunning() throws Exception {
        Map<String, Object> attrs = new HashMap<String, Object>();
        attrs.put("string", "String");
        attrs.put("float", 99f);
        attrs.put("boolean", false);

        Assert.assertFalse("Should not allow analytics before agent is running", NewRelic.setAttribute("DeviceId", -1));

        agentStart();
        Thread.sleep(duration);
        Agent.stop();

        Assert.assertFalse("Should not allow setAttribute(String) calls when agent is not running", NewRelic.setAttribute("attrString", "value"));
        Assert.assertFalse("Should not allow setAttribute(float) calls when agent is not running", NewRelic.setAttribute("attrFloat", 1.0f));
        Assert.assertFalse("Should not allow setAttribute(boolean) calls when agent is not running", NewRelic.setAttribute("attrBoolean", true));
        Assert.assertFalse("Should not allow incrementAttribute calls when agent is not running", NewRelic.incrementAttribute("attrIncr"));
        Assert.assertFalse("Should not allow incrementAttribute(float) calls when agent is not running", NewRelic.incrementAttribute("attrIncrFloat", -1.0f));
        Assert.assertFalse("Should not allow removeAttribute() calls when agent is not running", NewRelic.removeAttribute("attr"));
        Assert.assertFalse("Should not allow removeAllAttributes() calls when agent is not running", NewRelic.removeAllAttributes());
    }

    @Test
    public void shouldGenerateNewSessionIdWhenAgentStarted() throws Exception {
        String sessionId = agentConfig.getSessionID();
        Assert.assertNotNull("Should provide default session ID", sessionId);

        agentStart();
        Assert.assertNotEquals("Agent start should provide new session ID", sessionId, agentConfig.getSessionID());
        Agent.stop();

        sessionId = agentConfig.getSessionID();
        agentStart();
        Assert.assertNotEquals("Agent start should provide new session ID", sessionId, agentConfig.getSessionID());
        Agent.stop();
    }

    @Test
    public void shouldGenerateNewSessionIdUIVisibilityChanges() throws Exception {
        String sessionId = agentConfig.getSessionID();
        Assert.assertNotNull("Should provide default session ID", sessionId);

        ApplicationStateMonitor.getInstance().activityStopped();
        Thread.sleep(1000);
        Assert.assertTrue("Agent should be in background", ApplicationStateMonitor.isAppInBackground());

        ApplicationStateMonitor.getInstance().activityStarted();
        Thread.sleep(1000);
        Assert.assertFalse("Agent should be in foreground", ApplicationStateMonitor.isAppInBackground());

        Assert.assertNotEquals("Agent should provide new session ID when foregrounded", sessionId, agentConfig.getSessionID());
    }

    @Test
    public void doNotPreserveStateBetweenSessions() throws Exception {
        Agent.start();
        ApplicationStateMonitor.getInstance().activityStarted();
        Thread.sleep(1000);
        NewRelic.startInteraction("Interaction");
        Thread.sleep(3000);
        Assert.assertTrue("Activity History should contain 1 item", TraceMachine.getActivityHistory().size() == 1);
        Agent.start();
        Assert.assertFalse("Activity History should be empty", TraceMachine.getActivityHistory().size() > 0);
        Agent.stop();
    }

    @Test
    public void testEnvironmentInformation() throws Exception {
        EnvironmentInformation environmentInformation = agentImpl.getEnvironmentInformation();
        Assert.assertEquals(environmentInformation.getNetworkStatus(), agentImpl.getNetworkCarrier());
        Assert.assertEquals(environmentInformation.getNetworkWanType(), agentImpl.getNetworkWanType());
        Assert.assertTrue((environmentInformation.getDiskAvailable()[0] == 0) && (environmentInformation.getDiskAvailable()[1] == 0));
        Assert.assertEquals(environmentInformation.getMemoryUsage(), (int) (SpyContext.APP_MEMORY / 1024));
        Assert.assertEquals(environmentInformation.getOrientation(), 1);
    }

    @Test
    public void testCombinedAgentLifecycleGestures() throws Exception {
        eventStore = agentConfig.getEventStore();
        ApplicationStateMonitor.setInstance(new ApplicationStateMonitor());

        final ApplicationStateEvent e = new ApplicationStateEvent(ApplicationStateMonitor.getInstance());
        final AnalyticsControllerImpl analyticsController = AnalyticsControllerImpl.getInstance();

        agentImpl = new AndroidAgentImpl(spyContext.getContext(), agentConfig);
        Agent.setImpl(agentImpl);

        Harvest.setInstance(new Harvest() {
            @Override
            protected HarvestTimer getHarvestTimer() {
                return new HarvestTimer(new Harvester()) {
                    @Override
                    public void start() {
                        super.start();
                    }
                };
            }
        });

        Collection<AnalyticsEvent> queuedEvents;
        EventManager eventManager = analyticsController.getEventManager();

        // turn the flag on
        FeatureFlag.enableFeature(FeatureFlag.DistributedTracing);

        agentImpl.start();
        assertEquals("Should contain app launch user action event", eventManager.getEventsRecorded(), 1);
        queuedEvents = analyticsController.getEventManager().getQueuedEvents();
        Assert.assertNotNull("Should contain app launch event", getEventByActionType(queuedEvents, UserActionType.AppLaunch));

        // When the agent is backgrounded, a session event is created
        agentImpl.applicationBackgrounded(e);
        eventStore.clear();
        assertEquals("Should contain lifecycle user action events", eventManager.getEventsRecorded(), 3);
        queuedEvents = analyticsController.getEventManager().getQueuedEvents();
        Assert.assertNotNull("Should contain app background event", getEventByActionType(queuedEvents, UserActionType.AppBackground));
        Assert.assertNotNull("Should contain app background event", getSessionEvent(queuedEvents));

        agentImpl.applicationForegrounded(e);
        assertEquals("Should contain foreground user action events", eventManager.getEventsRecorded(), 1);
        queuedEvents = analyticsController.getEventManager().getQueuedEvents();
        Assert.assertNotNull("Should contain foreground (app launch) event", getEventByActionType(queuedEvents, UserActionType.AppLaunch));

        agentImpl.stop();
        assertEquals("Should contain lifecycle user action events", eventManager.getEventsRecorded(), 3);
        queuedEvents = analyticsController.getEventManager().getQueuedEvents();
        Assert.assertNotNull("Should contain app background event", getEventByActionType(queuedEvents, UserActionType.AppBackground));


        // turn the flag back off
        FeatureFlag.disableFeature(FeatureFlag.DistributedTracing);
        eventManager.empty();
        eventStore.clear();

        agentImpl.start();
        assertEquals("Should not contain app launch user action event", eventManager.getEventsRecorded(), 0);
        queuedEvents = analyticsController.getEventManager().getQueuedEvents();
        Assert.assertNull("Should contain app launch event", getEventByActionType(queuedEvents, UserActionType.AppLaunch));

        agentImpl.applicationBackgrounded(e);
        eventStore.clear();
        assertEquals("Should not contain lifecycle user action events", eventManager.getEventsRecorded(), 1);
        queuedEvents = analyticsController.getEventManager().getQueuedEvents();
        Assert.assertNull("Should contain app background event", getEventByActionType(queuedEvents, UserActionType.AppBackground));

        agentImpl.applicationForegrounded(e);
        assertEquals("Should not contain foreground user action events", eventManager.getEventsRecorded(), 0);
        queuedEvents = analyticsController.getEventManager().getQueuedEvents();
        Assert.assertNull("Should contain foreground (app launch) event", getEventByActionType(queuedEvents, UserActionType.AppLaunch));

        agentImpl.stop();
        assertEquals("Should not contain lifecycle user action events", eventManager.getEventsRecorded(), 1);
        queuedEvents = analyticsController.getEventManager().getQueuedEvents();
        Assert.assertNull("Should contain app background event", getEventByActionType(queuedEvents, UserActionType.AppBackground));
    }

    @Test
    public void getUUID() throws Exception {
        agentImpl.deviceInformation = null;
        String uuid = agentImpl.getUUID();
        Assert.assertFalse(uuid.isEmpty());

        agentImpl.deviceInformation = null;
        agentImpl.getSavedState().clear();
        agentConfig.setDeviceID("dead-beef-baad-f00d");
        uuid = agentImpl.getUUID();
        Assert.assertEquals(uuid, "dead-beef-baad-f00d");

        agentImpl.deviceInformation = null;
        agentImpl.getSavedState().clear();
        agentConfig.setDeviceID("");
        uuid = agentImpl.getUUID();
        Assert.assertEquals(AgentConfiguration.DEFAULT_DEVICE_UUID, uuid);

        agentImpl.getSavedState().clear();
        agentConfig.setDeviceID(null);
        uuid = agentImpl.getUUID();
        Assert.assertEquals(AgentConfiguration.DEFAULT_DEVICE_UUID, uuid);

        agentImpl.getSavedState().clear();
        agentConfig.setDeviceID("012345678901234567890123456789012345678901234567890123456789");
        uuid = agentImpl.getUUID();
        Assert.assertEquals(AgentConfiguration.DEVICE_UUID_MAX_LEN, uuid.length());
        Assert.assertTrue(StatsEngine.get().getStatsMap().containsKey(MetricNames.METRIC_UUID_TRUNCATED));
    }

    private void agentStart() throws InterruptedException {
        Agent.start();
        Thread.sleep(1 * 500);
    }

    private static AnalyticsEvent getEventByActionType(Collection<AnalyticsEvent> events, UserActionType actionType) {
        for (AnalyticsEvent event : events) {
            for (AnalyticsAttribute attr : event.getAttributeSet()) {
                if (attr.getName().equalsIgnoreCase(ACTION_TYPE_ATTRIBUTE) && attr.getStringValue().equalsIgnoreCase(actionType.name())) {
                    return event;
                }
            }
        }
        return null;
    }

    private static AnalyticsEvent getSessionEvent(Collection<AnalyticsEvent> events) {
        for (AnalyticsEvent event : events) {
            if (event.getCategory() == AnalyticsEventCategory.Session) {
                return event;
            }
        }
        return null;
    }

}
