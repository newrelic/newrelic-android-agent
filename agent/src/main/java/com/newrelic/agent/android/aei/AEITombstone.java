/*
 * Copyright (c) 2026. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.aei;

import android.app.ApplicationExitInfo;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.abovevacant.epitaph.core.Architecture;
import com.abovevacant.epitaph.core.BacktraceFrame;
import com.abovevacant.epitaph.core.Cause;
import com.abovevacant.epitaph.core.Signal;
import com.abovevacant.epitaph.core.Tombstone;
import com.abovevacant.epitaph.core.TombstoneThread;
import com.abovevacant.epitaph.wire.TombstoneDecoder;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Decodes the protobuf tombstone that {@link ApplicationExitInfo#getTraceInputStream()} returns
 * for {@link ApplicationExitInfo#REASON_CRASH_NATIVE} records, and renders it in debuggerd's
 * text layout so the output can be piped straight into {@code ndk-stack} for symbolication.
 *
 * <p>Two format details drive the decoding side:
 * <ul>
 *   <li>The tombstone protobuf is only attached from API 31 (S) onward. On API 30 the trace
 *       stream is null for native crashes, so there is nothing to decode.</li>
 *   <li>AOSP hands back the native tombstone as a raw {@code AutoCloseInputStream}, unlike the
 *       ANR trace which it wraps in a {@code GZIPInputStream}. The bytes arriving here are
 *       therefore already-uncompressed protobuf and go straight to the decoder.</li>
 * </ul>
 *
 * <p>Three more come from how {@code ndk-stack} parses its input. Its state machine only starts
 * consuming once it sees {@link #NDK_STACK_BANNER}, and it silently produces nothing at all for
 * input that lacks it — so the banner is emitted verbatim rather than paraphrased. Then, once it
 * has matched at least one frame, the very next line that is <em>not</em> a frame ends the dump.
 * That is why each thread gets its own banner instead of debuggerd's
 * {@code --- --- ---} separator: with the separator, ndk-stack would symbolicate the crashing
 * thread and silently discard every remaining thread. Finally, its frame pattern requires a
 * non-space token after the program counter, so a frame with no mapped file still has to emit
 * {@link #UNKNOWN_MAP} or it would terminate the run early.
 *
 * Decoding is best-effort: any failure is the caller's to absorb, because a tombstone we cannot
 * read must never stop the rest of the AEI records from being harvested.
 */
class AEITombstone {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    /**
     * The exact line {@code ndk-stack} looks for to enter its in-crash state (16 groups). It
     * substring-matches this, so the spacing and group count must not drift.
     */
    static final String NDK_STACK_BANNER =
            "*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***";

    /**
     * debuggerd's thread separator, used here to close each thread block. It has to be a line that
     * is neither the banner nor a frame — see the note in {@link #toReport}. A blank line would
     * also work, but a whitespace-only line is fragile: anything that filters or trims the log on
     * the way to ndk-stack could drop it and silently cost every other thread.
     */
    static final String THREAD_SEPARATOR =
            "--- --- --- --- --- --- --- --- --- --- --- --- --- --- --- ---";

    /** Stand-in path for a frame with no mapped file, keeping the line parseable. */
    static final String UNKNOWN_MAP = "<unknown>";

    /**
     * Cap on backtrace frames rendered per thread. Native backtraces are usually well under this,
     * but a runaway stack (deep recursion overflowing) can carry thousands of frames. The cap is
     * deliberately high because truncating a stack defeats the point of symbolicating it.
     */
    static final int MAX_BACKTRACE_FRAMES = 256;

    private AEITombstone() {
    }

    /**
     * @return true if this exit record's trace stream is expected to be a protobuf tombstone
     * rather than a text trace.
     */
    @RequiresApi(api = Build.VERSION_CODES.R)
    static boolean isNativeTombstone(int reason) {
        // REASON_CRASH_NATIVE exists in API 30, but the tombstone protobuf was only attached
        // to the exit record in API 31.
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && reason == ApplicationExitInfo.REASON_CRASH_NATIVE;
    }

    /**
     * Decode a tombstone protobuf and render it as debuggerd-style text.
     *
     * @param traceIs uncompressed tombstone protobuf stream; not closed by this method
     * @param pid     the pid of the exited process, used for the report header
     * @return the rendered report
     * @throws IOException if the stream is truncated or is not a well-formed tombstone
     */
    static String decodeToString(InputStream traceIs, int pid) throws IOException {
        return toReport(TombstoneDecoder.decode(traceIs), pid);
    }

    /**
     * Emit a report line by line. logcat truncates a single entry at roughly 4KB, so a multi-line
     * report written as one message would be silently cut off partway through the backtrace.
     */
    static void logReport(String report) {
        for (String line : report.split("\n")) {
            log.info(line);
        }
    }

