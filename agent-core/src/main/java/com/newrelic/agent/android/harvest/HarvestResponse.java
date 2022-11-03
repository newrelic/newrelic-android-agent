/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

/**
 * A response from the collector. Contains a status code and response.
 */
public class HarvestResponse {
    private static final String DISABLE_STRING = "DISABLE_NEW_RELIC";

    /**
     * Enumeration which maps an integer status code to a logical response meaning.
     */
    public enum Code {
        OK(200),
        ACCEPTED(202),
        UNAUTHORIZED(401),
        FORBIDDEN(403),
        REQUEST_TIMEOUT(408),
        ENTITY_TOO_LARGE(413),
        UNSUPPORTED_MEDIA_TYPE(415),
        TOO_MANY_REQUESTS(429),
        INVALID_AGENT_ID(450),
        INTERNAL_SERVER_ERROR(500),
        UNKNOWN(-1);

        int statusCode;

        Code(int statusCode) {
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public boolean isError() {
            return !isOK();
        }

        public boolean isOK() {
            switch (this) {
                case OK:
                case ACCEPTED:
                    return true;
            }
            return false;
        }
    }

    private int statusCode;
    private String responseBody;
    private long responseTime;

    /**
     * Return the {@link Code} for this response.
     *
     * @return {@link Code} of the collector response.
     */
    public Code getResponseCode() {
        for (Code code : Code.values()) {
            if (code.getStatusCode() == statusCode)
                return code;
        }

        return Code.UNKNOWN;
    }

    /**
     * Returns whether or not the collector response is a 'disable command'. This command should disable
     * the agent. A disable command has a FORBIDDEN (403) code and "DISABLE_NEW_RELIC" as response BODY.
     *
     * @return true if the response is a disable command, false otherwise.
     */
    public boolean isDisableCommand() {
        return Code.FORBIDDEN == getResponseCode() && DISABLE_STRING.equals(getResponseBody());
    }

    /**
     * Returns true if this response is an error.
     *
     * @return true if the response is an error, false otherwise.
     */
    public boolean isError() {
        return statusCode >= 400;
    }

    public boolean isUnknown() {
        return getResponseCode() == Code.UNKNOWN;
    }

    /**
     * Returns true if this response is OK (not an error).
     *
     * @return true if the response is OK, false if it is an error.
     */
    public boolean isOK() {
        return statusCode == 200 || statusCode == 201;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public long getResponseTime() {
        return responseTime;
    }

    public void setResponseTime(long responseTime) {
        this.responseTime = responseTime;
    }
}
