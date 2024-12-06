/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

public final class Constants {

    public final class Transactions {
        public static final String CONTENT_LENGTH = "content_length";
        public static final String CONTENT_TYPE = "content_type";
    }

    public static final class ApolloGraphQLHeader {
        public static final String OPERATION_NAME = "X-APOLLO-OPERATION-NAME";
        public static final String OPERATION_TYPE = "X-APOLLO-OPERATION-TYPE";
        public static final String OPERATION_ID = "X-APOLLO-OPERATION-ID";

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
