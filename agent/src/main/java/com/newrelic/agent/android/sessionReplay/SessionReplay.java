package com.newrelic.agent.android.sessionReplay;

import static com.newrelic.agent.android.util.Constants.SessionReplay.FIRST_TIMESTAMP;
import static com.newrelic.agent.android.util.Constants.SessionReplay.LAST_TIMESTAMP;

import android.app.Application;
import android.os.Handler;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.sessioncontext.SessionContextStore;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.analytics.EventListener;
import com.newrelic.agent.android.analytics.EventManager;
import com.newrelic.agent.android.analytics.EventManagerImpl;
import com.newrelic.agent.android.background.ApplicationStateEvent;
import com.newrelic.agent.android.background.ApplicationStateListener;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestLifecycleAware;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.sessionReplay.capture.SessionReplayFileManager;
import com.newrelic.agent.android.sessionReplay.recovery.SessionReplayOrphanRecoverer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import com.newrelic.agent.android.sessionReplay.capture.SessionReplayFrame;
import com.newrelic.agent.android.sessionReplay.capture.SessionReplayProcessor;
import com.newrelic.agent.android.sessionReplay.capture.ViewDrawInterceptor;
import com.newrelic.agent.android.sessionReplay.internal.OnFrameTakenListener;
import com.newrelic.agent.android.sessionReplay.viewMapper.ComposeImageThingy;
import com.newrelic.agent.android.sessionReplay.viewMapper.SessionReplayImageViewThingy;
import com.newrelic.agent.android.sessionReplay.touch.OnTouchRecordedListener;
import com.newrelic.agent.android.sessionReplay.touch.TouchTracker;
import com.newrelic.agent.android.sessionReplay.models.RRWebEvent;
import com.newrelic.agent.android.sessionReplay.models.RRWebFullSnapshotEvent;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import curtains.Curtains;

public class SessionReplay implements OnFrameTakenListener, HarvestLifecycleAware, OnTouchRecordedListener, ApplicationStateListener, EventListener {
    private static Application application;
    private static Handler uiThreadHandler;
    private static AgentConfiguration agentConfiguration;
    private static SessionReplayActivityLifecycleCallbacks sessionReplayActivityLifecycleCallbacks;
    private static SessionReplayProcessor processor;
    private static ViewDrawInterceptor viewDrawInterceptor;
    private static final SessionReplay instance = new SessionReplay();
    private SessionReplayFileManager fileManager;
    protected static final AgentLog log = AgentLogManager.getAgentLog();
    private static boolean isFirstChunk = true;
    private static final AtomicBoolean takeFullSnapshot = new AtomicBoolean(true);
    private static SessionReplayModeManager modeManager;

    /**
     * When the working buffer was last emptied by a harvest.
     *
     * Read by the WebView replay merge path: a harvest truncates the file, so an event written before
     * the next native full snapshot lands in a chunk that contains no node tree for it to attach to.
     * Republishing this lets that path re-close its graft gate at each harvest boundary and wait for
     * the post-harvest snapshot instead.
     */
    private static final AtomicLong lastHarvestClearedAtMs = new AtomicLong(0L);

    // Guards against double-init. initSessionReplay is invoked from both agent boot and
    // onHarvestConnected; without this flag the second call would recreate the processor,
    // file manager, and re-register the Harvest listener (causing onHarvest() to fire twice
    // per cycle). Cleared in deInitialize() so onSessionRestarted can re-init normally.
    private static final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private static final AtomicBoolean orphanRecoveryDone = new AtomicBoolean(false);

    // Buffer for queuing frames and touch data that arrive during harvest
    private static final AtomicBoolean isHarvesting = new AtomicBoolean(false);
    private final List<List<RRWebEvent>> frameBufferDuringHarvest =
            Collections.synchronizedList(new ArrayList<>());
    private final List<TouchTracker> touchBufferDuringHarvest =
            Collections.synchronizedList(new ArrayList<>());
    private final List<String> webViewEventBufferDuringHarvest =
            Collections.synchronizedList(new ArrayList<>());

    // Sliding window for ERROR mode
    private static final long SLIDING_WINDOW_MS = 15000L; // 15 seconds
    private static ScheduledExecutorService slidingWindowExecutor;
    private static ScheduledFuture<?> slidingWindowTask;

    /**
     * Sets whether the next snapshot should be a full snapshot or incremental.
     * This can be called from any class to force a full snapshot on the next capture.
     *
     * @param shouldTakeFullSnapshot true to take a full snapshot, false for incremental
     */
    public static void setTakeFullSnapshot(boolean shouldTakeFullSnapshot) {
        takeFullSnapshot.set(shouldTakeFullSnapshot);
        log.audit("SessionReplay: takeFullSnapshot set to " + shouldTakeFullSnapshot);
    }

