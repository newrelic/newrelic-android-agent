/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.rum;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewTreeObserver;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.background.ApplicationStateEvent;
import com.newrelic.agent.android.background.ApplicationStateListener;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;

import java.io.Closeable;
import java.io.IOException;
import java.util.Date;

public class AppApplicationLifeCycle implements Application.ActivityLifecycleCallbacks, Closeable, ApplicationStateListener {
    private Context context;
    private static boolean firstDrawInvoked = false;
    private static boolean firstActivityCreated = false;
    private static boolean firstActivityResumed = false;

    private static boolean isActivityChangingConfig = false;
    private static boolean isForegrounded = false;
    private static boolean isBackgrounded = false;
    // Latches true if the app is backgrounded before the first frame is drawn. In that case the
    // cold-start (TTID) sample would include the backgrounded duration, so it is invalidated.
    // Unambiguous: no activity handoff precedes the first frame, so a zero-activity state before
    // the first draw is always a real user-backgrounding.
    private static boolean backgroundedBeforeFirstDraw = false;
    private static int activityReferences = 0;

    private static AgentConfiguration agentConfiguration = new AgentConfiguration();

    private static final AgentLog log = AgentLogManager.getAgentLog();

    /**
     * Initializes lifecycle tracking when the app performs a cold start.
     *
     * <p><b>Microsoft Intune MAM Compatibility:</b> This method safely handles contexts
     * wrapped by Microsoft Intune's Mobile Application Management (MAM). Intune wraps
     * contexts with {@code MAMContext}, which cannot be cast to {@link Application}.
     * We use {@link Context#getApplicationContext()} to get the actual {@link Application}
     * instance, avoiding {@link ClassCastException}.</p>
     *
     * @param context The context used to register lifecycle callbacks (may be MAM-wrapped)
     */
    public void onColdStartInitiated(Context context) {
        this.context = context.getApplicationContext();

        // Use ApplicationContext (this.context) instead of input context to avoid
        // ClassCastException with Microsoft Intune MAM's MAMContext wrapper

        AppTracer.getInstance().setAppOnCreateTime(SystemClock.uptimeMillis());
        // Cold-start (TTID) start anchor: true process-creation time on API 24+, otherwise fall
        // back to the content-provider init time (the earliest hook the agent has).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            AppTracer.getInstance().setProcessStartTime(Process.getStartUptimeMillis());
        }
        if (this.context instanceof Application) {
            ((Application) this.context).registerActivityLifecycleCallbacks(this);
            // Captures end of Application.onCreate(): ContentProvider.onCreate runs inside
            // ActivityThread.handleBindApplication, which also runs Application.onCreate before
            // returning. A Runnable posted here cannot execute until handleBindApplication
            // returns, so it lands right after Application.onCreate completes.
            new Handler(Looper.getMainLooper()).post(() ->
                    AppTracer.getInstance().setAppOnCreateEndTime(SystemClock.uptimeMillis()));
        } else {
            log.error("Unable to register activity lifecycle callbacks: ApplicationContext is not an Application instance. " +
                    "Context type: " + (this.context != null ? this.context.getClass().getName() : "null"));
        }
    }

    @Override
    public void close() throws IOException {
        // Safe cast check to avoid ClassCastException with MAMContext
        if (context instanceof Application) {
            ((Application) context).unregisterActivityLifecycleCallbacks(this);
        } else {
            log.warn("Unable to unregister activity lifecycle callbacks: Context is not an Application instance. " +
                    "Context type: " + (context != null ? context.getClass().getName() : "null"));
        }
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle bundle) {
        try {
            AppTracer tracer = AppTracer.getInstance();
            tracer.setIsColdStart(bundle == null);
            if (!firstActivityCreated) {
                firstActivityCreated = true;
                tracer.setFirstActivityCreatedTime(SystemClock.uptimeMillis());
                tracer.setFirstActivityName(activity.getLocalClassName());
                tracer.setFirstActivityReferrer(activity.getReferrer() + "");
                tracer.setFirstActivityIntent(activity.getIntent());
                // Cold start ends when the first frame is drawn (TTID). Watch the first activity's
                // decor view for its first draw.
                registerFirstFrameListener(activity);
            }
            log.debug("App launch time onActivityCreated " + new Date().getTime());
        } catch (Exception ex) {
            log.error("App launch time exception: " + ex);
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {
        log.debug("App launch time onActivityStarted " + new Date().getTime());
        AppTracer tracer = AppTracer.getInstance();
        if (tracer.getFirstActivityStartTime() == 0L) {
            // Cold-start fallback: the branch below only fires on background→foreground
            // transitions, so without this firstActivityStartTime stays 0 on cold start
            // and hotStartTime ends up ≈ uptime.
            tracer.setFirstActivityStartTime(SystemClock.uptimeMillis());
        }
        if (++activityReferences == 1 && !isActivityChangingConfig && isBackgrounded) {
            isForegrounded = true;
            isBackgrounded = false;
            tracer.setFirstActivityStartTime(SystemClock.uptimeMillis());
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        try {
            if (!FeatureFlag.featureEnabled(FeatureFlag.AppStartMetrics)) {
                log.verbose("App launch time feature is not enabled.");
                return;
            }

            log.debug(activity.getLocalClassName());
            AppTracer tracer = AppTracer.getInstance();
            if (isForegrounded) {
                // Hot start: the app returned to the foreground from the background.
                isForegrounded = false;
                tracer.setFirstActivityResumeTime(SystemClock.uptimeMillis());
                AppStartUpMetrics metrics = new AppStartUpMetrics();
                StatsEngine.get().sample(MetricNames.APP_LAUNCH_HOT, metrics.getHotStartTime() / 1000.0f);
                log.debug("App launch time " + metrics.toString());
            } else if (!firstActivityResumed) {
                // First resume of the cold session. Cold start (TTID) is measured at the first
                // frame, not here; this only records the resume time for sub-timings/debug.
                firstActivityResumed = true;
                tracer.setFirstActivityResumeTime(SystemClock.uptimeMillis());
            }
        } catch (Exception ex) {
            log.error("App launch time exception: " + ex);
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        log.debug("App launch time onActivityPaused" + new Date().getTime());
    }

    @Override
    public void onActivityStopped(Activity activity) {
        log.debug("App launch time onActivityStopped" + new Date().getTime());
        isActivityChangingConfig = activity.isChangingConfigurations();
        if (--activityReferences == 0 && !isActivityChangingConfig) {
            isBackgrounded = true;
            if (!firstDrawInvoked) {
                // Backgrounded before the first frame was drawn: the cold-start (TTID) sample
                // would include the backgrounded time, so mark it invalid.
                backgroundedBeforeFirstDraw = true;
            }
        }
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
        log.debug("App launch time onActivitySaveInstanceState" + new Date().getTime());
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        log.debug("App launch time onActivityDestroyed" + new Date().getTime());
    }

    @Override
    public void applicationForegrounded(ApplicationStateEvent applicationStateEvent) {
        log.debug("App launch time applicationForegrounded" + new Date().getTime());
    }

    @Override
    public void applicationBackgrounded(ApplicationStateEvent applicationStateEvent) {
        log.debug("App launch time applicationBackgrounded" + new Date().getTime());
    }

    /**
     * Registers a one-shot listener on the activity's decor view that fires when the first frame
     * is drawn, marking the end of the cold-start (TTID) window.
     */
    private void registerFirstFrameListener(final Activity activity) {
        try {
            final View decorView = activity.getWindow().getDecorView();
            final ViewTreeObserver viewTreeObserver = decorView.getViewTreeObserver();
            if (!viewTreeObserver.isAlive()) {
                return;
            }
            viewTreeObserver.addOnDrawListener(new ViewTreeObserver.OnDrawListener() {
                @Override
                public void onDraw() {
                    if (firstDrawInvoked) {
                        return;
                    }
                    // Guarded: onDraw() runs during the view draw pass, so an uncaught exception
                    // here would surface in rendering. A metric must never crash the UI.
                    try {
                        firstDrawInvoked = true;
                        AppTracer.getInstance().setFirstDrawTime(SystemClock.uptimeMillis());
                        sampleColdStart();
                        // A listener cannot be removed from within onDraw(); post the removal.
                        final ViewTreeObserver.OnDrawListener self = this;
                        decorView.post(() -> {
                            ViewTreeObserver vto = decorView.getViewTreeObserver();
                            if (vto.isAlive()) {
                                vto.removeOnDrawListener(self);
                            }
                        });
                    } catch (Exception ex) {
                        log.error("App launch time exception in first-frame listener: " + ex);
                    }
                }
            });
        } catch (Exception ex) {
            log.error("App launch time exception registering first-frame listener: " + ex);
        }
    }

    /**
     * Samples the cold-start (TTID) metric if this launch qualifies. Invoked once, from the
     * first-frame callback.
     */
    static void sampleColdStart() {
        if (!FeatureFlag.featureEnabled(FeatureFlag.AppStartMetrics)) {
            log.verbose("App launch time feature is not enabled.");
            return;
        }
        AppTracer tracer = AppTracer.getInstance();
        AppStartUpMetrics metrics = new AppStartUpMetrics();
        if (shouldRecordColdStart(tracer.isColdStart(), backgroundedBeforeFirstDraw)) {
            StatsEngine.get().sample(MetricNames.APP_LAUNCH_COLD, metrics.getColdStartTime() / 1000.0f);
        }
        log.debug("App launch time " + metrics.toString());
    }

    /**
     * A cold-start sample is recorded only when this is a genuine cold start and the app was not
     * backgrounded before the first frame drew (which would inflate the measurement).
     */
    static boolean shouldRecordColdStart(boolean isColdStart, boolean backgroundedBeforeFirstDraw) {
        return isColdStart && !backgroundedBeforeFirstDraw;
    }

    private String emptyIfNull(String s) {
        return s == null ? "" : s;
    }

    public static AgentConfiguration getAgentConfiguration() {
        return agentConfiguration;
    }

    public static void setAgentConfiguration(AgentConfiguration agentConfiguration) {
        AppApplicationLifeCycle.agentConfiguration = agentConfiguration;
    }
}
