/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.background;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestApplicationStateMonitor {
    private ApplicationStateMonitorTest asm;
    private StubApplicationStateListener listener;

    @Before
    public void setUp() throws Exception {
        asm = new ApplicationStateMonitorTest();
        listener = new StubApplicationStateListener();
        asm.addApplicationStateListener(listener);
    }

    @Test
    public void ensureTrivialUsageWorks() throws Exception {
        asm.activityStarted();
        asm.activityStopped();

        Thread.sleep(750);

        asm.activityStarted();
        asm.shutdownExecutor();

        //
        // Events are only generated if the ASM determines that the entire application has gone
        // into the background, so we only expect two events here.
        //
        assertEquals(2, listener.getEvents().size());
        assertEquals("background", listener.getEvents().get(0));
        assertEquals("foreground", listener.getEvents().get(1));
    }

    @Test
    public void ensureBackgroundStateWhenUiIsHidden() {
        asm.activityStarted();
        asm.uiHidden();
        asm.shutdownExecutor();

        assertEquals(1, listener.getEvents().size());
        assertEquals("background", listener.getEvents().get(0));
    }

    @Test
    public void ensureForegroundStateWhenActivityIsRestarted() {
        asm.getForgroundState().set(false);
        asm.activityStarted();
        asm.shutdownExecutor();

        assertEquals(1, listener.getEvents().size());
        assertEquals("foreground", listener.getEvents().get(0));
    }

    @Test
    public void shouldManageListeners() {
        assertTrue("Should contain ApplicationStateListener", asm.getListeners().contains(listener));

        asm.removeApplicationStateListener(listener);
        assertTrue("Listeners should be empty", asm.getListeners().isEmpty());
    }

    @Test
    public void shouldNotifyAllListenersWhenInBackground() {
        asm.addApplicationStateListener(new StubApplicationStateListener());
        asm.addApplicationStateListener(new StubApplicationStateListener());
        asm.addApplicationStateListener(new StubApplicationStateListener());
        asm.addApplicationStateListener(new StubApplicationStateListener());
        asm.addApplicationStateListener(new StubApplicationStateListener());

        asm.uiHidden();
        asm.shutdownExecutor();

        for (ApplicationStateListener listener : asm.getListeners()) {
            StubApplicationStateListener stubApplicationStateListener = (StubApplicationStateListener) listener;
            assertEquals(1, stubApplicationStateListener.getEvents().size());
            assertEquals("background", stubApplicationStateListener.getEvents().get(0));
        }
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

        public AtomicBoolean getForgroundState() {
            return foregrounded;
        }
    }
}