    /**
     * Gets the current takeFullSnapshot value.
     *
     * @return true if the next snapshot will be a full snapshot, false otherwise
     */
    public static boolean shouldTakeFullSnapshot() {
        return takeFullSnapshot.get();
    }

    /**
     * Initializes the SessionReplay system with a specific recording mode.
     * This method sets up the necessary callbacks, handlers, and starts recording.
     * Should be called from the application's onCreate method.
     *
     * @param application     The application instance
     * @param uiThreadHandler Handler for the UI thread
     * @param agentConfiguration The agent configuration
     * @param mode The recording mode (FULL, ERROR, or OFF)
     */
    public static void initialize(Application application, Handler uiThreadHandler, AgentConfiguration agentConfiguration, SessionReplayMode mode) {
        if (application == null) {
            log.error("Cannot initialize with null application");
            return;
        }

        if (uiThreadHandler == null) {
            log.error("Cannot initialize with null UI thread handler");
            return;
        }

        if (mode == null) {
            log.error("Cannot initialize with null mode");
            return;
        }

        SessionReplay.application = application;
        SessionReplay.uiThreadHandler = uiThreadHandler;
        SessionReplay.agentConfiguration = agentConfiguration;

        // Initialize the singleton mode manager with the provided mode
        modeManager = SessionReplayModeManager.getInstance(agentConfiguration.getSessionReplayConfiguration());
        // Transition to the provided mode if not already in it
        modeManager.transitionTo(mode, "Initialization");

        sessionReplayActivityLifecycleCallbacks = new SessionReplayActivityLifecycleCallbacks(instance,application,modeManager);
        registerCallbacks();
    }

    /**
     * Deinitializes the SessionReplay system.
     * This method cleans up resources, unregisters callbacks, and stops recording.
     * Should be called when the application is being terminated or when session replay
     * functionality needs to be disabled.
     */
    public static void deInitialize() {
        if(application == null) {
            return;
        }
        unregisterCallbacks();
        Harvest.removeHarvestListener(instance);
        stopRecording();

        // Shutdown file manager
        SessionReplayFileManager.shutdown();

        isInitialized.set(false);
        log.debug("Session replay deinitialized");
    }

    @Override
    public void onHarvestBefore() {
        // Only prepare for harvest when in FULL mode
        if (modeManager != null && modeManager.getCurrentMode() == SessionReplayMode.FULL) {
            // Mark that harvest is starting - pause frame writes to prevent race condition
            isHarvesting.set(true);
            log.debug("SessionReplay: Harvest started, pausing frame writes");
        }
    }

    @Override
    public void onHarvest() {
        // Only harvest when in FULL mode
        if (modeManager == null || modeManager.getCurrentMode() == SessionReplayMode.ERROR) {
            log.debug("SessionReplay: Skipping harvest - in Error mode (buffered data not ready)");
            return;
        }

        log.debug("Harvest started, reading frames and touch data from file as JSON array");

        // Read all events from the file as JsonArray
        JsonArray jsonArray = SessionReplayFileManager.readEventsAsJsonArray();

        if (jsonArray.isEmpty()) {
            log.debug("No events found in file to process.");
            return;
        }

        // Events reach the NDJSON file in arrival order, and merged WebView events arrive in
        // batches that lag native writes, so the file is not ordered by time. Sorting here does two
        // jobs at once: it makes the stream structurally valid for replay, and it turns the
        // positional first/last timestamp reads below into real min/max, so SessionReplaySender and
        // buildFrozenAttributes need no change.
        jsonArray = sortEventsForReplay(jsonArray);

        // Shed WebView documents rather than losing the whole chunk to the size cap.
        jsonArray = enforcePayloadBudget(jsonArray);

        Map<String, Object> attributes = new HashMap<>();

        // First/last timestamps. Scans inward for an event that actually carries a positive
        // timestamp rather than trusting the endpoints: a single malformed event at either end
        // would otherwise put System.currentTimeMillis() into the chunk metadata, which for
        // lastTimestamp reads as a chunk that extends past every event it contains.
        long firstTimestamp = System.currentTimeMillis();
        long lastTimestamp = System.currentTimeMillis();
        try {
            for (int i = 0; i < jsonArray.size(); i++) {
                long ts = readTimestamp(jsonArray.get(i));
                if (ts > 0) {
                    firstTimestamp = ts;
                    break;
                }
            }
            for (int i = jsonArray.size() - 1; i >= 0; i--) {
                long ts = readTimestamp(jsonArray.get(i));
                if (ts > 0) {
                    lastTimestamp = ts;
                    break;
                }
            }
            log.debug("Using event timestamps from file: " + firstTimestamp + " - " + lastTimestamp);
        } catch (Exception e) {
            log.warn("Failed to extract event timestamps, using current time");
        }

        attributes.put(FIRST_TIMESTAMP, firstTimestamp);
        attributes.put(LAST_TIMESTAMP, lastTimestamp);
        attributes.put(Constants.SessionReplay.IS_FIRST_CHUNK, isFirstChunk);

        log.info("SessionReplay harvest: " + jsonArray.size() + " events, timestamps [" + firstTimestamp + " - " + lastTimestamp + "], isFirstChunk=" + isFirstChunk);

        // Convert JsonArray to JSON string and report
        String jsonArrayString = new Gson().toJson(jsonArray);
        SessionReplayReporter.reportSessionReplayData(jsonArrayString.getBytes(), attributes);

        // Clear file after successful harvest
        fileManager.clearWorkingFileWhileRunningSession();
        lastHarvestClearedAtMs.set(System.currentTimeMillis());
        isFirstChunk = false;
        persistSrState(true, false);
        takeFullSnapshot.set(true);

    }


