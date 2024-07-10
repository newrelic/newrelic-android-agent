/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.background;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.util.NamedThreadFactory;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;


public class ApplicationStateMonitor {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    protected final ExecutorService executor;

    protected final ArrayList<ApplicationStateListener> applicationStateListeners = new ArrayList<ApplicationStateListener>();

    protected AtomicBoolean foregrounded = new AtomicBoolean(true);

    private static ApplicationStateMonitor instance = null;
    private AtomicLong activityCount = new AtomicLong(0);

    public ApplicationStateMonitor() {
        executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("AppStateMon"));
        log.info("Application state monitor has started");
    }

    public static ApplicationStateMonitor getInstance() {
        if (ApplicationStateMonitor.instance == null) {
            setInstance(new ApplicationStateMonitor());
        }
        return ApplicationStateMonitor.instance;
    }

    public static void setInstance(ApplicationStateMonitor instance) {
        ApplicationStateMonitor.instance = instance;
    }

    public void addApplicationStateListener(ApplicationStateListener listener) {
        synchronized (applicationStateListeners) {
                applicationStateListeners.add(listener);
            }
        }

    public void removeApplicationStateListener(ApplicationStateListener listener) {
        synchronized (applicationStateListeners) {
            applicationStateListeners.remove(listener);
        }
    }

    public void uiHidden() {
        final Runnable runner = () -> {
            if (foregrounded.get()) {
                log.info("UI has become hidden (app backgrounded)");
                notifyApplicationInBackground();
                foregrounded.set(false);
            }
        };
        executor.execute(runner);
    }

    public void activityStopped() {
        final Runnable runner = new Runnable() {
            @Override
            public void run() {
                if (activityCount.decrementAndGet() == 0) {
                    //application in background
                    uiHidden();
                }
            }
        };
        executor.execute(runner);
    }

    public void activityStarted() {
        log.info("Activity appears to have started");
        log.info("Activity count: " + activityCount.get());
        final Runnable runner = () -> {
            if (!foregrounded.get()) {
                foregrounded.set(true);
                notifyApplicationInForeground();
            }
        };
        log.verbose("Activity count: " + activityCount.get());
        executor.execute(runner);
    }

    private void notifyApplicationInBackground() {
        log.verbose("Application appears to have gone to the background");
        final ArrayList<ApplicationStateListener> listeners;
        synchronized (applicationStateListeners) {
            listeners = new ArrayList<ApplicationStateListener>(applicationStateListeners);
        }
        final ApplicationStateEvent e = new ApplicationStateEvent(this);
        for (ApplicationStateListener listener : listeners) {
            listener.applicationBackgrounded(e);
        }
    }

    private void notifyApplicationInForeground() {
        final ArrayList<ApplicationStateListener> listeners;
        synchronized (applicationStateListeners) {
            listeners = new ArrayList<ApplicationStateListener>(applicationStateListeners);
        }
        final ApplicationStateEvent e = new ApplicationStateEvent(this);
        for (ApplicationStateListener listener : listeners) {
            listener.applicationForegrounded(e);
        }
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public boolean getForegrounded() {
        return foregrounded.get();
    }

    public static boolean isAppInBackground() {
        return !getInstance().getForegrounded();
    }

}
