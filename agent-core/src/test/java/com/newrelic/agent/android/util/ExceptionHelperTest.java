/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.net.ssl.SSLException;

@RunWith(JUnit4.class)
public class ExceptionHelperTest {
    @Test
    public void exceptionToErrorCodeTest() {
        int errorCode = ExceptionHelper.NSURLErrorUnknown;
        UnknownHostException e1 = new UnknownHostException();
        int v1 = ExceptionHelper.exceptionToErrorCode(e1);
        Assert.assertEquals(v1, ExceptionHelper.NSURLErrorDNSLookupFailed);

        SocketTimeoutException e2 = new SocketTimeoutException();
        int v2 = ExceptionHelper.exceptionToErrorCode(e2);
        Assert.assertEquals(v2, ExceptionHelper.NSURLErrorTimedOut);

        ConnectException e3 = new ConnectException();
        int v3 = ExceptionHelper.exceptionToErrorCode(e3);
        Assert.assertEquals(v3, ExceptionHelper.NSURLErrorCannotConnectToHost);

        MalformedURLException e4 = new MalformedURLException();
        int v4 = ExceptionHelper.exceptionToErrorCode(e4);
        Assert.assertEquals(v4, ExceptionHelper.NSURLErrorBadURL);

        SSLException e5 = new SSLException("Test");
        int v5 = ExceptionHelper.exceptionToErrorCode(e5);
        Assert.assertEquals(v5, ExceptionHelper.NSURLErrorSecureConnectionFailed);

        FileNotFoundException e6 = new FileNotFoundException();
        int v6 = ExceptionHelper.exceptionToErrorCode(e6);
        Assert.assertEquals(v6, ExceptionHelper.NRURLErrorFileDoesNotExist);

        EOFException e7 = new EOFException();
        int v7 = ExceptionHelper.exceptionToErrorCode(e7);
        Assert.assertEquals(v7, ExceptionHelper.NSURLErrorRequestBodyStreamExhausted);

        IOException e8 = new IOException();
        int v8 = ExceptionHelper.exceptionToErrorCode(e8);
        Assert.assertEquals(v8, errorCode);

        RuntimeException e9 = new RuntimeException();
        int v9 = ExceptionHelper.exceptionToErrorCode(e9);
        Assert.assertEquals(v9, errorCode);
    }

    @Test
    public void testRecordSupportabilityMetric() {
        IOException e1 = new IOException();
        StackTraceElement[] st1 = new StackTraceElement[0];
        e1.setStackTrace(st1);
        ExceptionHelper.recordSupportabilityMetric(e1, "IOException");

        IOException e2 = new IOException("This is an IOException");
        ExceptionHelper.recordSupportabilityMetric(e2, "IOException");

        RuntimeException e3 = new RuntimeException();
        StackTraceElement[] st3 = new StackTraceElement[0];
        e3.setStackTrace(st3);
        ExceptionHelper.recordSupportabilityMetric(e3, "RuntimeException");

        RuntimeException e4 = new RuntimeException("This is a RuntimeException");
        ExceptionHelper.recordSupportabilityMetric(e4, "RuntimeException");
    }
}
