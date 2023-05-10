/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.measurement.consumer.BaseMeasurementConsumer;
import com.newrelic.agent.android.measurement.consumer.MeasurementConsumer;
import com.newrelic.agent.android.measurement.producer.BaseMeasurementProducer;
import com.newrelic.agent.android.measurement.producer.CustomMetricProducer;
import com.newrelic.agent.android.measurement.producer.MeasurementProducer;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

@RunWith(JUnit4.class)
public class MeasurementPoolTests {

    @BeforeClass
    public static void setLogging() {
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
    }

    private final AgentLog log = AgentLogManager.getAgentLog();

    @Test
    public void testAddMeasurementProducer() {
        MeasurementPool pool = new MeasurementPool();
        BaseMeasurementProducer producer = new BaseMeasurementProducer(MeasurementType.Custom);

        pool.addMeasurementProducer(producer);
        Assert.assertTrue(pool.getMeasurementProducers().contains(producer));

        try {
            pool.addMeasurementProducer(producer);
            // Ensure a duplicate wasn't added (pool is also a producer, so add 1)
            Assert.assertEquals(2, pool.getMeasurementProducers().size());
        } catch (Exception e) {
            Assert.assertEquals(MeasurementException.class, e.getClass());
        }
    }

    @Test
    public void testAddMeasurementConsumer() {
        MeasurementPool pool = new MeasurementPool();
        BaseMeasurementConsumer consumer = new BaseMeasurementConsumer(MeasurementType.Custom);

        pool.addMeasurementConsumer(consumer);
        Assert.assertTrue(pool.getMeasurementConsumers().contains(consumer));

        try {
            pool.addMeasurementConsumer(consumer);
            // Ensure a duplicate wasn't added
            Assert.assertEquals(1, pool.getMeasurementConsumers().size());
        } catch (Exception e) {
            Assert.assertEquals(MeasurementException.class, e.getClass());
        }
    }

    @Test
    public void testRemoveMeasurementProducer() {
        MeasurementPool pool = new MeasurementPool();
        BaseMeasurementProducer producer = new BaseMeasurementProducer(MeasurementType.Custom);

        pool.addMeasurementProducer(producer);
        Assert.assertTrue(pool.getMeasurementProducers().contains(producer));

        pool.removeMeasurementProducer(producer);
        Assert.assertFalse(pool.getMeasurementProducers().contains(producer));

        try {
            pool.removeMeasurementProducer(producer);
        } catch (Exception e) {
            Assert.assertEquals(MeasurementException.class, e.getClass());
        }
    }

    @Test
    public void testRemoveMeasurementConsumer() {
        MeasurementPool pool = new MeasurementPool();
        BaseMeasurementConsumer consumer = new BaseMeasurementConsumer(MeasurementType.Custom);

        pool.addMeasurementConsumer(consumer);
        Assert.assertTrue(pool.getMeasurementConsumers().contains(consumer));

        pool.removeMeasurementConsumer(consumer);
        Assert.assertFalse(pool.getMeasurementConsumers().contains(consumer));

        try {
            pool.removeMeasurementConsumer(consumer);
        } catch (Exception e) {
            Assert.assertEquals(MeasurementException.class, e.getClass());
        }
    }

    @Test
    public void testSimpleProcessMeasurements() {
        MeasurementPool pool = new MeasurementPool();
        BaseMeasurementProducer producer = new BaseMeasurementProducer(MeasurementType.Custom);
        BaseMeasurementConsumer consumer = new BaseMeasurementConsumer(MeasurementType.Custom);

        pool.addMeasurementProducer(producer);
        pool.addMeasurementConsumer(consumer);

        int numMeasurements = 1000;
        for (int i = 0; i < numMeasurements; i++) {
            producer.produceMeasurement(new CustomMetricMeasurement());
        }

        pool.broadcastMeasurements();
    }

