/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement;

import com.newrelic.agent.android.api.v2.TraceMachineInterface;
import com.newrelic.agent.android.instrumentation.MetricCategory;
import com.newrelic.agent.android.measurement.producer.BaseMeasurementProducer;
import com.newrelic.agent.android.measurement.producer.CustomMetricMeasurementProducer;
import com.newrelic.agent.android.measurement.producer.MethodMeasurementProducer;
import com.newrelic.agent.android.measurement.producer.NetworkMeasurementProducer;
import com.newrelic.agent.android.metric.Metric;
import com.newrelic.agent.android.metric.MetricUnit;
import com.newrelic.agent.android.test.mock.Providers;
import com.newrelic.agent.android.tracing.ActivityTrace;
import com.newrelic.agent.android.tracing.Trace;
import com.newrelic.agent.android.tracing.TraceLifecycleAware;
import com.newrelic.agent.android.tracing.TraceMachine;

import org.junit.Assert;

import java.util.ArrayList;
import java.util.Arrays;

public class MetricMeasurementFactory implements TraceMachineInterface {
    static Integer seq = 1;

    BaseMeasurementProducer baseMeasurementProducer = new BaseMeasurementProducer(MeasurementType.Any);
    CustomMetricMeasurementProducer customMetricMeasurementProducer = new CustomMetricMeasurementProducer();
    MethodMeasurementProducer methodMeasurementProducer = new MethodMeasurementProducer();
    NetworkMeasurementProducer networkMeasurementProducer = new NetworkMeasurementProducer();

    public Metric provideMetric() {
        return provideMetric("provideMetric-" + seq++, "provided/Metric/Scope");
    }

    public Metric provideMetric(String name, String scope) {
        Metric metric = new Metric(name, scope);
        metric.sample((Math.random() * 11) + 0.33);
        return metric;
    }

