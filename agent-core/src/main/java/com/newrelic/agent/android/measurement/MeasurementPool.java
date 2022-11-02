/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.measurement.consumer.MeasurementConsumer;
import com.newrelic.agent.android.measurement.producer.BaseMeasurementProducer;
import com.newrelic.agent.android.measurement.producer.MeasurementProducer;
import com.newrelic.agent.android.util.ExceptionHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The {@code MeasurementPool} class broadcasts {@link Measurement}s from multiple {@link MeasurementProducer}s
 * to multiple {@link MeasurementConsumer}s.
 * <p/>
 * {@code MeasurementPool} is responsible for draining {@code Measurement}s from {@code MeasurementProducer}s after they have
 * been produced. Because of this, a {@code MeasurementProducer} should be registered to only one {@code MeasurementPool}.
 * <p/>
 * {@code MeasurementPool} is itself both a {@code MeasurementProducer} and {@code MeasurementConsumer}. Consumed {@code Measurements}
 * are broadcast to registered {@code MeasurementConsumers} as if they were produced by a registered {@code MeasurementProducer}.
 * This allows {@code MeasurementPool} to be attached to other {@code MeasurementPools}.
 * <p/>
 * The {@code MeasurementPool} retains {@code Measurement}s for {@code MeasurementConsumers} if indicated. {@code Measurement}s
 * are retained on a per-{@code MeasurementConsumer} basis. Therefore, if a {@code Measurement} is retained, it is only
 * retained for that {@code MeasurementConsumer}. Several {@code MeasurementConsumers} may retain the same {@code Measurement},
 * which results in copies of the {@code Measurement} for each {@code MeasurementConsumer}.
 */
public class MeasurementPool extends BaseMeasurementProducer implements MeasurementConsumer {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    private final CopyOnWriteArrayList<MeasurementProducer> producers = new CopyOnWriteArrayList<MeasurementProducer>();
    private final CopyOnWriteArrayList<MeasurementConsumer> consumers = new CopyOnWriteArrayList<MeasurementConsumer>();

    public MeasurementPool() {
        super(MeasurementType.Any);
        addMeasurementProducer(this);
    }

    /**
     * Add a {@link MeasurementProducer} to the pool.
     *
     * @param producer The {@code MeasurementProducer} to add to the pool.
     */
    public void addMeasurementProducer(MeasurementProducer producer) {
        if (producer != null) {
            // Will block
            if (!producers.addIfAbsent(producer)) {
                log.debug("Attempted to add the same MeasurementProducer " + producer + "  multiple times.");
            }
        } else {
            log.debug("Attempted to add null MeasurementProducer.");
        }
    }

    /**
     * Remove a {@link MeasurementProducer} from the pool.
     *
     * @param producer The {@code MeasurementProducer} to remove from the pool.
     */
    public void removeMeasurementProducer(MeasurementProducer producer) {
        if (!producers.remove(producer)) {
            log.debug("Attempted to remove MeasurementProducer " + producer + " which is not registered.");
        }
    }

    /**
     * Add a {@link MeasurementConsumer} to the pool.
     *
     * @param consumer The {@code MeasurementConsumer} to add to the pool.
     */
    public void addMeasurementConsumer(MeasurementConsumer consumer) {
        if (consumer != null) {
            if (!consumers.addIfAbsent(consumer)) {
                log.debug("Attempted to add the same MeasurementConsumer " + consumer + " multiple times.");
            }
        } else {
            log.debug("Attempted to add null MeasurementConsumer.");
        }
    }

    /**
     * Remove a {@link MeasurementConsumer} from the pool.
     *
     * @param consumer The {@code MeasurementConsumer} to remove from the pool.
     */
    public void removeMeasurementConsumer(MeasurementConsumer consumer) {
        if (!consumers.remove(consumer)) {
            log.debug("Attempted to remove MeasurementConsumer " + consumer + " which is not registered.");
        }
    }

    /**
     * Gather {@code Measurements} from all {@code MeasurementProducers} and distribute them to all {@code MeasurementConsumers}.
     */
    public void broadcastMeasurements() {
        // log.debug("MeasurementPool broadcasting measurements");
        List<Measurement> allProducedMeasurements = new ArrayList<Measurement>();

        // CopyOnWriteArray iterators should *not* block:
        for (MeasurementProducer producer : producers) {
            // Gather all produced Measurements.
            Collection<Measurement> measurements = producer.drainMeasurements();
            if (measurements.size() > 0) {
                allProducedMeasurements.addAll(measurements);
                // filter out any null measurements
                while (allProducedMeasurements.remove(null)) {
                    ;
                }
            }
        }

        if (allProducedMeasurements.size() > 0) {
            // log.debug("There are " + allProducedMeasurements.size() + " measurements to produce...");
            for (MeasurementConsumer consumer : consumers) {
                // The newly produced Measurements now in a list ready for consumption:
                for (Measurement measurement : allProducedMeasurements) {
                    // Consume all Measurements that this consumer cares about.
                    if (consumer.getMeasurementType() == measurement.getType() || consumer.getMeasurementType() == MeasurementType.Any) {
                        // log.debug("Consumer " + consumer + " set to consume measurement " + measurement);
                        try {
                            consumer.consumeMeasurement(measurement);
                        } catch (Exception e) {
                            ExceptionHelper.exceptionToErrorCode(e);
                            log.error("broadcastMeasurements exception[" + e.getClass().getName() + "]");
                        }
                    }
                }
            }
        }
    }

    @Override
    public void consumeMeasurement(Measurement measurement) {
        produceMeasurement(measurement);
    }

    @Override
    public void consumeMeasurements(Collection<Measurement> measurements) {
        produceMeasurements(measurements);
    }

    @Override
    public MeasurementType getMeasurementType() {
        return MeasurementType.Any;
    }

    public Collection<MeasurementProducer> getMeasurementProducers() {
        return producers;
    }

    public Collection<MeasurementConsumer> getMeasurementConsumers() {
        return consumers;
    }
}