    @Test
    public void testConcurrentProcessMeasurements() {
        MeasurementPool pool = new TestMeasurementPool();

        int numProducers = 1;
        for (int i = 0; i < numProducers; i++) {
            pool.addMeasurementProducer(new CountingMeasurementProducer(MeasurementType.Custom));
        }

        int numConsumers = 5;
        for (int i = 0; i < numConsumers; i++) {
            pool.addMeasurementConsumer(new CountingMeasurementConsumer(MeasurementType.Custom));
        }

        long totalMeasurements = 1000;
        int maxMeasurementsPerRun = 1000;
        double skipProcessingChance = 0.0;

        ConsumerThread consumerThread = new ConsumerThread(pool, totalMeasurements, skipProcessingChance);
        Future consumerFuture = Executors.newSingleThreadExecutor().submit(consumerThread);

        ProducerThread producerThread = new ProducerThread(pool, totalMeasurements, maxMeasurementsPerRun);
        Future producerFuture = Executors.newSingleThreadExecutor().submit(producerThread);

        while (true) {
            if (consumerFuture.isDone() || consumerFuture.isCancelled()) {
                if (producerFuture.isDone() || producerFuture.isCancelled()) {
                    Assert.assertEquals(consumerThread.getProducedMeasurementCount() * numConsumers, consumerThread.getConsumedMeasurementCount());
                    break;
                }
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // no-op
            } catch (Exception e) {
                log.error(e.getMessage());
            }

        }
    }

    @Test
    public void testThreadSafeConcurrentProcessMeasurements() {
        MeasurementPool pool = new TestMeasurementPool();

        // To force this test to fail, change the prod/cons collections in MeasurementPool to:
        // private final Collection<MeasurementProducer> producers = new ArrayList<MeasurementProducer>();
        // private final Collection<MeasurementConsumer> consumers = new ArrayList<MeasurementConsumer>();

        int numProducers = 5;
        for (int i = 0; i < numProducers; i++) {
            pool.addMeasurementProducer(new CountingMeasurementProducer(MeasurementType.Custom));
        }

        int numConsumers = 15;
        for (int i = 0; i < numConsumers; i++) {
            pool.addMeasurementConsumer(new CountingMeasurementConsumer(MeasurementType.Custom));
        }

        final int threadCnt = 10;
        long totalMeasurements = 1000000;
        int maxMeasurementsPerRun = 1000;
        double skipProcessingChance = 0.33;

        final ExecutorService threadPool = Executors.newFixedThreadPool(threadCnt);

        try {
            final ArrayList<Future> futures = new ArrayList<Future>(threadCnt);

            futures.add(threadPool.submit(new ConsumerThread(pool, totalMeasurements, skipProcessingChance)));
            futures.add(threadPool.submit(new PoolUpdateThread(pool)));
            futures.add(threadPool.submit(new ProducerThread(pool, totalMeasurements, maxMeasurementsPerRun)));
            futures.add(threadPool.submit(new ReaderThread(pool)));
            futures.add(threadPool.submit(new PoolUpdateThread(pool)));
            futures.add(threadPool.submit(new ReaderThread(pool)));
            futures.add(threadPool.submit(new ProducerThread(pool, totalMeasurements, maxMeasurementsPerRun)));
            futures.add(threadPool.submit(new ConsumerThread(pool, totalMeasurements, skipProcessingChance)));

            Thread.sleep(30 * 1000);

            for (Future future : futures) {
                if (!future.isCancelled()) {
                    future.cancel(true);
                }
                future.get();
            }

        } catch (CancellationException e) {
            // noop
        } catch (InterruptedException e) {
            // noop
        } catch (ExecutionException e) {
            e.printStackTrace();
            log.error(e.getMessage());
            if (e.getCause() instanceof ConcurrentModificationException) {
                Assert.fail(e.getMessage());
            }
        }
    }

    @Test
    public void testConsumingProducesMeasurement() {
        MeasurementPool pool = new MeasurementPool();

        Measurement measurement = new CustomMetricMeasurement();
        pool.consumeMeasurement(measurement);

        Collection<Measurement> producedMeasurements = pool.drainMeasurements();

        Assert.assertEquals(1, producedMeasurements.size());
        Assert.assertEquals(measurement, producedMeasurements.iterator().next());
    }

    @Test
    public void testMeasurementPoolFillsFromAnotherPool() {
        CustomMetricProducer producer = new CustomMetricProducer();
        MeasurementPool rootPool = new MeasurementPool();
        MeasurementPool activityPool = new MeasurementPool();

        rootPool.addMeasurementProducer(producer);
        rootPool.addMeasurementConsumer(activityPool);

        for (int i = 0; i < 1000; i++) {
            producer.produceMeasurement("customname", "customcategory", 1,2,3);
        }

        rootPool.broadcastMeasurements();

        Collection<Measurement> measurements = activityPool.drainMeasurements();
        Assert.assertEquals(1000, measurements.size());
    }

