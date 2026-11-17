/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay

import android.os.Handler
import android.os.Looper
import com.newrelic.agent.android.logging.AgentLogManager
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executor

/** Reads the mode that is recording right now. Called while the notifier's lock is held. */
internal fun interface SessionReplayModeSource {
    fun currentMode(): SessionReplayMode
}

/**
 * The [SessionReplayConfigListener]s registered through [SessionReplay.addConfigListener].
 * One instance lives for the process, so listeners stay registered across sessions and agent
 * restarts.
 *
 * [publish] copies the configuration and reads the mode under [lock], remembers the result as
 * the latest state, and posts one delivery task to [executor]. Doing both under one lock keeps
 * delivery in publish order and makes the last notification carry the final state, even when the
 * harvest thread and the main thread change state at the same time.
 */
internal class SessionReplayConfigNotifier(
    private val modeSource: SessionReplayModeSource,
    private val executor: Executor,
) {

    private class State(val configuration: SessionReplayConfiguration, val mode: SessionReplayMode)

    private val lock = Any()
    private val listeners = CopyOnWriteArraySet<SessionReplayConfigListener>()

    /** The latest published state, or null until the agent has resolved something. Guarded by [lock]. */
    private var last: State? = null

    /**
     * @return true if [listener] was not already registered. If a state was published already,
     * [listener] is called once with it, through the same queue as every other notification.
     */
    fun add(listener: SessionReplayConfigListener?): Boolean {
        if (listener == null) return false
        return try {
            synchronized(lock) {
                if (listeners.add(listener)) {
                    try {
                        last?.let { state -> executor.execute { deliver(listener, state) } }
                    } catch (e: Exception) {
                        // the registration stands; only the replay is lost
                        log("could not replay the latest config to a new listener: $e")
                    }
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            log("could not add config listener: $e")
            false
        }
    }

    /** @return true if [listener] was registered */
    fun remove(listener: SessionReplayConfigListener?): Boolean = listener != null && listeners.remove(listener)

    /**
     * Records the current state and tells the listeners that are registered right now. A listener
     * that registers after this call hears this state through its replay, not through this
     * delivery, so nobody hears it twice.
     */
    fun publish(configuration: SessionReplayConfiguration) {
        try {
            synchronized(lock) {
                val state = State(copyOf(configuration), modeSource.currentMode())
                last = state
                val targets = listeners.toList()
                if (targets.isEmpty()) return
                executor.execute { targets.forEach { deliver(it, state) } }
            }
        } catch (e: Exception) {
            // publish() runs inside harvester callbacks; nothing may escape into them
            log("could not publish config change: $e")
        }
    }

    /** Forgets every listener and the latest state. For tests. */
    fun reset() {
        synchronized(lock) {
            listeners.clear()
            last = null
        }
    }

    /**
     * A throwing listener must not reach the caller: this runs on the main thread, and the
     * harvester wraps its whole listener loop in one try, so an escaping exception would also
     * skip every harvest listener after ours. [Throwable] is deliberate: a listener built against
     * an older agent can fail with a linkage error, which must not crash the host app either.
     */
    private fun deliver(listener: SessionReplayConfigListener, state: State) {
        if (!listeners.contains(listener)) return
        try {
            listener.onSessionReplayConfigChanged(state.configuration, state.mode)
        } catch (t: Throwable) {
            AgentLogManager.getAgentLog().error("SessionReplay: config listener ${listener.javaClass.name} failed: $t")
        }
    }

    private fun log(message: String) {
        AgentLogManager.getAgentLog().error("SessionReplay: $message")
    }

    /**
     * The live object is updated in place by every config update, and delivery happens later on
     * another thread, so listeners get a copy. [SessionReplayConfiguration.setConfiguration] copies
     * only the raw fields, so the derived masking sets are rebuilt on the copy from its rules.
     */
    private fun copyOf(configuration: SessionReplayConfiguration): SessionReplayConfiguration =
        SessionReplayConfiguration().also {
            it.setConfiguration(configuration)
            it.processCustomMaskingRules()
        }

    companion object {
        /** An executor that runs work on the main thread, in the order it was posted. */
        @JvmStatic
        fun mainThreadExecutor(): Executor {
            // created on first use so loading SessionReplay never touches Looper
            val handler by lazy(LazyThreadSafetyMode.PUBLICATION) { Handler(Looper.getMainLooper()) }
            return Executor { handler.post(it) }
        }
    }
}
