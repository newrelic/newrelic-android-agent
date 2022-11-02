/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import com.newrelic.agent.android.activity.MeasuredActivity;
import com.newrelic.agent.android.api.common.TransactionData;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.measurement.ThreadInfo;
import com.newrelic.agent.android.measurement.consumer.ActivityMeasurementConsumer;
import com.newrelic.agent.android.measurement.consumer.CustomMetricConsumer;
import com.newrelic.agent.android.measurement.consumer.HttpTransactionHarvestingConsumer;
import com.newrelic.agent.android.measurement.consumer.MeasurementConsumer;
import com.newrelic.agent.android.measurement.consumer.MethodMeasurementConsumer;
import com.newrelic.agent.android.measurement.consumer.SummaryMetricMeasurementConsumer;
import com.newrelic.agent.android.measurement.http.HttpTransactionMeasurement;
import com.newrelic.agent.android.measurement.producer.ActivityMeasurementProducer;
import com.newrelic.agent.android.measurement.producer.CustomMetricProducer;
import com.newrelic.agent.android.measurement.producer.MeasurementProducer;
import com.newrelic.agent.android.measurement.producer.MethodMeasurementProducer;
import com.newrelic.agent.android.measurement.producer.NetworkMeasurementProducer;
import com.newrelic.agent.android.metric.MetricUnit;
import com.newrelic.agent.android.tracing.Trace;

import java.util.Map;

/**
 * Primary user facing API for the Measurement Engine. Static methods which wrap an instance of a {@link MeasurementEngine}.
 */
public class Measurements {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    private static final MeasurementEngine measurementEngine = new MeasurementEngine();

    // Measurement Producers
    private final static NetworkMeasurementProducer networkMeasurementProducer = new NetworkMeasurementProducer();
    private final static ActivityMeasurementProducer activityMeasurementProducer = new ActivityMeasurementProducer();
    private final static MethodMeasurementProducer methodMeasurementProducer = new MethodMeasurementProducer();
    private final static CustomMetricProducer customMetricProducer = new CustomMetricProducer();

    // Measurement Consumers, Metric Producers
    private final static HttpTransactionHarvestingConsumer httpTransactionHarvester = new HttpTransactionHarvestingConsumer();
    private final static ActivityMeasurementConsumer activityConsumer = new ActivityMeasurementConsumer();
    private final static MethodMeasurementConsumer methodMeasurementConsumer = new MethodMeasurementConsumer();
    private final static SummaryMetricMeasurementConsumer summaryMetricMeasurementConsumer = new SummaryMetricMeasurementConsumer();
    private final static CustomMetricConsumer customMetricConsumer = new CustomMetricConsumer();

    private static boolean broadcastNewMeasurements = true;

    /**
     * Initialize the Measurement Engine.
     */
    public static void initialize() {
        log.info("Measurement Engine initialized.");
        TaskQueue.start();

        // HTTP Error Measurements
        addMeasurementProducer(networkMeasurementProducer);

        addMeasurementProducer(activityMeasurementProducer);
        addMeasurementProducer(methodMeasurementProducer);
        addMeasurementProducer(customMetricProducer);

        // Consumers
        addMeasurementConsumer(httpTransactionHarvester);
        addMeasurementConsumer(activityConsumer);
        addMeasurementConsumer(methodMeasurementConsumer);
        addMeasurementConsumer(summaryMetricMeasurementConsumer);
        addMeasurementConsumer(customMetricConsumer);
    }

    /**
     * Shut down the Measurement Engine.
     */
    public static void shutdown() {
        TaskQueue.stop();
        measurementEngine.clear();

        log.info("Measurement Engine shutting down.");
        removeMeasurementProducer(networkMeasurementProducer);
        removeMeasurementProducer(activityMeasurementProducer);
        removeMeasurementProducer(methodMeasurementProducer);
        removeMeasurementProducer(customMetricProducer);

        removeMeasurementConsumer(httpTransactionHarvester);
        removeMeasurementConsumer(activityConsumer);
        removeMeasurementConsumer(methodMeasurementConsumer);
        removeMeasurementConsumer(summaryMetricMeasurementConsumer);
        removeMeasurementConsumer(customMetricConsumer);
    }

    /*** Measurement Production APIs ***/
    /* Network APIs */

    public static void addHttpTransaction(HttpTransactionMeasurement transactionMeasurement) {
        if (Harvest.isDisabled()) return;

        if (transactionMeasurement == null) {
            log.error("TransactionMeasurement is null. HttpTransactionMeasurement measurement not created.");
        } else {
            networkMeasurementProducer.produceMeasurement(transactionMeasurement);
            newMeasurementBroadcast();
        }
    }