    @Test
    public void testFilterNullMeasurements() throws Exception {
        // test a collection that contains some null measurements
        final Measurement nullMeasurement = null;
        ArrayList<Measurement> nullMeasurements = new ArrayList<Measurement>();
        nullMeasurements.add(nullMeasurement);
        nullMeasurements.add(new BaseMeasurement(MeasurementType.Custom));
        nullMeasurements.add(nullMeasurement);
        nullMeasurements.add(new BaseMeasurement(MeasurementType.Custom));
        nullMeasurements.add(nullMeasurement);
        nullMeasurements.add(new BaseMeasurement(MeasurementType.Custom));
        Assert.assertEquals(6, nullMeasurements.size());

        MeasurementPool pool = new MeasurementPool();
        CustomMetricProducer producer = new CustomMetricProducer();
        CountingMeasurementConsumer consumer = new CountingMeasurementConsumer(MeasurementType.Custom);

        // test null measurements against measurement producers
        pool.addMeasurementConsumer(consumer);
        pool.addMeasurementProducer(producer);

        producer.produceMeasurements(nullMeasurements);

        // these should fail as well
        producer.produceMeasurement(nullMeasurement);
        producer.produceMeasurement("customname", "customcategory", 1,2,3);
        producer.produceMeasurement("customname", "customcategory", 1,2,3);
        pool.broadcastMeasurements();

        Assert.assertEquals(5, consumer.getConsumedMeasurementCount().get());

        // producer should be drained
        Assert.assertEquals(0, pool.drainMeasurements().size());
    }


    private class CountingMeasurementProducer extends BaseMeasurementProducer {
        private final AtomicLong producedMeasurements = new AtomicLong();

        public CountingMeasurementProducer(MeasurementType measurementType) {
            super(measurementType);
        }

        @Override
        public void produceMeasurement(Measurement measurement) {
            synchronized (this) {
                super.produceMeasurement(measurement);
                producedMeasurements.incrementAndGet();
            }
        }

        public AtomicLong getProducedMeasurementCount() {
            return producedMeasurements;
        }
    }

    private class CountingMeasurementConsumer extends BaseMeasurementConsumer {
        private final AtomicLong consumedMeasurements = new AtomicLong();

        public CountingMeasurementConsumer(MeasurementType measurementType) {
            super(measurementType);
        }

        @Override
        public void consumeMeasurement(Measurement measurement) {
            synchronized (this) {
                consumedMeasurements.incrementAndGet();
                super.consumeMeasurement(measurement);
            }
        }

        @Override
        public void consumeMeasurements(Collection<Measurement> measurements) {
            synchronized (this) {
                consumedMeasurements.addAndGet(measurements.size());
                super.consumeMeasurements(measurements);
            }
        }

        public AtomicLong getConsumedMeasurementCount() {
            return consumedMeasurements;
        }
    }

    private class ProducerThread implements Runnable {
        private final static int MAX_SLEEP = 200;
        private final SecureRandom rand = new SecureRandom();
        private final long totalMeasurements;
        private final int maxMeasurementsPerRun;
        private final MeasurementPool pool;

        public ProducerThread(MeasurementPool pool, long totalMeasurements, int maxMeasurementsPerRun) {
            this.totalMeasurements = totalMeasurements;
            this.maxMeasurementsPerRun = maxMeasurementsPerRun;
            this.pool = pool;
        }

        @Override
        public void run() {
            long measurementCount = 0;
            while (measurementCount < totalMeasurements) {
                long numMeasurements = rand.nextInt(maxMeasurementsPerRun);

                if (measurementCount + numMeasurements >= totalMeasurements) {
                    numMeasurements = totalMeasurements - measurementCount;
                }

                for (MeasurementProducer producer : pool.getMeasurementProducers()) {
                    for (int i = 0; i < numMeasurements; i++) {
                        BaseMeasurement measurement = new CustomMetricMeasurement();
                        producer.produceMeasurement(measurement);
                    }
                    measurementCount += numMeasurements;
                }

                log.debug(Thread.currentThread().getId() + ": ProducerThread{numMeasurements=" + numMeasurements + ", measurementCount=" + measurementCount + "}");

                try {
                    Thread.sleep(300 + rand.nextInt(MAX_SLEEP));
                } catch (InterruptedException e) {
                    // no-op
                } catch (ConcurrentModificationException e) {
                    throw e;
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
            }
            log.debug(Thread.currentThread().getId() + ": ProducerThread finished");
        }
    }

