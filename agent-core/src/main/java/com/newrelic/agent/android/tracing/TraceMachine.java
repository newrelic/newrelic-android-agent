/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.tracing;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.Measurements;
import com.newrelic.agent.android.TaskQueue;
import com.newrelic.agent.android.api.v2.TraceFieldInterface;
import com.newrelic.agent.android.api.v2.TraceMachineInterface;
import com.newrelic.agent.android.harvest.ActivityHistory;
import com.newrelic.agent.android.harvest.ActivitySighting;
import com.newrelic.agent.android.harvest.AgentHealth;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestAdapter;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.ExceptionHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class TraceMachine extends HarvestAdapter {

    public static final String NR_TRACE_FIELD = "_nr_trace";
    public static final String NR_TRACE_TYPE = "Lcom/newrelic/agent/android/tracing/Trace;";
    public static final String ACTIVITY_METRIC_PREFIX = "Mobile/Activity/Name/";
    public static final String ACTIVITY_BACKGROUND_METRIC_PREFIX = "Mobile/Activity/Background/Name/";
    public static final String ACTIVTY_DISPLAY_NAME_PREFIX = "Display ";
    public static final AtomicBoolean enabled = new AtomicBoolean(true);    // enabled by default

    public static int MAX_CONCURRENT_TRACES = 5;

    private static final AgentLog log = AgentLogManager.getAgentLog();
    private static final Object TRACE_MACHINE_LOCK = new Object();
    private static final Collection<TraceLifecycleAware> traceListeners = new CopyOnWriteArrayList<TraceLifecycleAware>();
    private static final ThreadLocal<Trace> threadLocalTrace = new ThreadLocal<Trace>();
    private static final ThreadLocal<TraceStack> threadLocalTraceStack = new ThreadLocal<TraceStack>();
    private static final List<ActivitySighting> activityHistory = new CopyOnWriteArrayList<ActivitySighting>();

    // After this timeout (in ms) the trace will automatically be closed unless it has missing children
    public static int HEALTHY_TRACE_TIMEOUT = 500;

    // After this timeout (in ms) the trace will automatically be closed regardless of missing children
    public static int UNHEALTHY_TRACE_TIMEOUT = 60000;

    // Map of all active traces keyed by root trace UUID string
    private static final ConcurrentHashMap<String, TraceMachine> activeTraces = new ConcurrentHashMap<>();

    // Most recently started TraceMachine for backward-compatible "current trace" resolution
    private static volatile TraceMachine lastStartedTraceMachine = null;

    private static TraceMachineInterface traceMachineInterface;

    private ActivityTrace activityTrace;
    private final String traceId;

    protected static boolean isEnabled() {
        return enabled.get() && FeatureFlag.featureEnabled(FeatureFlag.InteractionTracing);
    }

    protected TraceMachine(Trace rootTrace) {
        activityTrace = new ActivityTrace(rootTrace);
        traceId = rootTrace.myUUID.toString();

        // Register with the harvester
        Harvest.addHarvestListener(this);
    }

    public static TraceMachine getTraceMachine() {
        return lastStartedTraceMachine;
    }

    public static void addTraceListener(TraceLifecycleAware listener) {
        traceListeners.add(listener);
    }

    public static void removeTraceListener(TraceLifecycleAware listener) {
        traceListeners.remove(listener);
    }

    public static void setTraceMachineInterface(TraceMachineInterface traceMachineInterface) {
        TraceMachine.traceMachineInterface = traceMachineInterface;
    }

    public static void startTracing(String name) {
        startTracing(name, false);
    }

    public static void startTracing(final String name, final boolean customName) {
        startTracing(name, customName, false);
    }

    public static void startTracing(final String name, final boolean customName, final boolean customInteraction) {
        try {
            // abort if all InteractionTracing is disabled (or global enabled switch is false)
            if (!isEnabled()) {
                return;
            }

            // unless it's a custom interaction, abort if DefaultInteractions is disabled,
            if (!customInteraction && !FeatureFlag.featureEnabled(FeatureFlag.DefaultInteractions)) {
                return;
            }

            // abort if the ActivityTraceConfiguration says don't collect traces
            if (!Harvest.shouldCollectActivityTraces()) {
                return;
            }

            synchronized (TRACE_MACHINE_LOCK) {
                // If we've hit the max concurrent traces limit, complete the oldest trace
                if (activeTraces.size() >= MAX_CONCURRENT_TRACES) {
                    TraceMachine oldest = findOldestTrace();
                    if (oldest != null) {
                        log.debug("Max concurrent traces reached, completing oldest trace");
                        oldest.completeActivityTrace();
                    }
                }

                // Make sure to clear things out before we get started on this thread.
                threadLocalTrace.remove();
                threadLocalTraceStack.set(new TraceStack());

                // Create a new trace with no parent id as it's the root
                final Trace rootTrace = new Trace();
                if (customName) {
                    rootTrace.displayName = name;
                } else {
                    rootTrace.displayName = formatActivityDisplayName(name);
                }


                rootTrace.metricName = formatActivityMetricName(rootTrace.displayName);
                rootTrace.metricBackgroundName = formatActivityBackgroundMetricName(rootTrace.displayName);

                rootTrace.entryTimestamp = System.currentTimeMillis();

                log.debug("Started trace of " + name + ":" + rootTrace.myUUID.toString());

                // Downstream lock contention starts by creating a new TraceMachine:
                TraceMachine newTraceMachine = new TraceMachine(rootTrace);
                rootTrace.traceMachine = newTraceMachine;

                // Add to active traces map
                activeTraces.put(newTraceMachine.traceId, newTraceMachine);
                lastStartedTraceMachine = newTraceMachine;

                // Place this trace in ThreadLocal storage (must be called from invoking thread)
                pushTraceContext(rootTrace);

                // Store the last activity and record this activity in the history
                newTraceMachine.activityTrace.previousActivity = getLastActivitySighting();
                activityHistory.add(new ActivitySighting(rootTrace.entryTimestamp, rootTrace.displayName));

                for (TraceLifecycleAware listener : traceListeners) {
                    listener.onTraceStart(newTraceMachine.activityTrace);
                }
            }
        } catch (Exception e) {
            log.error("Caught error while initializing TraceMachine, shutting it down", e);

            AgentHealth.noticeException(e);

            lastStartedTraceMachine = null;
            threadLocalTrace.remove();
            threadLocalTraceStack.remove();
        }
    }

    private static TraceMachine findOldestTrace() {
        TraceMachine oldest = null;
        long oldestTime = Long.MAX_VALUE;
        for (TraceMachine tm : activeTraces.values()) {
            if (tm.activityTrace.startedAt < oldestTime) {
                oldestTime = tm.activityTrace.startedAt;
                oldest = tm;
            }
        }
        return oldest;
    }

    // This should only really be used in dire situations as tracing completes based on timeouts.
    // ^^^ LIES!
    // This is called by the AndroidAgentImpl when the agent is stopped.  This happens on
    // application backgrounding, for instance.
    public static void haltTracing() {
        synchronized (TRACE_MACHINE_LOCK) {
            if (isTracingInactive()) {
                return;
            }

            // Halt all active traces
            for (TraceMachine tm : new ArrayList<>(activeTraces.values())) {
                tm.activityTrace.discard();
                Harvest.removeHarvestListener(tm);
            }
            activeTraces.clear();
            lastStartedTraceMachine = null;

            endLastActivitySighting();

            threadLocalTrace.remove();
            threadLocalTraceStack.remove();
        }
    }

    public static void haltTracing(String traceId) {
        synchronized (TRACE_MACHINE_LOCK) {
            TraceMachine tm = activeTraces.remove(traceId);
            if (tm == null) {
                return;
            }

            tm.activityTrace.discard();
            Harvest.removeHarvestListener(tm);

            // Update lastStartedTraceMachine if it was the one we removed
            if (lastStartedTraceMachine == tm) {
                lastStartedTraceMachine = findMostRecentTrace();
            }

            endLastActivitySighting();

            threadLocalTrace.remove();
            threadLocalTraceStack.remove();
        }
    }

    private static TraceMachine findMostRecentTrace() {
        TraceMachine mostRecent = null;
        long mostRecentTime = Long.MIN_VALUE;
        for (TraceMachine tm : activeTraces.values()) {
            if (tm.activityTrace.startedAt > mostRecentTime) {
                mostRecentTime = tm.activityTrace.startedAt;
                mostRecent = tm;
            }
        }
        return mostRecent;
    }

    public static void endTrace() {
        TraceMachine tm = lastStartedTraceMachine;
        if (tm != null) {
            tm.completeActivityTrace();
        } else {
            log.debug("Attempted to end trace with no trace machine!");
        }
    }

    public static void endTrace(String id) {
        try {
            // First try direct lookup in activeTraces
            TraceMachine tm = activeTraces.get(id);
            if (tm != null) {
                tm.completeActivityTrace();
                return;
            }

            // Fall back to checking root trace UUID (for backward compat with UUID-based IDs)
            for (TraceMachine candidate : activeTraces.values()) {
                if (candidate.activityTrace.rootTrace.myUUID.toString().equals(id)) {
                    candidate.completeActivityTrace();
                    return;
                }
            }
        } catch (Exception e) {
            log.error("Tried to end trace: " + e.getMessage());
        }
    }

    public static String formatActivityMetricName(String name) {
        return ACTIVITY_METRIC_PREFIX + name;
    }

    public static String formatActivityBackgroundMetricName(String name) {
        return ACTIVITY_BACKGROUND_METRIC_PREFIX + name;
    }

    public static String formatActivityDisplayName(String name) {
        return ACTIVTY_DISPLAY_NAME_PREFIX + name;
    }

    private static Trace registerNewTrace(String name) throws TracingInactiveException {
        if (isTracingInactive()) {
            log.debug("Tried to register a new trace but tracing is inactive!");
            throw new TracingInactiveException();
        }

        Trace parentTrace = getCurrentTrace();

        // Resolve the TraceMachine from the parent trace's back-reference, or fall back to lastStarted
        TraceMachine owningMachine = parentTrace.traceMachine;
        if (owningMachine == null) {
            owningMachine = lastStartedTraceMachine;
        }
        if (owningMachine == null) {
            throw new TracingInactiveException();
        }

        // Create a new trace with the parent id
        Trace childTrace = new Trace(name, parentTrace.myUUID, owningMachine);
        try {
            owningMachine.activityTrace.addTrace(childTrace);
        } catch (Exception e) {
            throw new TracingInactiveException();
        }

        log.verbose("Registering trace of " + name + " with parent " + parentTrace.displayName);

        parentTrace.addChild(childTrace);

        // Note we don't load the trace just quite yet.  We're still on the parent thread and don't want to clobber the
        // current trace.

        return childTrace;
    }

    protected void completeActivityTrace() {
        synchronized (TRACE_MACHINE_LOCK) {
            // Remove this trace from the active map
            activeTraces.remove(this.traceId);

            // Update lastStartedTraceMachine if it was the one we completed
            if (lastStartedTraceMachine == this) {
                lastStartedTraceMachine = findMostRecentTrace();
            }

            this.activityTrace.complete();
            endLastActivitySighting();

            for (final TraceLifecycleAware listener : traceListeners) {
                listener.onTraceComplete(this.activityTrace);
            }
            // Deregister with the harvester
            Harvest.removeHarvestListener(this);
        }
    }

    public static void enterNetworkSegment(String name) {
        try {
            if (isTracingInactive())
                return;

            // Sometimes, network traces never end because the stream isn't read or the response was cached.
            // In this case, we'll just end them before starting another one.
            Trace currentTrace = getCurrentTrace();
            if (currentTrace.getType() == TraceType.NETWORK) {
                exitMethod();
            }

            // We pass null here because we don't want to push the current trace on the stack
            enterMethod(null, name, null);

            Trace networkTrace = getCurrentTrace();
            networkTrace.setType(TraceType.NETWORK);
        } catch (TracingInactiveException e) {
            // Nothing to do here, just move along.
        } catch (Exception e) {
            log.error("Caught error while calling enterNetworkSegment()", e);
            AgentHealth.noticeException(e);
        }
    }

    @SuppressWarnings("unused")
    public static void enterMethod(String name) {
        enterMethod(null, name, null);
    }

    public static void enterMethod(String name, ArrayList<String> annotationParams) {
        enterMethod(null, name, annotationParams);
    }

    @SuppressWarnings("unused")
    public static void enterMethod(Trace trace, String name, ArrayList<String> annotationParams) {
        try {
            if (isTracingInactive()) {
                return;
            }

            // Resolve the owning TraceMachine from thread-local context or lastStarted
            TraceMachine owningMachine = null;
            Trace currentThreadTrace = threadLocalTrace.get();
            if (currentThreadTrace != null && currentThreadTrace.traceMachine != null) {
                owningMachine = currentThreadTrace.traceMachine;
            }
            if (owningMachine == null) {
                owningMachine = lastStartedTraceMachine;
            }
            if (owningMachine == null) {
                return;
            }

            final long currentTime = System.currentTimeMillis();
            final long lastUpdatedAt = owningMachine.activityTrace.lastUpdatedAt;
            final long inception = owningMachine.activityTrace.startedAt;

            if ((lastUpdatedAt + HEALTHY_TRACE_TIMEOUT) < currentTime && !owningMachine.activityTrace.hasMissingChildren()) {
                log.debug(String.format("LastUpdated[%d] CurrentTime[%d] Trigger[%d]", lastUpdatedAt, currentTime, currentTime - lastUpdatedAt));
                log.debug("Completing activity trace after hitting healthy timeout (" + HEALTHY_TRACE_TIMEOUT + "ms)");
                owningMachine.completeActivityTrace();
                return;
            }

            if (inception + UNHEALTHY_TRACE_TIMEOUT < currentTime) {
                log.debug("Completing activity trace after hitting unhealthy timeout (" + UNHEALTHY_TRACE_TIMEOUT + "ms)");
                owningMachine.completeActivityTrace();
                return;
            }

            loadTraceContext(trace);

            Trace childTrace = registerNewTrace(name);

            pushTraceContext(childTrace);

            childTrace.scope = getCurrentScope();

            childTrace.setAnnotationParams(annotationParams);

            // Notify our listeners we're entering the method
            for (TraceLifecycleAware listener : traceListeners) {
                listener.onEnterMethod();
            }

            // Set the timestamp last so we're not timing ourselves
            childTrace.entryTimestamp = System.currentTimeMillis();
        } catch (TracingInactiveException e) {
            // Nothing to do here, just move along.
        } catch (Exception e) {
            log.error("Caught error while calling enterMethod()", e);
            AgentHealth.noticeException(e);
        }
    }

    @SuppressWarnings("unused")
    public static void exitMethod() {
        try {
            if (isTracingInactive()) {
                return;
            }

            Trace trace = threadLocalTrace.get();

            if (trace == null) {
                log.debug("threadLocalTrace is null");
                return;
            }

            // Set this first so we're not timing ourselves
            trace.exitTimestamp = System.currentTimeMillis();

            // Attempt to fetch the id and name of this thread
            if (trace.threadId == 0 && traceMachineInterface != null) {
                trace.threadId = traceMachineInterface.getCurrentThreadId();
                trace.threadName = traceMachineInterface.getCurrentThreadName();
            }

            // Notify our listeners we're exiting the method
            for (TraceLifecycleAware listener : traceListeners) {
                listener.onExitMethod();
            }

            // Attempt to complete the trace.  If something goes wrong and the tracemachine has already stopped, clean up
            // the thread locals and hand off the trace to the Measurement Engine.
            try {
                trace.complete();
            } catch (TracingInactiveException e) {
                threadLocalTrace.remove();
                threadLocalTraceStack.remove();
                if (trace.getType() == TraceType.TRACE) {
                    TaskQueue.queue(trace);
                }

                return;
            }

            // Now we need to pop this trace from the stack and set the active one
            threadLocalTraceStack.get().pop();

            // There is a chance we're instrumenting something on a thread that never received context
            // from its parent.  This is usually because we didn't instrument the thread jump.
            if (threadLocalTraceStack.get().empty()) {
                threadLocalTrace.set(null);
            } else {
                Trace parentTrace = threadLocalTraceStack.get().peek();
                threadLocalTrace.set(parentTrace);

                // Finally, we'll add our execution time to our parent's child exclusive time accumulator.
                parentTrace.childExclusiveTime += trace.getDurationAsMilliseconds();
            }

            if (trace.getType() == TraceType.TRACE) {
                TaskQueue.queue(trace);
            }
        } catch (Exception e) {
            log.error("Caught error while calling exitMethod()", e);
            AgentHealth.noticeException(e);
        }
    }

    private static void pushTraceContext(Trace trace) {
        if (isTracingInactive() || trace == null)
            return;

        TraceStack traceStack = threadLocalTraceStack.get();

        if (traceStack.empty()) {
            traceStack.push(trace);
        } else if (traceStack.peek() != trace) {
            traceStack.push(trace);
        }

        threadLocalTrace.set(trace);
    }

    private static void loadTraceContext(Trace trace) {
        if (isTracingInactive()) {
            return;
        }

        // If there's no context, we probably jumped threads so we'll use the context we're given to start the stack.
        if (threadLocalTrace.get() == null) {
            threadLocalTrace.set(trace);

            threadLocalTraceStack.set(new TraceStack());

            // If trace is null at the point, chances are we're in a thread that never received context across the jump.
            if (trace == null) {
                return;
            }

            threadLocalTraceStack.get().push(trace);
        } else {
            // When we rejoin the UI thread, our context will have been cleared and trace will be null.  In this case,
            // we simply pick up where we left off on the UI thread and there's no need to push on the stack.
            if (trace == null) {
                // Well this is awkward.  When people start their own threads, we lose lose context across the jump.
                // There isn't much we can do here if we weren't given a trace and the stack is empty.  This will just
                // end up attaching this trace to the root trace.
                if (threadLocalTraceStack.get().isEmpty()) {
                    log.debug("No context to load!");
                    threadLocalTrace.set(null);
                    return;
                }

                trace = threadLocalTraceStack.get().peek();
                threadLocalTrace.set(trace);
            }
        }

        log.verbose("Trace " + trace.myUUID.toString() + " is now active");
    }

    // This is called at the end of user methods to remove the trace context from thread local storage.  Note that we
    // don't unload on the UI thread.
    @SuppressWarnings("unused")
    public static void unloadTraceContext(Object object) {
        try {
            if (isTracingInactive()) {
                return;
            }

            if (traceMachineInterface != null && traceMachineInterface.isUIThread()) {
                return;
            }

            if (threadLocalTrace.get() != null) {
                log.verbose("Trace " + threadLocalTrace.get().myUUID.toString() + " is now inactive");
            }

            threadLocalTrace.remove();
            threadLocalTraceStack.remove();

            // Finally, remove the trace context from the object's field
            try {
                TraceFieldInterface tfi = (TraceFieldInterface) object;
                tfi._nr_setTrace(null);
            } catch (ClassCastException e) {
                ExceptionHelper.recordSupportabilityMetric(e, "TraceFieldInterface");
                log.error("Not a TraceFieldInterface: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("Caught error while calling unloadTraceContext()", e);
            AgentHealth.noticeException(e);
        }
    }

    public static Trace getCurrentTrace() throws TracingInactiveException {
        if (isTracingInactive()) {
            throw new TracingInactiveException();
        }

        // Return the thread local trace.  In a pinch, return the root trace since we've lost our position.
        Trace trace = threadLocalTrace.get();
        if (trace != null) {
            return trace;
        } else {
            return getRootTrace();
        }
    }

    public static Map<String, Object> getCurrentTraceParams() throws TracingInactiveException {
        return getCurrentTrace().getParams();
    }

    public static void setCurrentTraceParam(String key, Object value) {
        if (isTracingInactive()) {
            return;
        }

        try {
            Trace trace = getCurrentTrace();
            if (trace != null) {
                if (key == null) {
                    log.error("Cannot set current trace param: key is null");
                } else if (value == null) {
                    log.error("Cannot set current trace param: value is null");
                } else {
                    trace.getParams().put(key, value);
                }
            } else {
                throw new TracingInactiveException();
            }
        } catch (TracingInactiveException e) {
            return;
        }
    }

    public static void setCurrentDisplayName(String name) {
        synchronized (TRACE_MACHINE_LOCK) {
            TraceMachine tm = resolveCurrentTraceMachine();
            if (tm != null) {
                try {
                    Trace currentTrace = getCurrentTrace();
                    if (currentTrace != null) {
                        currentTrace.displayName = name;
                        for (TraceLifecycleAware listener : traceListeners) {
                            try {
                                listener.onTraceRename(tm.activityTrace);
                            } catch (Exception e) {
                                log.error("Cannot name trace. Tracing is not available: " + e.toString());
                            }
                        }
                    }
                } catch (TracingInactiveException e) {
                    return;
                }
            }
        }
    }

    /**
     * Resolve the TraceMachine from the current thread's trace context, falling back to lastStartedTraceMachine.
     */
    private static TraceMachine resolveCurrentTraceMachine() {
        Trace currentThreadTrace = threadLocalTrace.get();
        if (currentThreadTrace != null && currentThreadTrace.traceMachine != null) {
            return currentThreadTrace.traceMachine;
        }
        return lastStartedTraceMachine;
    }

    // This essentially allows one to rename the scope of the current Interaction/Activity trace.
    // It does not, however, rename the method names of the traces.
    public static void setRootDisplayName(String name) {
        if (isTracingInactive()) {
            return;
        }

        try {
            final Trace rootTrace = getRootTrace();
            Measurements.renameActivity(rootTrace.displayName, name);
            renameActivityHistory(rootTrace.displayName, name);
            rootTrace.metricName = formatActivityMetricName(name);
            rootTrace.metricBackgroundName = formatActivityBackgroundMetricName(name);
            rootTrace.displayName = name;

            final Trace currentTrace = getCurrentTrace();
            currentTrace.scope = getCurrentScope();
        } catch (TracingInactiveException e) {
            return;
        }
    }

    private static void renameActivityHistory(String oldName, String newName) {
        for (ActivitySighting activitySighting : activityHistory) {
            if (activitySighting.getName().equals(oldName)) {
                activitySighting.setName(newName);
            }
        }
    }

    public static String getCurrentScope() {
        try {
            if (isTracingInactive()) {
                return null;
            }

            TraceMachine tm = resolveCurrentTraceMachine();
            if (tm == null) {
                return null;
            }

            // If the interface isn't up yet, we'll assume we're on the main UI thread
            if (traceMachineInterface == null || traceMachineInterface.isUIThread()) {
                return tm.activityTrace.rootTrace.metricName;
            }

            return tm.activityTrace.rootTrace.metricBackgroundName;
        } catch (Exception e) {
            log.error("Caught error while calling getCurrentScope()", e);
            AgentHealth.noticeException(e);
            return null;
        }
    }

    public static boolean isTracingActive() {
        return !activeTraces.isEmpty();
    }

    public static boolean isTracingActive(String traceId) {
        return activeTraces.containsKey(traceId);
    }

    public static boolean isTracingInactive() {
        return activeTraces.isEmpty();
    }

    public void storeCompletedTrace(Trace trace) {
        try {
            if (activityTrace == null) {
                log.debug("Attempted to store a completed trace with no activity trace!");
                return;
            }

            activityTrace.addCompletedTrace(trace);
        } catch (Exception e) {
            log.error("Caught error while calling storeCompletedTrace()", e);
            AgentHealth.noticeException(e);
        }
    }

    public static Trace getRootTrace() throws TracingInactiveException {
        try {
            // Try to resolve from thread-local context first
            Trace currentThreadTrace = threadLocalTrace.get();
            if (currentThreadTrace != null && currentThreadTrace.traceMachine != null) {
                return currentThreadTrace.traceMachine.activityTrace.rootTrace;
            }
            // Fall back to lastStartedTraceMachine
            return lastStartedTraceMachine.activityTrace.rootTrace;
        } catch (NullPointerException e) {
            throw new TracingInactiveException();
        }
    }

    public static ActivityTrace getActivityTrace() throws TracingInactiveException {
        try {
            // Try to resolve from thread-local context first
            Trace currentThreadTrace = threadLocalTrace.get();
            if (currentThreadTrace != null && currentThreadTrace.traceMachine != null) {
                return currentThreadTrace.traceMachine.activityTrace;
            }
            // Fall back to lastStartedTraceMachine
            return lastStartedTraceMachine.activityTrace;
        } catch (NullPointerException e) {
            throw new TracingInactiveException();
        }
    }

    public static ActivityTrace getActivityTrace(String traceId) throws TracingInactiveException {
        TraceMachine tm = activeTraces.get(traceId);
        if (tm == null) {
            throw new TracingInactiveException();
        }
        return tm.activityTrace;
    }

    public static int getActiveTraceCount() {
        return activeTraces.size();
    }

    public static ActivityHistory getActivityHistory() {
        return new ActivityHistory(activityHistory);
    }

    public static ActivitySighting getLastActivitySighting() {
        if (activityHistory.isEmpty())
            return null;

        return activityHistory.get(activityHistory.size() - 1);
    }

    public static void endLastActivitySighting() {
        final ActivitySighting activitySighting = getLastActivitySighting();

        if (activitySighting != null) {
            activitySighting.end(System.currentTimeMillis());
        }
    }

    public static void clearActivityHistory() {
        activityHistory.clear();
    }

    @Override
    public void onHarvestBefore() {
        // Check timeout for this specific TraceMachine's activity trace
        final long currentTime = System.currentTimeMillis();
        final long lastUpdatedAt = this.activityTrace.lastUpdatedAt;
        final long inception = this.activityTrace.startedAt;

        if (lastUpdatedAt + HEALTHY_TRACE_TIMEOUT < currentTime && !this.activityTrace.hasMissingChildren()) {
            log.debug("Completing activity trace after hitting healthy timeout (" + HEALTHY_TRACE_TIMEOUT + "ms)");
            completeActivityTrace();
            StatsEngine.get().inc(MetricNames.SUPPORTABILITY_TRACES_HEALTHY);
            return;
        }

        if (inception + UNHEALTHY_TRACE_TIMEOUT < currentTime) {
            log.debug("Completing activity trace after hitting unhealthy timeout (" + UNHEALTHY_TRACE_TIMEOUT + "ms)");
            completeActivityTrace();
            StatsEngine.get().inc(MetricNames.SUPPORTABILITY_TRACES_UNHEALTHY);
            return;
        }
    }

    @Override
    public void onHarvestSendFailed() {
        try {
            this.activityTrace.incrementReportAttemptCount();
        } catch (NullPointerException e) {
            // no-op, tracing must not be active.
        }
    }

    private static class TraceStack extends Stack<Trace> {
    }
}
