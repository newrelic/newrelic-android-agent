/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.rum;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;

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
        if (this.context instanceof Application) {
            ((Application) this.context).registerActivityLifecycleCallbacks(this);
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
            tracer.setFirstActivityResumeTime(SystemClock.uptimeMillis());
            AppStartUpMetrics metrics = new AppStartUpMetrics();
            if (!firstActivityResumed
                    && (agentConfiguration.getLaunchActivityClassName() == null
                    || (agentConfiguration.getLaunchActivityClassName().equalsIgnoreCase(activity.getLocalClassName())))) {
                firstActivityResumed = true;
                if (tracer.isColdStart()) {
                    StatsEngine.get().sample(MetricNames.APP_LAUNCH_COLD, metrics.getColdStartTime() / 1000.0f);
                }
            } else {
                if (isForegrounded) {
                    isForegrounded = false;
                    StatsEngine.get().sample(MetricNames.APP_LAUNCH_HOT, metrics.getHotStartTime() / 1000.0f);
                }
            }
            log.debug("App launch time " + metrics.toString());
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