    private class ConsumerThread implements Runnable {
        protected final static int MAX_SLEEP = 200;
        protected final SecureRandom rand = new SecureRandom();
        protected final MeasurementPool pool;
        private final long totalMeasurements;
        private final double skipChance;
        private long producedMeasurementCount;
        private long consumedMeasurementCount;

        public ConsumerThread(MeasurementPool pool, long totalMeasurements, double skipChance) {
            this.pool = pool;
            this.totalMeasurements = totalMeasurements;
            this.skipChance = skipChance;
        }

        @Override
        public void run() {
            while (true) {
                producedMeasurementCount = 0;
                consumedMeasurementCount = 0;

                for (MeasurementProducer producer : pool.getMeasurementProducers()) {
                    producedMeasurementCount += ((CountingMeasurementProducer) producer).getProducedMeasurementCount().get();
                }

                if (producedMeasurementCount >= totalMeasurements || rand.nextFloat() > skipChance) {
                    pool.broadcastMeasurements();
                }

                for (MeasurementConsumer consumer : pool.getMeasurementConsumers()) {
                    consumedMeasurementCount += ((CountingMeasurementConsumer) consumer).getConsumedMeasurementCount().get();
                }

                log.debug(Thread.currentThread().getId() + ": ConsumerThread{producedMeasurementCount=" + producedMeasurementCount + ", consumedMeasurementCount=" + consumedMeasurementCount + "}");

                if (consumedMeasurementCount >= totalMeasurements * pool.getMeasurementConsumers().size()) {
                    break;
                }

                try {
                    Thread.sleep(300 + rand.nextInt(MAX_SLEEP));
                } catch (InterruptedException e) {
                    // no-op
                } catch (ConcurrentModificationException e) {
                    throw e;
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
                log.debug(Thread.currentThread().getId() + ": ConsumerThread finished");
            }
        }

        public long getProducedMeasurementCount() {
            return producedMeasurementCount;
        }

        public long getConsumedMeasurementCount() {
            return consumedMeasurementCount;
        }
    }


    private static class TestMeasurementPool extends MeasurementPool {
        TestMeasurementPool() {
            super();
            // MeasurementPool adds itself as a producer. Don't want this for testing
            removeMeasurementProducer(this);
        }
    }

    private class PoolUpdateThread extends ConsumerThread {
        public PoolUpdateThread(MeasurementPool pool) {
            super(pool, 0, 0);
        }

        @Override
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(100 + rand.nextInt(MAX_SLEEP));
                    Collection<MeasurementProducer> producers = pool.getMeasurementProducers();
                    if (producers.size() > 1) {
                        pool.removeMeasurementProducer(producers.iterator().next());
                        pool.addMeasurementProducer(new CountingMeasurementProducer(MeasurementType.Custom));
                    }

                    Collection<MeasurementConsumer> consumers = pool.getMeasurementConsumers();
                    if (consumers.size() > 1) {
                        pool.removeMeasurementConsumer(consumers.iterator().next());
                        pool.addMeasurementConsumer(new CountingMeasurementConsumer(MeasurementType.Custom));
                    }
                }

            } catch (InterruptedException e) {
                // no-op
            } catch (ConcurrentModificationException e) {
                throw e;
            } catch (Exception e) {
                log.error(e.getMessage());
            }

            log.debug(Thread.currentThread().getId() + ": PoolUpdateThread finished");
        }

    }

    private class ReaderThread extends ConsumerThread {
        public ReaderThread(MeasurementPool pool) {
            super(pool, 0, 0);
        }

        @Override
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(50 + rand.nextInt(MAX_SLEEP));

                    Iterator<MeasurementProducer> pIt = pool.getMeasurementProducers().iterator();
                    while (pIt.hasNext()) {
                        MeasurementProducer prod = pIt.next();
                        prod.getMeasurementType();
                        Thread.sleep(10 + rand.nextInt(MAX_SLEEP));
                    }

                    Iterator<MeasurementConsumer> cIt = pool.getMeasurementConsumers().iterator();
                    while (cIt.hasNext()) {
                        MeasurementConsumer cons = cIt.next();
                        cons.getMeasurementType();
                        Thread.sleep(100 + rand.nextInt(MAX_SLEEP));
                    }
                }

            } catch (InterruptedException e) {
                // no-op
            } catch (ConcurrentModificationException e) {
                throw e;
            } catch (Exception e) {
                log.error(e.getMessage());
            }

            log.debug(Thread.currentThread().getId() + ": ReaderThread finished");
        }

    }

}