    /**
     * Drops WebView document grafts, largest first, until the chunk fits under
     * {@link Constants.Network#MAX_PAYLOAD_SIZE} compressed.
     *
     * Without this, {@link SessionReplayReporter} rejects an oversized chunk <em>whole</em> — native
     * events included — so a single heavy WebView document costs an entire harvest cycle of native
     * replay and shows the viewer a blank player. Shedding the grafts instead degrades the WebView to
     * an empty iframe while the native replay survives, which is the right direction to fail in:
     * device measurement put one graft at 79–97% of the chunk that contained it.
     *
     * @return the original array when it already fits, otherwise a reduced copy
     */
    static JsonArray enforcePayloadBudget(JsonArray events) {
        try {
            byte[] json = new Gson().toJson(events).getBytes();
            int compressed = gzippedLength(json);
            if (compressed <= Constants.Network.MAX_PAYLOAD_SIZE) {
                return events;
            }

            // Derive an uncompressed budget from this chunk's own measured ratio rather than a
            // guessed constant: replay payloads have been observed compressing anywhere from 10% to
            // 41%, so a fixed assumption would either shed too eagerly or not enough. 5% of headroom
            // absorbs the ratio drifting as content is removed.
            double ratio = (double) compressed / (double) json.length;
            long budget = (long) ((Constants.Network.MAX_PAYLOAD_SIZE / ratio) * 0.95);

            // Largest first, so the fewest documents are lost.
            List<Integer> graftIndices = new ArrayList<>();
            for (int i = 0; i < events.size(); i++) {
                if (isWebViewGraft(events.get(i))) {
                    graftIndices.add(i);
                }
            }
            if (graftIndices.isEmpty()) {
                log.warn("SessionReplay: chunk is " + compressed + " compressed bytes, over the "
                        + Constants.Network.MAX_PAYLOAD_SIZE + " cap, and carries no WebView documents to shed");
                return events;
            }
            graftIndices.sort((a, b) -> Integer.compare(
                    events.get(b).toString().length(), events.get(a).toString().length()));

            Set<Integer> shed = new HashSet<>();
            long total = json.length;
            for (Integer index : graftIndices) {
                if (total <= budget) {
                    break;
                }
                total -= events.get(index).toString().length();
                shed.add(index);
            }

            JsonArray reduced = new JsonArray();
            for (int i = 0; i < events.size(); i++) {
                if (!shed.contains(i)) {
                    reduced.add(events.get(i));
                }
            }

            for (int i = 0; i < shed.size(); i++) {
                StatsEngine.SUPPORTABILITY.inc(
                        MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_REPLAY_SHED);
            }
            int after = gzippedLength(new Gson().toJson(reduced).getBytes());
            log.warn("SessionReplay: chunk was " + compressed + " compressed bytes (cap "
                    + Constants.Network.MAX_PAYLOAD_SIZE + "); shed " + shed.size() + " of "
                    + graftIndices.size() + " WebView document(s), now " + after
                    + ". The native replay is preserved; those WebViews render empty until the next graft.");
            return reduced;
        } catch (Exception e) {
            // Never lose a harvest to a sizing bug: fall through and let the reporter decide.
            log.error("SessionReplay: payload budget check failed; reporting unmodified", e);
            return events;
        }
    }