    public Measurement provideMeasurement(MeasurementType measurementType) {
        if (measurementType == MeasurementType.Any) {
            return provideMeasurement();
        }

        BaseMeasurement measurement = new BaseMeasurement(measurementType);

        int tDelta = (int) (Math.random() * 1500f);
        long tStart = System.currentTimeMillis() - tDelta;
        long tEnd = tStart + tDelta;

        final String provideName = measurementType.name().toLowerCase() + "Measurement";

        switch (measurementType) {
            case Network:
                TraceMachine.startTracing("networkTrace");
                return new HttpTransactionMeasurement(Providers.provideTransactionData());

            case Custom:
                measurement = provideCategorizedMeasurement();
                break;

            case Method:
                Trace trace;
                try {
                    TraceMachine.startTracing("methodTraces");
                    trace = Providers.provideMethodTrace();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                TraceMachine.endTrace();

                return new MethodMeasurement(
                        trace.displayName,
                        trace.scope,
                        trace.entryTimestamp,
                        trace.exitTimestamp,
                        trace.exclusiveTime,
                        MetricCategory.NETWORK);

            case Activity:
                measurement = new ActivityMeasurement("activityMeasurement", tStart, tEnd);
                break;

            case Any:
            case Machine:
                break;
        }

        measurement.setName(provideName);
        measurement.setScope("providedMeasurementScope");
        measurement.setStartTime(tStart);
        measurement.setEndTime(tEnd);
        measurement.setExclusiveTime(tDelta);
        measurement.setThreadInfo(ThreadInfo.fromThread(Thread.currentThread()));
        measurement.finish();

        return measurement;
    }

    public Measurement provideMeasurement() {
        switch (randoInt(6)) {
            case 0:     // Network
                return provideMeasurement(MeasurementType.Network);
            case 1:     // Method
                return provideMeasurement(MeasurementType.Method);
            case 2:     // Activity
                return provideMeasurement(MeasurementType.Activity);
            case 3:     // Custom
                return provideCategorizedMeasurement();
            case 4:     // Any
                return provideMeasurement(MeasurementType.Any);
            case 5:     // Machine
                return provideMeasurement(MeasurementType.Machine);
        }

        return provideMeasurement(MeasurementType.Any);
    }

    public CustomMetricMeasurement provideCategorizedMeasurement() {
        CustomMetricMeasurement customMetricMeasurement = provideCustomMeasurement();

        switch (randoInt(6) + 1) {
            case 1:
                customMetricMeasurement.setCategory(MetricCategory.VIEW_LOADING);
                break;
            case 2:
                customMetricMeasurement.setCategory(MetricCategory.VIEW_LAYOUT);
                break;
            case 3:
                customMetricMeasurement.setCategory(MetricCategory.DATABASE);
                break;
            case 4:
                customMetricMeasurement.setCategory(MetricCategory.IMAGE);
                break;
            case 5:
                customMetricMeasurement.setCategory(MetricCategory.JSON);
                break;
            case 6:
            default:
                customMetricMeasurement.setCategory(MetricCategory.NETWORK);
                break;
        }

        final String producerName = CustomMetricMeasurementProducer.createMetricName(customMetricMeasurement.getName(),
                customMetricMeasurement.getCategory().name(),
                MetricUnit.BYTES_PER_SECOND, MetricUnit.BYTES);

        customMetricMeasurement.setName(producerName);

        return customMetricMeasurement;
    }

    public CustomMetricMeasurement provideCustomMeasurement() {
        int tDelta = randoInt(700);
        long tStart = System.currentTimeMillis() - tDelta;
        long tEnd = tStart + tDelta;

        CustomMetricMeasurement customMetricMeasurement = new CustomMetricMeasurement("customMeasurement",
                randoInt(7), (tEnd - tStart), tDelta * 0.34);

        customMetricMeasurement.setScope("providedCustomMeasurementScope");
        customMetricMeasurementProducer.produceMeasurement(
                customMetricMeasurement.getName(),
                MetricCategory.DATABASE.getCategoryName(),
                Math.toIntExact(customMetricMeasurement.getCustomMetric().getCount()),
                customMetricMeasurement.getCustomMetric().getTotal(),
                customMetricMeasurement.getCustomMetric().getExclusive(),
                MetricUnit.BYTES,
                MetricUnit.BYTES_PER_SECOND);

        return customMetricMeasurement;
    }

    public Trace provideRootTrace() {
        return provideActivityTrace("provideRootTrace", 1).rootTrace;
    }

    public ActivityTrace provideActivityTrace(final int nChildThreads) {
        return provideActivityTrace("provideActivityTrace", nChildThreads);
    }

    public ActivityTrace provideActivityTrace(final String traceName, final int nChildThreads) {
        final ActivityTrace[] activityTrace = {null};

        TraceLifecycleAware traceListener = new TraceLifecycleAware() {
            @Override
            public void onTraceComplete(ActivityTrace trace) {
                activityTrace[0] = trace;
            }
        };

        TraceMachine.addTraceListener(traceListener);
        TraceMachine.setTraceMachineInterface(this);

        Thread runner = new Thread(() -> {
            ArrayList<Thread> childThreads = new ArrayList<>();

            TraceMachine.startTracing(traceName);

            try {
                final ArrayList<String> annotations = new ArrayList<>(Arrays.asList("category", MetricCategory.class.getName(), "JSON"));

                for (int i = 0; i < nChildThreads; i++) {
                    childThreads.add(new Thread(() -> {
                        try {
                            TraceMachine.enterMethod(Thread.currentThread().getName() + "#onCreate", annotations);
                            Thread.sleep((int) (Math.random() * 1234));
                            TraceMachine.exitMethod();

                            TraceMachine.enterMethod(Thread.currentThread().getName() + "#onCreateView", annotations);
                            Thread.sleep((int) (Math.random() * 2345));
                            TraceMachine.exitMethod();

                            TraceMachine.enterMethod(Thread.currentThread().getName() + "#onStart", annotations);
                            Thread.sleep((int) (Math.random() * 1234));
                            TraceMachine.exitMethod();

                            TraceMachine.enterNetworkSegment("https://bin.org");
                            Thread.sleep((int) (Math.random() * 2282));

                            TraceMachine.enterMethod(Thread.currentThread().getName() + "#onResume", annotations);
                            Thread.sleep((int) (Math.random() * 3156));
                            TraceMachine.exitMethod();

                            TraceMachine.enterNetworkSegment("https://bin.org/login");
                            Thread.sleep((int) (Math.random() * 2737));

                            TraceMachine.enterMethod(Thread.currentThread().getName() + "#onStop", annotations);
                            Thread.sleep((int) (Math.random() * 6661));
                            TraceMachine.exitMethod();

                        } catch (InterruptedException ignored) {
                        }
                    }));
                }

                Assert.assertEquals(nChildThreads, childThreads.size());
                childThreads.forEach(thread -> thread.start());
                Thread.sleep((int) (Math.random() * 0.9) * 1000);
                childThreads.forEach(thread -> {
                    try {
                        thread.join();
                    } catch (InterruptedException e) {
                    }
                });

            } catch (InterruptedException ignored) {
            }

            TraceMachine.endTrace();
        });

        try {
            runner.start();
            runner.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        TraceMachine.removeTraceListener(traceListener);

        return activityTrace[0];
    }

    int randoInt(int ceil) {
        return ((int) (Math.random() * ceil));
    }

    @Override
    public long getCurrentThreadId() {
        return Thread.currentThread().getId();
    }

    @Override
    public String getCurrentThreadName() {
        return Thread.currentThread().getName();
    }

    @Override
    public boolean isUIThread() {
        return Thread.currentThread().getName().equals("main");
    }
}