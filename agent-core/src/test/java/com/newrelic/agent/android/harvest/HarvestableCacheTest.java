/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.google.gson.JsonArray;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.harvest.type.BaseHarvestable;
import com.newrelic.agent.android.harvest.type.Harvestable;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.tracing.ActivityTrace;
import com.newrelic.agent.android.tracing.Trace;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.fail;

@RunWith(JUnit4.class)
public class HarvestableCacheTest {
    private final static Lock lock = new ReentrantLock();
    private final AgentLog log = AgentLogManager.getAgentLog();

    @BeforeClass
    public static void setLogging() {
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
    }

    @Test
    public void testCacheActivityTrace() {
        lock.lock();
        try {
            Harvest.shutdown();

            Assert.assertEquals(0, Harvest.getActivityTraceCacheSize());
            Harvest.addActivityTrace(new TestActivityTrace());
            Assert.assertEquals(1, Harvest.getActivityTraceCacheSize());
            Harvest.setHarvestConfiguration(new HarvestConfiguration());
            Harvest.initialize(new AgentConfiguration());
            Harvest.start();
            Assert.assertEquals(0, Harvest.getActivityTraceCacheSize());
        } finally {
            lock.unlock();
        }
    }

    @Test
    public void testThreadSafeCache() throws Exception {
        try {
            testThreadedCacheAccess(true);
        } catch (ConcurrentModificationException e) {
            fail("Cache is NOT threadsafe.");
        }
    }

    @Test
    public void testThreadUnsafeCache() throws Exception {
        try {
            testThreadedCacheAccess(false);
            fail("Multithreaded tests are inadequate. Should not complete.");
        } catch (ConcurrentModificationException e) {
            // no-op
        } catch (RuntimeException e) {
            fail("Cache access failed on exception " + e.getClass().getSimpleName());
            e.printStackTrace();
        }
    }


    private void testThreadedCacheAccess(final boolean threadSafe) throws RuntimeException, ExecutionException {
        final HarvestableCacheStub stub = new HarvestableCacheStub(threadSafe);

        final Runnable producer = new Runnable() {
            @Override
            public void run() {
                while (!stub.isCancelled()) {
                    try {
                        stub.add(new BaseHarvestable(Harvestable.Type.VALUE));
                        Thread.sleep(10);

                    } catch (InterruptedException e) {
                    }
                }
            }
        };

        final Runnable consumer1 = new Runnable() {
            @Override
            public void run() throws ConcurrentModificationException {
                while (!stub.isCancelled()) {
                    Collection<Harvestable> cache = stub.getCache();
                    try {
                        Iterator<Harvestable> itr = cache.iterator();
                        while (itr.hasNext()) {
                            stub.remove(itr.next());
                        }
                        stub.flush();
                        Thread.sleep(100);

                    } catch (InterruptedException e) {
                    }
                }
            }
        };

        final Runnable consumer2 = new Runnable() {
            @Override
            public void run() throws ConcurrentModificationException {
                while (!stub.isCancelled()) {
                    Collection<Harvestable> cache = stub.getCache();
                    try {
                        Iterator<Harvestable> itr = cache.iterator();
                        while (itr.hasNext()) {
                            JsonArray jsonArray = itr.next().asJsonArray();
                        }
                        Thread.sleep(69);

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

        };

        try {
            ExecutorService executorService = Executors.newFixedThreadPool(3);
            Future<?> pFuture = executorService.submit(producer);
            Future<?> cFuture1 = executorService.submit(consumer1);
            Future<?> cFuture2 = executorService.submit(consumer2);

            Thread.sleep(10 * 1000);
            stub.setCancelled(true);

            pFuture.get();
            cFuture1.get();
            cFuture2.get();

        } catch (InterruptedException e) {
            fail("Test interrupted.");
            e.printStackTrace();
        } catch (ExecutionException e) {
            throw (RuntimeException) e.getCause();
        }
    }

    private class TestActivityTrace extends ActivityTrace {

        public TestActivityTrace() {
            rootTrace = new Trace();
        }

        @Override
        public JsonArray asJsonArray() {
            return new JsonArray();
        }
    }

    private class HarvestableCacheStub extends HarvestableCache {
        private boolean cancelled = false;
        private Collection<Harvestable> cache = getNewCache();
        private boolean threadsafe = true;

        private HarvestableCacheStub(boolean threadsafe) {
            this.threadsafe = threadsafe;
        }

        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        protected Collection<Harvestable> getNewCache() {
            if (threadsafe) {
                cache = new CopyOnWriteArrayList<Harvestable>();
            } else {
                cache = new ArrayList<Harvestable>();
            }

            return cache;
        }

        protected Collection<Harvestable> getCache() {
            return cache;
        }

        public void remove(Harvestable h) {
            cache.remove(h);
        }
    }
}
