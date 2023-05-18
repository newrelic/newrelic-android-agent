/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.rum.contentprovider;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;

import com.newrelic.agent.android.SpyContext;
import com.newrelic.agent.android.rum.AppApplicationLifeCycle;
import com.newrelic.agent.android.rum.AppTracer;

import org.junit.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowContentResolver;

@RunWith(RobolectricTestRunner.class)
public class AppContentProviderTest {
    private final static String AUTHORITY = "com.newrelic.agent.android.rum.contentprovider.NewRelicAppContentProvider";

    private ContentResolver resolver;
    private SpyContext contextSpy = new SpyContext();
    private Application application = spy((Application) contextSpy.getContext());
    private NewRelicAppContentProvider contentProvider;

    @Before
    public void setUp() throws Exception {
        resolver = contextSpy.getContext().getContentResolver();
        contentProvider = spy(new NewRelicAppContentProvider());
        contentProvider.appApplicationLifeCycle = spy(new AppApplicationLifeCycle());

        when(contentProvider.getContext()).thenReturn(application);

        ShadowContentResolver.registerProviderInternal(AUTHORITY, contentProvider);
    }

    @Test
    public void testOnCreate() {
        Assert.assertEquals(0, AppTracer.getInstance().getContentProviderStartedTime(), 0);
        Assert.assertTrue(contentProvider.onCreate());
        Assert.assertTrue(AppTracer.getInstance().getContentProviderStartedTime() != 0);
        verify(contentProvider.appApplicationLifeCycle, times(1)).onColdStartInitiated(any(Context.class));
        verify(application, times(1)).registerActivityLifecycleCallbacks(any(AppApplicationLifeCycle.class));
    }
}
