/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

import com.newrelic.agent.android.harvest.AgentHealth;
import com.newrelic.agent.android.harvest.AgentHealthException;
import com.newrelic.agent.android.harvest.type.HarvestErrorCodes;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.net.ssl.SSLException;

public class ExceptionHelper implements HarvestErrorCodes {

    private static final AgentLog log = AgentLogManager.getAgentLog();

    public static int exceptionToErrorCode(Exception e) {
        int errorCode = NSURLErrorUnknown;

        log.debug("ExceptionHelper: exception " + e.getClass().getName() + " to error code.");

        if (e instanceof UnknownHostException) {
            errorCode = NSURLErrorDNSLookupFailed;
        } else if (e instanceof SocketTimeoutException) {
            errorCode = NSURLErrorTimedOut;
        } else if (e instanceof ConnectException) {
            // This will gracefully handle HttpHostConnectException too.
            errorCode = NSURLErrorCannotConnectToHost;
        } else if (e instanceof MalformedURLException) {
            errorCode = NSURLErrorBadURL;
        } else if (e instanceof SSLException) {
            errorCode = NSURLErrorSecureConnectionFailed;
        } else if (e instanceof FileNotFoundException) {
            errorCode = NRURLErrorFileDoesNotExist;
        } else if (e instanceof EOFException) {
            errorCode = NSURLErrorRequestBodyStreamExhausted;
        } else if (e instanceof IOException) {
            recordSupportabilityMetric(e, "IOException");
        } else if (e instanceof RuntimeException) {
            recordSupportabilityMetric(e, "RuntimeException");
        }

        return errorCode;
    }

    public static void recordSupportabilityMetric(final Exception e, final String baseExceptionKey) {
        final AgentHealthException agentHealthException = new AgentHealthException(e);
        // The unhandled exception may represent a set of possible exceptions,
        // so dump out as much detail as available
        if (agentHealthException.getStackTrace() != null & agentHealthException.getStackTrace().length > 0) {
            StackTraceElement topTraceElement = agentHealthException.getStackTrace()[0];
            log.error(String.format("ExceptionHelper: %s:%s(%s:%s) %s[%s] %s",
                    agentHealthException.getSourceClass(), agentHealthException.getSourceMethod(),
                    topTraceElement.getFileName(), topTraceElement.getLineNumber(),
                    baseExceptionKey, agentHealthException.getExceptionClass(),
                    agentHealthException.getMessage()));

            AgentHealth.noticeException(agentHealthException, baseExceptionKey);
        }
    }
}
