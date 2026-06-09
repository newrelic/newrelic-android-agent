package com.newrelic.agent.android.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;

import com.newrelic.agent.android.background.ApplicationStateEvent;
import com.newrelic.agent.android.background.ApplicationStateListener;
import com.newrelic.agent.android.background.ApplicationStateMonitor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(RobolectricTestRunner.class)
public class ActivityLifecycleBackgroundListenerTest {
    private ApplicationStateMonitorTest asm;
    private StubApplicationStateListener listener;
    private ActivityLifecycleBackgroundListener albl;
    private ApplicationStateMonitor defaultInstance;

    @Before
    public void setUp() throws Exception {
        defaultInstance = ApplicationStateMonitor.getInstance();

        asm = new ApplicationStateMonitorTest();
        listener = new StubApplicationStateListener();
        asm.addApplicationStateListener(listener);
        ApplicationStateMonitor.setInstance(asm);

        albl = new ActivityLifecycleBackgroundListener();

        // On mono builds AndroidAgentImpl constructor will start activity
        ApplicationStateMonitorTest.getInstance().activityStarted();
    }

    @After
    public void resetApplicationStateMonitorInstance() throws Exception {
        ApplicationStateMonitor.setInstance(defaultInstance);
    }

    @Test
    public void ensureTrivialUsageWorks() throws Exception {

        assertFalse(ApplicationStateMonitorTest.isAppInBackground());

        albl.onActivityPaused(null);
        albl.onTrimMemory(20);
        albl.onActivityStopped(null);

        Thread.sleep(750);
        assertTrue(ApplicationStateMonitorTest.isAppInBackground());

        albl.onActivityStarted(null);
        albl.onActivityResumed(null);
        Thread.sleep(750);
        asm.shutdownExecutor();

        assertFalse(ApplicationStateMonitorTest.isAppInBackground());

        assertEquals(2, listener.getEvents().size());
        assertEquals("background", listener.getEvents().get(0));
        assertEquals("foreground", listener.getEvents().get(1));
    }

    @Test
    public void ensureBackgroundStateWhenUiIsHidden() throws Exception {
        albl.onActivityStarted(null);
        albl.onActivityPaused(null);
        albl.onTrimMemory(20);
        Thread.sleep(750);
        asm.shutdownExecutor();

        assertEquals(1, listener.getEvents().size());
        assertEquals("background", listener.getEvents().get(0));
    }

    @Test
    public void ensureForegroundStateWhenActivityIsRestarted() throws Exception {
        asm.getForegroundState().set(false);
        assertTrue(ApplicationStateMonitorTest.isAppInBackground());

        albl.onActivityStarted(null);
        albl.onActivityResumed(null);
        Thread.sleep(750);

        assertEquals(1, listener.getEvents().size());
        assertEquals("foreground", listener.getEvents().get(0));
    }

    @Test
    public void rotationDoesNotEmitBackgroundOrForegroundEvents() throws Exception {
        // Initial: foregrounded=true
        assertFalse(ApplicationStateMonitorTest.isAppInBackground());

        Activity oldActivity = Mockito.mock(Activity.class);
        Mockito.when(oldActivity.isChangingConfigurations()).thenReturn(true);

        Activity newActivity = Mockito.mock(Activity.class);
        Mockito.when(newActivity.isChangingConfigurations()).thenReturn(false);

        // Full rotation lifecycle: old paused/stopped/destroyed, new created/started/resumed
        albl.onActivityPaused(oldActivity);
        albl.onActivityStopped(oldActivity);
        albl.onActivityDestroyed(oldActivity);
        albl.onActivityCreated(newActivity, null);
        albl.onActivityStarted(newActivity);
        albl.onActivityResumed(newActivity);

        Thread.sleep(750);
        asm.shutdownExecutor();

        assertEquals("No state events should fire during rotation", 0, listener.getEvents().size());
        assertFalse("App should still be foregrounded after rotation",
                ApplicationStateMonitorTest.isAppInBackground());
    }

    @Test
    public void realBackgroundingStillEmitsEvents() throws Exception {
        // Regression guard: an Activity that is NOT changing configurations
        // continues to drive the existing background/foreground transitions.
        Activity activity = Mockito.mock(Activity.class);
        Mockito.when(activity.isChangingConfigurations()).thenReturn(false);

        albl.onActivityPaused(activity);
        albl.onActivityStopped(activity);
        Thread.sleep(750);
        assertTrue(ApplicationStateMonitorTest.isAppInBackground());

        albl.onActivityStarted(activity);
        albl.onActivityResumed(activity);
        Thread.sleep(750);
        asm.shutdownExecutor();

        assertEquals(2, listener.getEvents().size());
        assertEquals("background", listener.getEvents().get(0));
        assertEquals("foreground", listener.getEvents().get(1));
    }


    private static final class StubApplicationStateListener implements ApplicationStateListener {
        private final ArrayList<String> events = new ArrayList<String>();

        public List<String> getEvents() {
            return events;
        }

        @Override
        public void applicationForegrounded(ApplicationStateEvent e) {
            events.add("foreground");
        }

        @Override
        public void applicationBackgrounded(ApplicationStateEvent e) {
            events.add("background");
        }
    }

    private static class ApplicationStateMonitorTest extends ApplicationStateMonitor {

        public ApplicationStateMonitorTest() {
            super();
        }

        public ArrayList<ApplicationStateListener> getListeners() {
            return applicationStateListeners;
        }

        public void shutdownExecutor() {
            try {
                getExecutor().shutdown();
                getExecutor().awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        public AtomicBoolean getForegroundState() {
            return foregrounded;
        }
    }
}