    /* Custom Metrics */

    public static void addCustomMetric(String name, String category, int count, double totalValue, double exclusiveValue) {
        if (Harvest.isDisabled()) return;

        customMetricProducer.produceMeasurement(name, category, count, totalValue, exclusiveValue);
        newMeasurementBroadcast();
    }

    public static void addCustomMetric(String name, String category, int count, double totalValue, double exclusiveValue, MetricUnit countUnit, MetricUnit valueUnit) {
        if (Harvest.isDisabled()) return;

        customMetricProducer.produceMeasurement(name, category, count, totalValue, exclusiveValue, countUnit, valueUnit);
        newMeasurementBroadcast();
    }

    public static void setBroadcastNewMeasurements(boolean broadcast) {
        broadcastNewMeasurements = broadcast;
    }

    private static void newMeasurementBroadcast() {
        if (broadcastNewMeasurements)
            broadcast();
    }

    public static void broadcast() {
        // may block on lock contention
        measurementEngine.broadcastMeasurements();
    }

    /* Activity APIs */

    /**
     * Start a new {@code MeasuredActivity} with the given name.
     *
     * @param activityName The Activity name.
     * @return A new {@code MeasuredActivity};
     */
    public static MeasuredActivity startActivity(String activityName) {
        if (Harvest.isDisabled()) return null;

        return measurementEngine.startActivity(activityName);
    }

    /**
     * Rename a running {@code MeasuredActivity}
     *
     * @param oldName The current name of the {@code MeasuredActivity}
     * @param newName The new name of the {@code Measure}dActivity
     */
    public static void renameActivity(String oldName, String newName) {
        measurementEngine.renameActivity(oldName, newName);
    }

    /**
     * End a {@code MeasuredActivity}.
     *
     * @param activityName The name of the {@code MeasuredActivity} to end.
     */
    public static void endActivity(String activityName) {
        if (Harvest.isDisabled()) return;

        MeasuredActivity measuredActivity = measurementEngine.endActivity(activityName);
        activityMeasurementProducer.produceMeasurement(measuredActivity);
        newMeasurementBroadcast();
    }

    /**
     * End a {@code MeasuredActivity}.
     *
     * @param activity The {@code MeasuredActivity} to end.
     */
    public static void endActivity(MeasuredActivity activity) {
        if (Harvest.isDisabled()) return;

        measurementEngine.endActivity(activity);
        activityMeasurementProducer.produceMeasurement(activity);
        newMeasurementBroadcast();
    }

    /**
     * End a {@code MeasuredActivity} without producing a {@code Measurement}.
     *
     * @param activity The {@code MeasuredActivity} to end.
     */
    public static void endActivityWithoutMeasurement(MeasuredActivity activity) {
        if (Harvest.isDisabled()) return;

        measurementEngine.endActivity(activity);
    }

    public static void addTracedMethod(Trace trace) {
        if (Harvest.isDisabled()) return;

        methodMeasurementProducer.produceMeasurement(trace);
        newMeasurementBroadcast();
    }

    /**
     * Add a {@code MeasurementProducer} to the Measurement Engine.
     *
     * @param measurementProducer the {@code MeasurementProducer} to add
     */
    public static void addMeasurementProducer(MeasurementProducer measurementProducer) {
        measurementEngine.addMeasurementProducer(measurementProducer);
    }

    /**
     * Remove a {@code MeasurementProducer} from the Measurement Engine.
     *
     * @param measurementProducer the {@code MeasurementProducer} to remove
     */
    public static void removeMeasurementProducer(MeasurementProducer measurementProducer) {
        measurementProducer.drainMeasurements();
        measurementEngine.removeMeasurementProducer(measurementProducer);
    }

    /**
     * Add a {@code MeasurementConsumer} to the Measurement Engine.
     *
     * @param measurementConsumer the {@code MeasurementConsumer} to add
     */
    public static void addMeasurementConsumer(MeasurementConsumer measurementConsumer) {
        measurementEngine.addMeasurementConsumer(measurementConsumer);
    }

    /**
     * Remove a {@code MeasurementConsumer} from the Measurement Engine.
     *
     * @param measurementConsumer the {@code MeasurementConsumer} to remove
     */
    public static void removeMeasurementConsumer(MeasurementConsumer measurementConsumer) {
        measurementEngine.removeMeasurementConsumer(measurementConsumer);
    }

    /**
     * Process all {@code MeasuredActivity}s, {@code MeasurementProducers}, {@code MeasurementConsumers} and their {@code Measurement}s
     * Name is a work in progress.
     */
    public static void process() {
        measurementEngine.broadcastMeasurements();
    }
}
