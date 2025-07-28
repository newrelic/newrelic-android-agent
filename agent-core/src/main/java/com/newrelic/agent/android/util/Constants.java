/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

public final class Constants {

    public static final String INSTRUMENTATION_PROVIDER = "instrumentation.provider";
    public static final String INSTRUMENTATION_VERSION = "instrumentation.version";
    public static final String INSTRUMENTATION_NAME = "instrumentation.name";
    public static final String INSTRUMENTATION_COLLECTOR_NAME = "collector.name";
    public static final String INSTRUMENTATION_PROVIDER_ATTRIBUTE = "mobile";
    public static final String INSTRUMENTATION_ANDROID_NAME = "AndroidAgent";


    public final class Transactions {
        public static final String CONTENT_LENGTH = "content_length";
        public static final String CONTENT_TYPE = "content_type";
    }

    public static final class ApolloGraphQLHeader {
        public static final String OPERATION_NAME = "X-APOLLO-OPERATION-NAME";
        public static final String OPERATION_TYPE = "X-APOLLO-OPERATION-TYPE";
        public static final String OPERATION_ID = "X-APOLLO-OPERATION-ID";

    }

    public static final class SessionReplay {
        public static final String SESSION_REPLAY_DATA_DIR = "newrelic/sessionReplay/";
        public static final String SESSION_REPLAY_FILE_MASK = "sessionReplaydata%s.%s";
        public static final String IS_FIRST_CHUNK = "isFirstChunk";
        public static final String HAS_META = "hasMeta";
        public static final String ENTITY_GUID = "entityGuid";
        public static final String RRWEB_VERSION = "rrweb.version";
        public static final String DECOMPRESSED_BYTES = "decompressedBytes";
        public static final String PAYLOAD_TYPE = "payload.type";
        public static final String REPLAY_FIRST_TIMESTAMP = "replay.firstTimestamp";
        public static final String REPLAY_LAST_TIMESTAMP = "replay.lastTimestamp";
        public static final String CONTENT_ENCODING = "content_encoding";
        public static final String APP_VERSION = "appVersion";
        public static final String SESSION_ID = "sessionId";
        public static final String FIRST_TIMESTAMP = "firstTimestamp";
        public static final String LAST_TIMESTAMP = "lastTimestamp";

        // Attribute values
        public static final String RRWEB_VERSION_VALUE = "^2.0.0-alpha.17";
        public static final String PAYLOAD_TYPE_STANDARD = "standard";
        public static final String CONTENT_ENCODING_GZIP = "gzip";

        // URL parameters
        public static final String URL_TYPE_PARAM = "type=SessionReplay";
        public static final String URL_APP_ID_PARAM = "app_id=";
        public static final String URL_PROTOCOL_VERSION_PARAM = "protocol_version=0";
        public static final String URL_TIMESTAMP_PARAM = "timestamp=";
        public static final String URL_ATTRIBUTES_PARAM = "attributes=";

        // URL encoding
        public static final String URL_ENCODED_AMPERSAND = "%26";
        public static final String URL_ENCODED_EQUALS = "%3D";
    }

    public final class Network {
        public static final String APPLICATION_LICENSE_HEADER = "X-App-License-Key";
        public static final String APPLICATION_ID_HEADER = "X-APPLICATION-ID";
        public static final String APP_DATA_HEADER = "X-NewRelic-App-Data";
        public static final String APP_VERSION_HEADER = "X-NewRelic-App-Version";
        public static final String CONNECT_TIME_HEADER = "X-NewRelic-Connect-Time";
        public static final String CROSS_PROCESS_ID_HEADER = "X-NewRelic-ID";
        public static final String DEVICE_OS_NAME_HEADER = "X-NewRelic-OS-Name";
        public static final String ACCOUNT_ID_HEADER = "X-NewRelic-Account-Id";
        public static final String TRUSTED_ACCOUNT_ID_HEADER = "X-NewRelic-Trusted-Account-Id";
        public static final String ENTITY_GUID_HEADER = "X-NewRelic-Entity-Guid";
        public static final String SESSION_ID_HEADER = "X-NewRelic-Session";
        public static final String AGENT_CONFIGURATION_HEADER = "X-NewRelic-AgentConfiguration";

        public static final String CONTENT_TYPE_HEADER = "Content-Type";
        public static final String CONTENT_ENCODING_HEADER = "Content-Encoding";
        public static final String CONTENT_LENGTH_HEADER = "Content-Length";
        public static final String USER_AGENT_HEADER = "User-Agent";
        public static final String HOST_HEADER = "Host";

        public static final long MAX_PAYLOAD_SIZE = 1000000; //bytes

        public final class ContentType {
            public static final String URL_ENCODED = "application/x-www-form-urlencoded";
            public static final String MULTIPART_FORM_DATA = "multipart/form-data";
            public static final String JSON = "application/json";
            public static final String OCTET_STREAM = "application/octet-stream";
            public static final String GZIP = "application/gzip";
        }

        public final class Encoding {
            public static final String DEFLATE = "deflate";
            public static final String IDENTITY = "identity";
            public static final String GZIP = "gzip";
        }
    }
}
