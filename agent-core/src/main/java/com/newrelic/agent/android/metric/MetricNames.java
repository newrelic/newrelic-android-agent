/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.metric;

public class MetricNames {
    // Top-level metric spaces
    public static final String METRIC_ACTIVITY = "Mobile/Activity/";
    public static final String ACTIVITY_NETWORK_METRIC_COUNT_FORMAT = METRIC_ACTIVITY + "Network/<activity>/Count";
    public static final String ACTIVITY_NETWORK_METRIC_TIME_FORMAT = METRIC_ACTIVITY + "Network/<activity>/Time";

    public static final String METRIC_MOBILE = "Mobile/App/";
    public static final String MOBILE_APP_INSTALL = METRIC_MOBILE + "Install";
    public static final String MOBILE_APP_UPGRADE = METRIC_MOBILE + "Upgrade";
    public static final String METRIC_UUID_CREATED = METRIC_MOBILE + "UUID/Created";
    public static final String METRIC_UUID_RECOVERED = METRIC_MOBILE + "UUID/Recovered";
    public static final String METRIC_UUID_OVERRIDDEN = METRIC_MOBILE + "UUID/Overridden";
    public static final String METRIC_UUID_TRUNCATED = METRIC_MOBILE + "UUID/Truncated";

    public static final String METRIC_SESSION = "Session/";
    public static final String SESSION_START = METRIC_SESSION + "Start";
    public static final String SESSION_DURATION = METRIC_SESSION + "Duration";

    public static final String SUPPORTABILITY_EVENTS = "Supportability/Events/";
    public static final String SUPPORTABILITY_EVENT_ADDED = SUPPORTABILITY_EVENTS + "Added";
    public static final String SUPPORTABILITY_EVENT_OVERFLOW = SUPPORTABILITY_EVENTS + "Overflow";
    public static final String SUPPORTABILITY_EVENT_EVICTED = SUPPORTABILITY_EVENTS + "Evicted";
    public static final String SUPPORTABILITY_EVENT_QUEUE_SIZE_EXCEEDED = SUPPORTABILITY_EVENTS + "Queue/Size/Exceeded";
    public static final String SUPPORTABILITY_EVENT_QUEUE_TIME_EXCEEDED = SUPPORTABILITY_EVENTS + "Queue/Time/Exceeded";
    public static final String SUPPORTABILITY_EVENT_SIZE_UNCOMPRESSED = SUPPORTABILITY_EVENTS + "Size/Uncompressed";

    public static final String SUPPORTABILITY_COLLECTOR = "Supportability/AgentHealth/Collector/";

    public static final String SUPPORTABILITY_AGENT = "Supportability/AgentHealth/";
    public static final String SUPPORTABILITY_TRANS_DROPPED = SUPPORTABILITY_AGENT + "TransactionsDropped";
    public static final String SUPPORTABILITY_ERRORS_DROPPED = SUPPORTABILITY_AGENT + "ErrorsDropped";
    public static final String SUPPORTABILITY_TRACES_IGNORED = SUPPORTABILITY_AGENT + "IgnoredTraces";
    public static final String SUPPORTABILITY_TRACES_DROPPED = SUPPORTABILITY_AGENT + "BigActivityTracesDropped";
    public static final String SUPPORTABILITY_TRACES_HEALTHY = SUPPORTABILITY_AGENT + "HealthyActivityTraces";
    public static final String SUPPORTABILITY_TRACES_UNHEALTHY = SUPPORTABILITY_AGENT + "UnhealthyActivityTraces";
    public static final String SUPPORTABILITY_HARVEST_ON_MAIN_THREAD = SUPPORTABILITY_AGENT + "HarvestOnMainThread";
    public static final String SUPPORTABILITY_PAYLOAD_REMOVED_STALE = SUPPORTABILITY_AGENT + "Payload/Removed/Stale";
    public static final String SUPPORTABILITY_SESSION_INVALID_DURATION = SUPPORTABILITY_AGENT + "Session/InvalidDuration";
    public static final String SUPPORTABILITY_RESPONSE_TIME_INVALID_DURATION = SUPPORTABILITY_AGENT + "Network/Request/ResponseTime/InvalidDuration";

