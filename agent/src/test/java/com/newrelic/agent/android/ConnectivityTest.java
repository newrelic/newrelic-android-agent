/**
 * Copyright 2021 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import android.content.Context;

import com.newrelic.agent.android.api.common.CarrierType;
import com.newrelic.agent.android.util.Connectivity;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class ConnectivityTest {
    private Context contextSpy = new SpyContext().getContext();

    @Test
    public void carrierNameFromContext() throws Exception {
        String carrierName = Connectivity.carrierNameFromContext(contextSpy);
        Assert.assertEquals(carrierName, CarrierType.WIFI);
    }
}