    /**
     * Whether this event is a WebView document graft.
     *
     * Identified structurally rather than by a marker field: only the WebView merge path ever adds a
     * {@code type: 0} Document node, since the native diff generator adds element and text nodes.
     * That keeps the discriminator out of the uploaded payload.
     */
    private static boolean isWebViewGraft(JsonElement event) {
        try {
            JsonObject object = event.getAsJsonObject();
            if (object.get("type").getAsInt() != RRWebEvent.RRWEB_EVENT_INCREMENTAL_SNAPSHOT) {
                return false;
            }
            JsonObject data = object.getAsJsonObject("data");
            if (data == null || !data.has("adds")) {
                return false;
            }
            for (JsonElement add : data.getAsJsonArray("adds")) {
                JsonObject node = add.getAsJsonObject().getAsJsonObject("node");
                if (node != null && node.has("type") && node.get("type").getAsInt() == 0) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Not a graft-shaped event.
        }
        return false;
    }

    /** Compressed length using the same gzip the reporter applies, so the check matches the cap. */
    private static int gzippedLength(byte[] uncompressed) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(32, uncompressed.length / 8));
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(uncompressed);
        }
        return out.size();
    }

    /**
     * @return the wall-clock ms at which a harvest last emptied the working buffer, or 0 if none has.
     */
    public static long getLastHarvestClearedAtMs() {
        return lastHarvestClearedAtMs.get();
    }

    /**
     * Orders a harvest's events for replay: by timestamp, and within one timestamp by event type so
     * that Meta precedes FullSnapshot precedes Incremental. A snapshot can therefore never be
     * serialized behind a mutation that depends on it.
     *
     * The sort is <em>stable</em> ({@link Collections#sort} is a merge sort), which is load-bearing:
     * within a type, arrival order is preserved, and that is what keeps a WebView document graft
     * ahead of the incremental mutations that arrived after it in the same batch and share its
     * timestamp.
     *
     * @param events events in file arrival order
     * @return a new array in replay order
     */
    static JsonArray sortEventsForReplay(JsonArray events) {
        List<JsonElement> ordered = new ArrayList<>(events.size());
        for (JsonElement event : events) {
            ordered.add(event);
        }

        Collections.sort(ordered, (left, right) -> {
            int byTime = Long.compare(readTimestamp(left), readTimestamp(right));
            if (byTime != 0) {
                return byTime;
            }
            return Integer.compare(typeRank(left), typeRank(right));
        });

        JsonArray sorted = new JsonArray();
        for (JsonElement event : ordered) {
            sorted.add(event);
        }
        return sorted;
    }

    /**
     * @return the event's timestamp, or 0 when absent or unreadable. Zero sorts such an event to the
     * front, where the {@code > 0} guards in {@link #onHarvest()} skip over it — as opposed to
     * sinking it to the end, where it would become the chunk's {@code lastTimestamp}.
     */
    private static long readTimestamp(JsonElement event) {
        try {
            JsonObject object = event.getAsJsonObject();
            if (object.has("timestamp") && !object.get("timestamp").isJsonNull()) {
                return object.get("timestamp").getAsLong();
            }
        } catch (Exception e) {
            // Malformed event; treated as timestamp-less.
        }
        return 0L;
    }

    /**
     * Tie-break order within a single timestamp: Meta, then FullSnapshot, then Incremental, then
     * anything unrecognized.
     */
    private static int typeRank(JsonElement event) {
        try {
            JsonObject object = event.getAsJsonObject();
            if (object.has("type") && !object.get("type").isJsonNull()) {
                switch (object.get("type").getAsInt()) {
                    case RRWebEvent.RRWE_EVENT_META:
                        return 0;
                    case RRWebEvent.RRWEB_EVENT_FULL_SNAPSHOT:
                        return 1;
                    case RRWebEvent.RRWEB_EVENT_INCREMENTAL_SNAPSHOT:
                        return 2;
                    default:
                        return 3;
                }
            }
        } catch (Exception e) {
            // Malformed event; ordered last within its timestamp.
        }
        return 3;
    }

    /**
     * Notified whenever a native full snapshot is written.
     *
     * A full snapshot makes the replayer reset its mirror and rebuild the document from scratch,
     * which destroys anything grafted into it — so any layer holding content that lives <em>inside</em>
     * a native node has to re-attach it. Exists as a listener rather than a direct call so this
     * package keeps no compile-time dependency on the WebView package.
     */
    public interface FullSnapshotListener {
        /** @param timestampMs the snapshot's own timestamp, so re-attached content can be ordered after it */
        void onNativeFullSnapshot(long timestampMs);
    }

    private static volatile FullSnapshotListener fullSnapshotListener;

    public static void setFullSnapshotListener(FullSnapshotListener listener) {
        fullSnapshotListener = listener;
    }

    /**
     * Records one merged WebView rrweb event into the same NDJSON buffer the native capture writes
     * to, so there is a single storage path and offline persistence and orphan recovery keep working
     * unchanged.
     *
     * Buffers across the harvest window for the same reason {@link #onFrameTaken} does: an event
     * written between {@link #onHarvest()}'s read and its {@code clearWorkingFileWhileRunningSession}
     * truncate would be silently discarded.
     *
     * @param event a fully remapped, harvest-ready rrweb event
     */
    public static void recordWebViewReplayEvent(JsonObject event) {
        if (event != null) {
            recordWebViewReplayEvent(event.toString());
        }
    }

    /**
     * String overload, so a cached document can be re-attached by splicing it into a pre-built event
     * rather than rebuilding and re-serializing a multi-megabyte Gson tree each time.
     */
    public static void recordWebViewReplayEvent(String eventJson) {
        if (eventJson == null || eventJson.isEmpty()) {
            return;
        }
        if (instance.fileManager == null) {
            log.warn("SessionReplay: dropping a WebView replay event; file manager not initialized");
            return;
        }
        if (isHarvesting.get()) {
            log.audit("WebView replay event received during harvest, buffering for later write");
            instance.webViewEventBufferDuringHarvest.add(eventJson);
            return;
        }
        instance.fileManager.addJsonEventToFile(eventJson);
    }

    private static void registerCallbacks() {
        application.registerActivityLifecycleCallbacks(sessionReplayActivityLifecycleCallbacks);
    }

    private static void unregisterCallbacks() {
        application.unregisterActivityLifecycleCallbacks(sessionReplayActivityLifecycleCallbacks);
    }

    public static void initSessionReplay(SessionReplayMode mode) {
        if (!isInitialized.compareAndSet(false, true)) {
            log.debug("Session replay already initialized; skipping duplicate init for mode: " + mode);
            return;
        }
        viewDrawInterceptor = new ViewDrawInterceptor(instance,agentConfiguration);
        processor = new SessionReplayProcessor();
        // Initialize file manager
        instance.fileManager = new SessionReplayFileManager(processor);
        SessionReplayFileManager.initialize(application);

        if(mode == SessionReplayMode.ERROR) {
            // Register SessionReplay as event listener using composite pattern
            // This allows SessionReplay to always listen for NetworkRequestErrorEvent while also supporting user-provided listeners
            EventManager eventManager = AnalyticsControllerImpl.getInstance().getEventManager();
            EventListener currentListener = ((EventManagerImpl) eventManager).getListener();

            // Wrap current listener (or create new composite) to ensure SessionReplay is always included
            if (currentListener instanceof CompositeEventListener) {
                // If already a composite, set SessionReplay as the session replay listener
                ((CompositeEventListener) currentListener).setSessionReplayListener(instance);
            } else {
                // Create new composite with SessionReplay and current listener
                CompositeEventListener composite = new CompositeEventListener(instance);
                if (currentListener != eventManager) {
                    // Only preserve current listener if it's not the default EventManager itself
                    composite.setUserListener(currentListener);
                }
                eventManager.setEventListener(composite);
            }
        }

        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_SESSION_REPLAY_INIT);
        startRecording(mode);
        isFirstChunk = true;
        log.debug("Session replay initialized successfully with mode: " + mode);
    }

    /**
     * Starts recording with the specified mode.
     *
     * @param mode The SessionReplayMode to use for recording (FULL or ERROR)
     */
    public static void startRecording(SessionReplayMode mode) {
        log.debug("Starting SessionReplay recording with mode: " + mode);
        Harvest.addHarvestListener(instance);
        takeFullSnapshot.set(true); // Force full snapshot when starting recording

        // Start sliding window timer if in ERROR mode
        if (mode == SessionReplayMode.ERROR) {
            startSlidingWindowTimer();
        } else if (mode == SessionReplayMode.FULL) {
            persistSrState(true, true);
        }

        // curtains.Curtains is a main-thread-only, non-thread-safe API (NR-608999): every
        // access must happen inside this single post() so the calling thread never races
        // the main thread on Curtains' internal LazyThreadSafetyMode.NONE delegate. The
        // listener must be registered before the decorViews.length == 0 early return,
        // otherwise cold start (no root view yet) never registers a listener at all.
        uiThreadHandler.post(() -> {
            Curtains.getOnRootViewsChangedListeners().add((view, added) -> {
                if (added) {
                    viewDrawInterceptor.Intercept(new View[]{view});
                } else {
                    viewDrawInterceptor.removeIntercept(new View[]{view});
                }
            });

            View[] decorViews = Curtains.getRootViews().toArray(new View[0]);//WindowManagerSpy.windowManagerMViewsArray();

            // Check if decorViews is not empty before accessing
            if (decorViews.length == 0) {
                log.warn("No root views available, skipping initial recording setup");
                return;
            }

            viewDrawInterceptor.Intercept(decorViews);
            sessionReplayActivityLifecycleCallbacks.setupTouchInterceptorForWindow(decorViews[0]);
        });
    }


    public static void stopRecording() {
        // See NR-608999: Curtains access must stay confined to the main thread.
        uiThreadHandler.post(() -> {
            if (viewDrawInterceptor != null) {
                viewDrawInterceptor.stopIntercept();
            }
            Curtains.getOnRootViewsChangedListeners().clear();
        });
    }

    @Override
    public void onFrameTaken(@NonNull SessionReplayFrame newFrame) {
        List<RRWebEvent> events = processor.processFrames(new ArrayList<>(List.of(newFrame)),takeFullSnapshot.get());

        // If harvest is in progress, buffer frames for later writing
        if (isHarvesting.get()) {
            log.audit("Frame received during harvest, buffering for later write");
            frameBufferDuringHarvest.add(events);
        } else {
            // Otherwise, write to file immediately
            if (fileManager != null) {
                fileManager.addFrameToFile(events);
            }
        }
        takeFullSnapshot.set(false);

        // A full snapshot resets the replayer's mirror, which wipes any content grafted into a
        // native node. Announce it so that content can be re-attached, timestamped to match the
        // snapshot so the harvest sort places it immediately after.
        long fullSnapshotAtMs = 0L;
        for (RRWebEvent event : events) {
            if (event instanceof RRWebFullSnapshotEvent) {
                fullSnapshotAtMs = Math.max(fullSnapshotAtMs, event.getTimestamp());
            }
        }
        if (fullSnapshotAtMs > 0L) {
            FullSnapshotListener listener = fullSnapshotListener;
            if (listener != null) {
                try {
                    listener.onNativeFullSnapshot(fullSnapshotAtMs);
                } catch (Throwable t) {
                    log.error("SessionReplay: full snapshot listener failed", t);
                }
            }
        }
    }

    @Override
    public void onTouchRecorded(TouchTracker touchTracker) {
        // If harvest is in progress, buffer touch data for later writing
        if (isHarvesting.get()) {
            log.audit("Touch data received during harvest, buffering for later write");
            touchBufferDuringHarvest.add(touchTracker);
        } else {
            // Otherwise, write to file immediately
            if (fileManager != null) {
                fileManager.addTouchToFile(touchTracker);
            }
        }
    }

    @Override
    public void applicationForegrounded(ApplicationStateEvent e) {

    }

    @Override
    public void applicationBackgrounded(ApplicationStateEvent e) {
        // delete the file if it exists
        log.debug("Deleting session replay working file on application backgrounded");
        if (fileManager != null) {
            fileManager.clearWorkingFile();
        }
        // Free the per-class image caches so a backgrounded process does not retain
        // up to ~8 MB (4 MB View + 4 MB Compose) of base64 image data until
        // process death. The next foreground capture will repopulate from drawables.
        SessionReplayImageViewThingy.clearImageCache();
        ComposeImageThingy.clearImageCache();
    }

    /**
     * Called when an event is about to be added to the analytics buffer.
     * Detects MobileRequestError events and switches SESSION REPLAY mode from ERROR to FULL.
     *
     * @param analyticsEvent The event about to be added
     * @return true to add the event, false to ignore
     */
    @Override
    public boolean onEventAdded(AnalyticsEvent analyticsEvent) {
        // Check if this is a mobile request error event

        //TODO: Uncomment when NetworkRequestErrorEvent Filter Functionality is available for mobile agents
//        if (analyticsEvent instanceof NetworkRequestErrorEvent) {
//            log.debug("SessionReplay: Mobile request error detected");
//            switchModeOnError();
//        }
        return true; // Always allow the event to be added
    }

    @Override
    public boolean onEventOverflow(AnalyticsEvent analyticsEvent) {
        return true;
    }

    @Override
    public boolean onEventEvicted(AnalyticsEvent analyticsEvent) {
        return true;
    }

    @Override
    public void onEventQueueSizeExceeded(int currentQueueSize) {
        // No-op
    }

    @Override
    public void onEventQueueTimeExceeded(int maxBufferTimeInSec) {
        // No-op
    }

    @Override
    public void onEventFlush() {
        // No-op
    }

    @Override
    public void onStart(EventManager eventManager) {
        // Register as a listener with the event manager
    }

    @Override
    public void onShutdown() {
        // No-op
    }

    @Override
    public void onHarvestFinalize() {
        // Resume frame/touch writes - allow buffered data to be written to file.
        // Runs after onHarvest() reads the file and before the network upload, so the
        // lock is released regardless of whether the upload later succeeds or fails.
        isHarvesting.set(false);
        log.debug("SessionReplay: Harvest finalized, resuming frame and touch writes");

        // Flush any frames that were buffered during harvest
        if (!frameBufferDuringHarvest.isEmpty()) {
            log.debug("Flushing " + frameBufferDuringHarvest.size() + " buffered frames to file after harvest");
            for (List<RRWebEvent> bufferedEvents : frameBufferDuringHarvest) {
                if (fileManager != null) {
                    fileManager.addFrameToFile(bufferedEvents);
                }
            }
            frameBufferDuringHarvest.clear();
        }

        // Flush any touch data that was buffered during harvest
        if (!touchBufferDuringHarvest.isEmpty()) {
            log.debug("Flushing " + touchBufferDuringHarvest.size() + " buffered touch data to file after harvest");
            for (TouchTracker bufferedTouch : touchBufferDuringHarvest) {
                if (fileManager != null) {
                    fileManager.addTouchToFile(bufferedTouch);
                }
            }
            touchBufferDuringHarvest.clear();
        }

        // Flush any WebView replay events that were buffered during harvest
        if (!webViewEventBufferDuringHarvest.isEmpty()) {
            log.debug("Flushing " + webViewEventBufferDuringHarvest.size() + " buffered WebView replay events to file after harvest");
            for (String bufferedEvent : webViewEventBufferDuringHarvest) {
                if (fileManager != null) {
                    fileManager.addJsonEventToFile(bufferedEvent);
                }
            }
            webViewEventBufferDuringHarvest.clear();
        }
    }

    /**
     * Starts the sliding window timer for ERROR mode.
     * Every 15 seconds, triggers a full snapshot and prunes old data.
     */
    private static void startSlidingWindowTimer() {
        if (slidingWindowExecutor == null) {
            slidingWindowExecutor = Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "SessionReplaySlidingWindow");
                t.setDaemon(true);
                return t;
            });
        }

        if (slidingWindowTask != null) {
            slidingWindowTask.cancel(false);
        }

        slidingWindowTask = slidingWindowExecutor.scheduleWithFixedDelay(
                SessionReplay::handleSlidingWindowTick,
                SLIDING_WINDOW_MS,
                SLIDING_WINDOW_MS,
                TimeUnit.MILLISECONDS
        );
        log.debug("SessionReplay: Sliding window timer started (15 second intervals)");
    }

    /**
     * Stops the sliding window timer for ERROR mode.
     * Called when transitioning from ERROR to FULL mode.
     */
    private static void stopSlidingWindowTimer() {
        if (slidingWindowTask != null) {
            slidingWindowTask.cancel(false);
            slidingWindowTask = null;
            log.debug("SessionReplay: Sliding window timer stopped");
        }
    }

    /**
     * Handles the 15-second sliding window tick.
     * Forces a full snapshot and prunes old data.
     */
    private static void handleSlidingWindowTick() {
        try {
            log.debug("SessionReplay: Sliding window tick - forcing full snapshot and pruning old data");
            // Force full snapshot for next frame
            setTakeFullSnapshot(true);
            // Prune events older than 15 seconds
            SessionReplayFileManager.pruneEventsOlderThan(SLIDING_WINDOW_MS);
        } catch (Exception e) {
            log.error("Error during sliding window tick", e);
        }
    }

    /**
     * Switches session replay mode from ERROR to FULL when an error is detected.
     * This ensures that when an error occurs during buffering, we capture full session context.
     *
     * @return true if mode was successfully transitioned to FULL, false otherwise
     */
    public static boolean switchModeOnError() {
        if (modeManager != null && modeManager.getCurrentMode() == SessionReplayMode.ERROR) {
            boolean modeChanged = modeManager.transitionTo(SessionReplayMode.FULL, "ErrorDetected");
            if (modeChanged) {
                // Stop the sliding window timer when switching to FULL mode
                stopSlidingWindowTimer();
                // Force a full snapshot to ensure we have complete data from this point forward
                setTakeFullSnapshot(true);
                persistSrState(true, true);
                return true;
            }
        }
        return false;
    }

    /**
     * Called when an error is detected (handled exception or error log).
     * Public method that can be called from NewRelic API to notify about errors.
     * If session replay is in ERROR mode, switches to FULL mode.
     */
    public static void onError() {
        if (modeManager != null) {
            log.info("SessionReplay: Error detected, current mode: " + modeManager.getCurrentMode());
        } else {
            log.debug("SessionReplay: Error detected but session replay not initialized");
        }
        switchModeOnError();
    }

    /**
     * Persists the Session Replay state for the current session into the session manifest so a
     * next-launch recoverer can decide whether an orphaned {@code .tmp} buffer is worth uploading.
     */
    /**
     * Recover Session Replay buffers orphaned by a prior abnormal termination (force-close, ANR,
     * crash, OOM) and re-report eligible ones for upload. Runs once per launch. Must be invoked
     * after both {@code SessionReplayReporter} is initialized and the prior session's exit reasons
     * have been recorded into the manifests (i.e. after the AEI harvest on harvest-connect).
     */
    public static void recoverOrphans() {
        if (!orphanRecoveryDone.compareAndSet(false, true)) {
            return;
        }
        try {
            File srDir = SessionReplayFileManager.getSessionReplayDataStore();
            AgentConfiguration cfg = AgentConfiguration.getInstance();
            new SessionReplayOrphanRecoverer(
                    srDir, cfg.getSessionContextStore(),
                    SessionReplayReporter::reportSessionReplayData, cfg.getSessionID(),
                    cfg.getPayloadTTL())
                    .recover();
        } catch (Exception e) {
            log.error("SessionReplay: orphan recovery failed: " + e);
        }
    }

    private static void persistSrState(boolean reachedFullMode, boolean isFirstChunkValue) {
        try {
            SessionContextStore store = AgentConfiguration.getInstance().getSessionContextStore();
            if (store != null) {
                store.updateSessionReplayState(
                        AgentConfiguration.getInstance().getSessionID(), reachedFullMode, isFirstChunkValue);
            }
        } catch (Exception e) {
            log.error("SessionReplay: failed to persist SR state: " + e);
        }
    }

    /**
     * Pauses session replay recording via the public API.
     * This method allows developers to programmatically stop session replay collection.
     * Immediately triggers a harvest cycle to send any buffered data.
     *
     * Behavior:
     * - If SessionReplay is disabled or OFF: returns false
     * - If in ERROR mode: transitions to OFF, triggers harvest immediately
     * - If in FULL mode: transitions to OFF, triggers harvest immediately
     *
     * After this call:
     * - No new data is collected
     * - Buffered data up to pause point is sent immediately via harvest
     * - File is cleared after harvest completes
     *
     * Note: hasReplay attribute remains true for this session.
     *
     * @return true if recording was stopped and harvest triggered, false if already stopped/disabled
     */
    public static boolean pauseReplay() {
        boolean modeChanged = modeManager.transitionTo(SessionReplayMode.OFF, "APIPauseReplay");
        if (modeChanged) {
            stopSlidingWindowTimer();
            stopRecording();
            return true;
        }
        return false;
    }

    /**
     * Gets the current session replay recording mode.
     * This allows developers to query the current state of session replay.
     *
     * @return The current SessionReplayMode (OFF, ERROR, or FULL), or null if not initialized
     */
    public static SessionReplayMode getCurrentMode() {
        if (modeManager == null) {
            return null;
        }
        return modeManager.getCurrentMode();
    }

    /**
     * Checks if session replay is currently recording (either ERROR or FULL mode).
     * This allows developers to check if session replay is active.
     *
     * @return true if recording in any mode (ERROR or FULL), false if OFF or not initialized
     */
    public static boolean isReplayRecording() {
        if (modeManager == null && modeManager.getCurrentMode() == SessionReplayMode.OFF) {
            return false;
        }
        return modeManager.isRecording();
    }

    /**
     * Transitions the session replay mode to a specified mode with a trigger reason.
     * Used internally by the API to transition between modes.
     * Handles stopping the sliding window timer when transitioning from ERROR to FULL.
     *
     * @param newMode The target SessionReplayMode
     * @param trigger The reason/trigger for the transition (for logging)
     * @return true if transition was successful, false otherwise
     */
    public static boolean transitionToMode(SessionReplayMode newMode, String trigger) {
        if (modeManager == null) {
            log.warn("SessionReplay: transitionToMode called but SessionReplay not initialized");
            return false;
        }

        SessionReplayMode currentMode = modeManager.getCurrentMode();

        // If transitioning from ERROR to FULL, stop the sliding window timer
        if (currentMode == SessionReplayMode.ERROR && newMode == SessionReplayMode.FULL) {
            stopSlidingWindowTimer();
            setTakeFullSnapshot(true);
        }

        return modeManager.transitionTo(newMode, trigger);
    }
}