/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest.type;

public interface HarvestErrorCodes {

    //
    // These error codes are based on the iOS NSURLConnection error codes.
    //
    // http://developer.apple.com/library/ios/#documentation/Cocoa/Reference/Foundation/Miscellaneous/Foundation_Constants/Reference/reference.html#//apple_ref/doc/uid/TP40003793-CH3g-SW40
    //
    // The values are shared with the iOS agent, so make sure to communicate any changes to values
    // to the iOS agent team.

    public static final int NSURLErrorUnknown                = -1;
    public static final int NSURLErrorBadURL                 = -1000;
    public static final int NSURLErrorTimedOut               = -1001;
    public static final int NSURLErrorCannotFindHost         = -1003;
    public static final int NSURLErrorCannotConnectToHost    = -1004;
    public static final int NSURLErrorDNSLookupFailed        = -1006;
    public static final int NSURLErrorBadServerResponse      = -1011;
    public static final int NSURLErrorRequestBodyStreamExhausted = -1021;
    public static final int NRURLErrorFileDoesNotExist       = -1100;
    public static final int NSURLErrorSecureConnectionFailed = -1200;
}