    public static final String SUPPORTABILITY_CRASH = SUPPORTABILITY_AGENT + "Crash/";
    public static final String SUPPORTABILITY_CRASH_RECURSIVE_HANDLER = SUPPORTABILITY_CRASH + "UncaughtExceptionHandler/Recursion";
    public static final String SUPPORTABILITY_CRASH_UNCAUGHT_HANDLER = SUPPORTABILITY_AGENT + "UncaughtExceptionHandler/<name>";
    public static final String SUPPORTABILITY_CRASH_UPLOAD_TIME = SUPPORTABILITY_CRASH + "UploadTime";
    public static final String SUPPORTABILITY_CRASH_UPLOAD_TIMEOUT = SUPPORTABILITY_CRASH + "UploadTimeOut";
    public static final String SUPPORTABILITY_CRASH_UPLOAD_THROTTLED = SUPPORTABILITY_CRASH + "UploadThrottled";
    public static final String SUPPORTABILITY_CRASH_FAILED_UPLOAD = SUPPORTABILITY_CRASH + "FailedUpload";
    public static final String SUPPORTABILITY_CRASH_REMOVED_STALE = SUPPORTABILITY_CRASH + "Removed/Stale";
    public static final String SUPPORTABILITY_CRASH_REMOVED_REJECTED = SUPPORTABILITY_CRASH + "Removed/Rejected";
    public static final String SUPPORTABILITY_CRASH_SIZE_UNCOMPRESSED = SUPPORTABILITY_CRASH + "Size/Uncompressed";
    public static final String SUPPORTABILITY_CRASH_INVALID_BUILDID = SUPPORTABILITY_CRASH + "InvalidBuildId";

    public static final String SUPPORTABILITY_HEX = SUPPORTABILITY_AGENT + "HEx/";
    public static final String SUPPORTABILITY_HEX_UPLOAD_TIME = SUPPORTABILITY_HEX + "UploadTime";
    public static final String SUPPORTABILITY_HEX_UPLOAD_TIMEOUT = SUPPORTABILITY_HEX + "UploadTimeOut";
    public static final String SUPPORTABILITY_HEX_UPLOAD_THROTTLED = SUPPORTABILITY_HEX + "UploadThrottled";
    public static final String SUPPORTABILITY_HEX_FAILED_UPLOAD = SUPPORTABILITY_HEX + "FailedUpload";

    public static final String SUPPORTABILITY_MOBILE_ANDROID = "Supportability/Mobile/Android/";
    public static final String SUPPORTABILITY_NDK = SUPPORTABILITY_MOBILE_ANDROID + "NDK/";
    public static final String SUPPORTABILITY_NDK_INIT = SUPPORTABILITY_NDK + "Init";
    public static final String SUPPORTABILITY_NDK_START = SUPPORTABILITY_NDK + "Start";
    public static final String SUPPORTABILITY_NDK_STOP = SUPPORTABILITY_NDK + "Stop";
    public static final String SUPPORTABILITY_NDK_ROOTED_DEVICE = SUPPORTABILITY_NDK + "RootedDevice";
    public static final String SUPPORTABILITY_NDK_REPORTS_CRASH = SUPPORTABILITY_NDK + "Reports/NativeCrash";
    public static final String SUPPORTABILITY_NDK_REPORTS_ANR = SUPPORTABILITY_NDK + "Reports/ANR";
    public static final String SUPPORTABILITY_NDK_REPORTS_EXCEPTION = SUPPORTABILITY_NDK + "Reports/NativeException";
    public static final String SUPPORTABILITY_NDK_REPORTS_FLUSH = SUPPORTABILITY_NDK + "Reports/Flush";

    public static final String SUPPORTABILITY_DATA_TOKEN = SUPPORTABILITY_AGENT + "DataToken/";
    public static final String SUPPORTABILITY_INVALID_DATA_TOKEN = SUPPORTABILITY_DATA_TOKEN + "Invalid";

    public static final String TAG_NAME = "<name>";
    public static final String TAG_STATE = "<state>";
    public static final String TAG_FRAMEWORK = "<framework>";
    public static final String TAG_FRAMEWORK_VERSION = "<frameworkVersion>";

    public static final String SUPPORTABILITY_API = SUPPORTABILITY_MOBILE_ANDROID + "<framework>/<frameworkVersion>/API/<name>";
    public static final String SUPPORTABILITY_DEPRECATED = SUPPORTABILITY_MOBILE_ANDROID + "<framework>/<frameworkVersion>/API/Deprecated/<name>";

    public static final String TAG_DESTINATION = "<destination>";
    public static final String TAG_SUBDESTINATION = "<subdestination>";

    public static final String METRIC_DATA_USAGE_COLLECTOR = "Collector";
    public static final String SUPPORTABILITY_OUTPUT_BYTES = "Output/Bytes";
    public static final String SUPPORTABILITY_DESTINATION_OUTPUT_BYTES = SUPPORTABILITY_MOBILE_ANDROID + "<framework>/<destination>/" + SUPPORTABILITY_OUTPUT_BYTES;
    public static final String SUPPORTABILITY_SUBDESTINATION_OUTPUT_BYTES = SUPPORTABILITY_MOBILE_ANDROID + "<framework>/<destination>/<subdestination>/" + SUPPORTABILITY_OUTPUT_BYTES;

    public static final String METRIC_APP_LAUNCH = "AppLaunch/";
    public static final String APP_LAUNCH_COLD = METRIC_APP_LAUNCH + "Cold";
    public static final String APP_LAUNCH_HOT = METRIC_APP_LAUNCH + "Hot";
    public static final String OFFLINE_STORAGE_HANDLED_EXCEPTION = "OfflineStorage/HandledExceptions";
    public static final String OFFLINE_STORAGE_CRASH = "OfflineStorage/Crashes";
}
