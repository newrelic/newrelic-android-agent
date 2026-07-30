/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;

import android.content.Context;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.analytics.AnalyticsEventStore;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.AbstractFileStore;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.NamedThreadFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * File-backed analytics event store. Stores one JSON file per event under
 * {@code filesDir/nr_event_cache/}. Filename is the event UUID; body is
 * {@code event.asJsonObject().toString()}. The UUID is recovered from the filename
 * on deserialize.
 *
 * <p>Writes ({@link #store}/{@link #delete}) are performed on a dedicated single-thread
 * executor so the recording hot path (often the main thread, e.g.
 * {@code NewRelic.recordBreadcrumb}) never blocks on disk I/O. Because both run on the
 * same single thread, a write and the harvest-time delete of the same event stay
 * FIFO-ordered — a write can never land after its delete and resurrect the event. Reads
 * ({@link #fetchAll}/{@link #count}) remain synchronous; {@code fetchAll} runs at startup
 * before any writes are queued. Callers that mutate an event around {@code store()} must
 * pass an immutable snapshot (e.g. a clone) since serialization happens on the executor.
 */
public class FileEventStore extends AbstractFileStore<AnalyticsEvent> implements AnalyticsEventStore {
    public static final String DIR_NAME = "nr_event_cache";

    private final ExecutorService writeExecutor =
            Executors.newSingleThreadExecutor(new NamedThreadFactory("EventStoreWriter"));

    public FileEventStore(Context context, AgentConfiguration config) {
        super(context, DIR_NAME, resolveCap(config),
                MetricNames.SUPPORTABILITY_EVENT_STORE_EVICTED,
                MetricNames.SUPPORTABILITY_EVENT_STORE_CORRUPTED,
                "FileEventStore");
    }

    private static int resolveCap(AgentConfiguration config) {
        final int configured = config.getMaxCachedEventCount();
        return configured > 0 ? configured : AgentConfiguration.DEFAULT_MAX_CACHED_EVENT_COUNT;
    }

    @Override
    public boolean store(final AnalyticsEvent event) {
        if (event == null) {
            return false;
        }
        writeExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final String eventJson = serialize(event);
                StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_EVENT_SIZE_UNCOMPRESSED, eventJson.length());
                storeInternal(event);
            }
        });
        return true;
    }

    @Override
    public List<AnalyticsEvent> fetchAll() {
        return fetchAllInternal();
    }

    @Override
    public int count() {
        return countInternal();
    }

    @Override
    public void delete(final AnalyticsEvent event) {
        if (event == null) {
            return;
        }
        writeExecutor.execute(new Runnable() {
            @Override
            public void run() {
                deleteInternal(event.getEventUUID());
            }
        });
    }

    @Override
    public void clear() {
        clearInternal();
    }

    /**
     * Block until queued writes/deletes have drained. For graceful shutdown and tests that
     * assert on-disk state synchronously after {@link #store}/{@link #delete}.
     *
     * @return true if the queue drained within {@code timeoutMs}
     */
    public boolean awaitWrites(long timeoutMs) {
        try {
            writeExecutor.submit(new Runnable() {
                @Override
                public void run() {
                }
            }).get(timeoutMs, TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void flush(long timeoutMs) {
        awaitWrites(timeoutMs);
    }

    @Override
    protected String keyOf(AnalyticsEvent event) {
        return event.getEventUUID();
    }

    @Override
    protected String serialize(AnalyticsEvent event) {
        return event.asJsonObject().toString();
    }

    @Override
    protected AnalyticsEvent deserialize(String keyFromFile, String json) {
        return AnalyticsEvent.eventFromJsonString(keyFromFile, json);
    }
}