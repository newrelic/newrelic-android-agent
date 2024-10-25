package com.newrelic.agent.android.aei;

import com.newrelic.agent.android.logging.AgentLogManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Model for the collected AEI trace data.
 *
 * The AEI trace filters and reduces the system trace returned
 * by ApplicationExitInfo.getTraceInputStream() to just the data expected at ingest.
 */
public class AEITrace {
    final static Pattern TRACE_HEADER_REGEXP = Pattern.compile(".*----- pid (?<pid>.\\d+) at (?<timeCreated>\\d{4}-\\d{2}-\\d{2}[T ]{0,}[0-9:.-]+) -----(?<body>.*$)");
    final static Pattern TRACE_THREADS_REGEXP = Pattern.compile(".*DALVIK THREADS \\((?<threadCnt>\\d+)\\):\\s(.*)----- end (\\d+) -----", Pattern.MULTILINE);
    final static Pattern TRACE_THREAD_ID_REGEXP = Pattern.compile("^\"(?<threadName>.*)\" (.*)prio=(\\d+).*$");

    final ArrayList<String> threads;
    String pid;
    String createTime;

    public AEITrace() {
        threads = new ArrayList<String>();
    }

    public AEITrace(File filePath) {
        this();
    }

    public AEITrace(int pid, File artifact) {
        this(artifact);
        this.pid = String.valueOf(pid);
    }

    public AEITrace decomposeFromSystemTrace(String sysTrace) {

        // replace newlines with tabs to parse the entire trace as a tab delimited string
        sysTrace = sysTrace.strip().replace('\n', '\t');

        // ----- pid 4473 at 2024-02-15 23:37:45.593138790-0800 -----
        Matcher headerMatcher = TRACE_HEADER_REGEXP.matcher(sysTrace);
        if (headerMatcher.matches()) {
            if (!(null == pid || pid.isBlank())) {
                pid = headerMatcher.group(1);
            }
            createTime = headerMatcher.group(2).strip();
        } else {
            AgentLogManager.getAgentLog().debug("The trace file does not contain the expected file header.");
        }

        // DALVIK THREADS (<nThreads>):\n<thread 0>\n<thread 1>...\n<thread n>\n----- end (<pid>) -----
        Matcher threadsMatcher = TRACE_THREADS_REGEXP.matcher(sysTrace);
        if (threadsMatcher.matches()) {
            String threadData = threadsMatcher.group(2).strip();
            parseThreadsData(threadData);
        } else {
            AgentLogManager.getAgentLog().error("The trace file does not contain the expected threads data.");
            // TODO try to parse the file anyway?
            // parseThreadsData(sysTrace);
        }

        return this;
    }

    private ArrayList<String> parseThreadsData(String threadData) {
        if (!(threadData == null || threadData.isEmpty())) {
            threads.addAll(List.of(threadData.split("\t\t")));
            threads.removeIf(s -> !TRACE_THREAD_ID_REGEXP.matcher(s).matches());
            threads.replaceAll(s -> {
                String[] frames = s.split("\t");
                return Arrays.stream(frames)
                        .filter(s1 -> !s1.trim().matches("[(|-].*"))
                        .collect(Collectors.joining("\n"));
            });
        }

        return threads;
    }

    public ArrayList<String> getThreads() {
        return threads;
    }

    public String getPid() {
        return pid;
    }

    public String getCreateTime() {
        return createTime;
    }

    @Override
    public String toString() {
        // transform trace data per DEM spec
        String flattenedThreads = threads.stream()
                .collect(Collectors.joining("\n"));

        return flattenedThreads;
    }
}
