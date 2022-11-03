/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import com.newrelic.agent.android.activity.MeasuredActivity;
import com.newrelic.agent.android.activity.NamedActivity;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.measurement.MeasurementException;
import com.newrelic.agent.android.measurement.MeasurementPool;
import com.newrelic.agent.android.measurement.consumer.MeasurementConsumer;
import com.newrelic.agent.android.measurement.producer.MeasurementProducer;
import com.newrelic.agent.android.util.NamedThreadFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * High level class which contains all current {@link MeasuredActivity}s and a root {@link MeasurementPool} which acts
 * as a source of {@code Measurements} for all {@link MeasuredActivity}'s pools.
 */
public class MeasurementEngine {
    private static final AgentLog log = AgentLogManager.getAgentLog();
    final Map<String, MeasuredActivity> activities = new ConcurrentHashMap<String, MeasuredActivity>();
    final MeasurementPool rootMeasurementPool = new MeasurementPool();

    /**
     * Record the start of a new {@code MeasuredActivity}.
     *
     * @param activityName The name of the new {@code MeasuredActivity}.
     * @return A new {@code MeasuredActivity} object.
     */
    public MeasuredActivity startActivity(String activityName) {
        if (activities.containsKey(activityName)) {
            throw new MeasurementException("An activity with the name '" + activityName + "' has already started.");
        }

        final MeasurementPool measurementPool = new MeasurementPool();
        final NamedActivity activity = new NamedActivity(activityName);

        // MeasurementEngines are allocated on the man (UI) thread, so anything that
        // blocks during construction will block the UI thread, possibly causing and ANR.
        // There's not really a cleaner way, but the most precise point to decouple the
        // threads is here, when the new pool is assigned. The NamedActivity doesn't use
        // the pool directly, so postponing its addition to the instance should be safe.

        bg(new Runnable() {
            @Override
            public void run() {
                activity.setMeasurementPool(measurementPool);
                rootMeasurementPool.addMeasurementConsumer(measurementPool);
            }
        });

        activities.put(activityName, activity);

        return activity;
    }

    /**
     * Rename a {@code NamedActivity}
     *
     * @param oldName The current name of the {@code NamedActivity}
     * @param newName The new name of the {@code NamedActivity}
     */
    public void renameActivity(String oldName, String newName) {
        final MeasuredActivity namedActivity = activities.remove(oldName);

        if (namedActivity != null && namedActivity instanceof NamedActivity) {
            activities.put(newName, namedActivity);
            ((NamedActivity)namedActivity).rename(newName);
        }
    }

    /**
     * End a {@code MeasuredActivity} with the given name.
     *
     * @param activityName The name of the {@code MeasuredActivity} to end.
     */
    public MeasuredActivity endActivity(String activityName) {
        MeasuredActivity measuredActivity = activities.get(activityName);
        if (measuredActivity == null) {
            throw new MeasurementException("Activity '" + activityName + "' has not been started.");
        }

        endActivity(measuredActivity);

        return measuredActivity;
    }

    /**
     * End a given {@code MeasuredActivity}.
     * @param activity The {@code MeasuredActivity} to end.
     */
    public void endActivity(MeasuredActivity activity) {
        // Will block on lock contention
        rootMeasurementPool.removeMeasurementConsumer(activity.getMeasurementPool());
        activities.remove(activity.getName());
        activity.finish();
    }

    /**
     * Clears the current list of activities.  Used in testing.
     */
    public void clear() {
        activities.clear();
    }

    /**
     * Add a {@code MeasurementProducer} to the root {@code MeasurementPool}.
     * @param measurementProducer The {@code MeasurementProducer} to add.
     */
    public void addMeasurementProducer(MeasurementProducer measurementProducer) {
        rootMeasurementPool.addMeasurementProducer(measurementProducer);
    }

    /**
     * Remove a {@code MeasurementProducer} from the root {@code MeasurementPool}.
     * @param measurementProducer The {@code MeasurementProducer} to remove.
     */
    public void removeMeasurementProducer(MeasurementProducer measurementProducer) {
        rootMeasurementPool.removeMeasurementProducer(measurementProducer);
    }

    /**
     * Add a {@code MeasurementConsumer} to the root {@code MeasurementPool}.
     * @param measurementConsumer The {@code MeasurementConsumer} to add.
     */
    public void addMeasurementConsumer(MeasurementConsumer measurementConsumer) {
        rootMeasurementPool.addMeasurementConsumer(measurementConsumer);
    }

    /**
     * Remove a {@code MeasurementConsumer} from the root {@code MeasurementPool}.
     * @param measurementConsumer The {@code MeasurementConsumer} to remove.
     */
    public void removeMeasurementConsumer(MeasurementConsumer measurementConsumer) {
        rootMeasurementPool.removeMeasurementConsumer(measurementConsumer);
    }

    /**
     * Broadcast measurements in the root {@code MeasurementPool} to all {@code MeasuredActivity}s
     */
    public void broadcastMeasurements() {
        // blocks on lock contention
        rootMeasurementPool.broadcastMeasurements();
    }


    // use judiciously
    private final ExecutorService worker = Executors.newCachedThreadPool(new NamedThreadFactory("MeasurementEngine"));

    Future<?> bg(Runnable runnable) {
        Future<?> future = null;
        try {
             future = worker.submit(runnable);
        } catch (Exception e) {
            log.warning("MeasurementEngine background worker: " + e);
        }
        return future;
    }

}