    static String toReport(Tombstone tombstone, int pid) {
        StringBuilder sb = new StringBuilder(4096);

        // Crashing thread first, then the rest; each as its own ndk-stack dump.
        List<TombstoneThread> threads = orderedThreads(tombstone);
        if (threads.isEmpty()) {
            // No thread data to render, but still emit the process header so the record is not
            // silently empty.
            appendBanner(sb, tombstone, pid, null);
        } else {
            for (TombstoneThread thread : threads) {
                appendBanner(sb, tombstone, pid, thread);
                appendBacktrace(sb, thread);
                // Every thread block has to be closed by a non-frame line. ndk-stack only starts a
                // new dump on a banner it sees while *not* already in-crash; a banner arriving
                // directly after a frame is instead consumed as the line that ends the previous
                // dump, which silently drops every other thread. This separator is that
                // terminator, leaving the next banner free to open a fresh dump.
                sb.append(THREAD_SEPARATOR).append('\n');
            }
        }

        appendCounts(sb, tombstone);

        return sb.toString();
    }

    /**
     * Crashing thread (the one whose tid matches the tombstone's) first, so the most relevant
     * stack leads the report, followed by every other thread in map order.
     */
    private static List<TombstoneThread> orderedThreads(Tombstone tombstone) {
        List<TombstoneThread> ordered = new ArrayList<>();
        Map<Integer, TombstoneThread> threads = tombstone.threads;
        if (threads == null || threads.isEmpty()) {
            return ordered;
        }

        TombstoneThread crashed = threads.get(tombstone.tid);
        if (crashed != null) {
            ordered.add(crashed);
        }
        for (TombstoneThread thread : threads.values()) {
            if (thread != null && thread != crashed) {
                ordered.add(thread);
            }
        }
        return ordered;
    }

    /**
     * Write the banner plus the header block preceding a thread's frames. The build/ABI preamble
     * is only repeated for the crashing thread; debuggerd does the same, and repeating it for
     * every thread would bloat the log for no gain.
     */
    private static void appendBanner(StringBuilder sb, Tombstone tombstone, int pid,
                                     TombstoneThread thread) {
        boolean isCrashingThread = thread == null || thread.id == tombstone.tid;

        sb.append(NDK_STACK_BANNER).append('\n');

        if (isCrashingThread) {
            // ndk-stack echoes any line containing "Build fingerprint:" or "Abort message:",
            // so these two labels are spelled exactly as debuggerd spells them.
            appendQuoted(sb, "Build fingerprint", tombstone.buildFingerprint);
            appendQuoted(sb, "Kernel Release", tombstone.kernelRelease);
            appendQuoted(sb, "Revision", tombstone.revision);
            appendQuoted(sb, "ABI", toAbi(tombstone.arch));
            appendPlain(sb, "Timestamp", tombstone.timestamp);
            sb.append("Process uptime: ").append(tombstone.processUptime).append("s\n");
        }

        appendPlain(sb, "Executable", tombstone.executableName);
        appendPlain(sb, "Cmdline", commandLine(tombstone));

        // pid: 4733, ppid: 4634, tid: 4735, name: worker  >>> /path/to/exe <<<
        sb.append("pid: ").append(tombstone.pid != 0 ? tombstone.pid : pid)
                .append(", ppid: ").append(tombstone.ppid)
                .append(", tid: ").append(thread == null ? tombstone.tid : thread.id)
                .append(", name: ").append(thread == null ? "" : thread.name)
                .append("  >>> ").append(processLabel(tombstone)).append(" <<<\n");
        sb.append("uid: ").append(tombstone.uid).append('\n');

        if (isCrashingThread) {
            appendSignal(sb, tombstone.signal);
            appendCauses(sb, tombstone.causes);
            appendQuoted(sb, "Abort message", tombstone.abortMessage);
        }
    }

    private static void appendSignal(StringBuilder sb, Signal signal) {
        if (signal == null) {
            return;
        }

        // signal 6 (SIGABRT), code -1 (SI_QUEUE), fault addr --------
        sb.append("signal ").append(signal.number).append(" (").append(signal.name).append(')')
                .append(", code ").append(signal.code).append(" (").append(signal.codeName).append(')')
                .append(", fault addr ")
                .append(signal.hasFaultAddress ? hex(signal.faultAddress) : "--------")
                .append('\n');

        if (signal.hasSender) {
            sb.append("sent by pid ").append(signal.senderPid)
                    .append(", uid ").append(signal.senderUid).append('\n');
        }
    }

    private static void appendCauses(StringBuilder sb, List<Cause> causes) {
        if (causes == null) {
            return;
        }
        for (Cause cause : causes) {
            if (cause != null) {
                appendPlain(sb, "Cause", cause.humanReadable);
            }
        }
    }

    private static void appendBacktrace(StringBuilder sb, TombstoneThread thread) {
        List<String> note = thread.backtraceNote;
        if (note != null) {
            for (String line : note) {
                appendPlain(sb, "Note", line);
            }
        }

        List<BacktraceFrame> backtrace = thread.backtrace;
        if (backtrace == null || backtrace.isEmpty()) {
            sb.append("backtrace:\n      (no backtrace available)\n");
            return;
        }

        sb.append(backtrace.size()).append(" total frames\n");
        sb.append("backtrace:\n");

        // Frames must stay contiguous: ndk-stack ends the dump at the first non-frame line
        // after a frame, so nothing may be interleaved here.
        int rendered = Math.min(backtrace.size(), MAX_BACKTRACE_FRAMES);
        for (int i = 0; i < rendered; i++) {
            appendFrame(sb, i, backtrace.get(i));
        }
        if (backtrace.size() > rendered) {
            sb.append("      ... ").append(backtrace.size() - rendered)
                    .append(" more frames omitted\n");
        }
    }

    /**
     * Mirrors debuggerd's frame layout, which uses the file-relative pc rather than the absolute
     * one so the offset is what a symbolizer expects:
     * {@code      #00 pc 000000000004c4c8  /system/lib64/libc.so (abort+164) (BuildId: ...)}
     */
    private static void appendFrame(StringBuilder sb, int index, BacktraceFrame frame) {
        if (frame == null) {
            return;
        }

        // Lowercase hex and the leading indent both matter: ndk-stack's frame pattern is
        // ".* +(#[0-9]+) +pc ([0-9a-f]+) +(([^ ]+).*)".
        sb.append(String.format(Locale.ROOT, "      #%02d pc %016x  ", index, frame.relPc));

        // Always a non-space token here, or the line stops matching and the dump ends early.
        sb.append(isPresent(frame.fileName) ? frame.fileName : UNKNOWN_MAP);

        if (isPresent(frame.functionName)) {
            sb.append(" (").append(frame.functionName);
            if (frame.functionOffset != 0) {
                sb.append('+').append(frame.functionOffset);
            }
            sb.append(')');
        }
        if (isPresent(frame.buildId)) {
            sb.append(" (BuildId: ").append(frame.buildId).append(')');
        }
        sb.append('\n');
    }

    /**
     * The bulk sections — memory mappings, log buffers, open fds — are counted rather than
     * dumped. They are what makes a tombstone hundreds of KB, and none of it helps ndk-stack.
     */
    private static void appendCounts(StringBuilder sb, Tombstone tombstone) {
        sb.append("sections: ").append(size(tombstone.memoryMappings)).append(" memory mappings, ")
                .append(size(tombstone.logBuffers)).append(" log buffers, ")
                .append(size(tombstone.openFds)).append(" open fds, ")
                .append(size(tombstone.crashDetails)).append(" crash details\n");
    }

    private static String commandLine(Tombstone tombstone) {
        List<String> commandLine = tombstone.commandLine;
        if (commandLine == null || commandLine.isEmpty()) {
            return null;
        }
        // Joined by hand rather than with String.join: that is API 26 and minSdk here is 24,
        // which trips lint's NewApi check even though this path only runs on API 31+.
        StringBuilder joined = new StringBuilder();
        for (String arg : commandLine) {
            if (joined.length() > 0) {
                joined.append(' ');
            }
            joined.append(arg);
        }
        return joined.toString();
    }

    private static String processLabel(Tombstone tombstone) {
        String cmdline = commandLine(tombstone);
        if (isPresent(cmdline)) {
            return cmdline;
        }
        return isPresent(tombstone.executableName) ? tombstone.executableName : UNKNOWN_MAP;
    }

    /** debuggerd's ABI spelling for the decoded architecture. */
    private static String toAbi(Architecture arch) {
        if (arch == null) {
            return null;
        }
        switch (arch) {
            case ARM32:
                return "arm";
            case ARM64:
                return "arm64";
            case X86:
                return "x86";
            case X86_64:
                return "x86_64";
            case RISCV64:
                return "riscv64";
            default:
                return null;
        }
    }

    /** {@code Label: 'value'} — debuggerd quotes these fields. */
    private static void appendQuoted(StringBuilder sb, String label, String value) {
        if (isPresent(value)) {
            sb.append(label).append(": '").append(value).append("'\n");
        }
    }

    /** {@code Label: value} — unquoted fields. */
    private static void appendPlain(StringBuilder sb, String label, String value) {
        // Fields absent from an older tombstone schema decode to empty strings, not nulls;
        // skip both so the report only carries what the OS actually reported.
        if (isPresent(value)) {
            sb.append(label).append(": ").append(value).append('\n');
        }
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isEmpty();
    }

    private static int size(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private static String hex(long value) {
        return String.format(Locale.ROOT, "0x%x", value);
    }
}
