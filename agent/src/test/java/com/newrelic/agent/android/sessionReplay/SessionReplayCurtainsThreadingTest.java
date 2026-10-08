/*
 * Copyright (c) 2026. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import android.os.Handler;
import android.os.Looper;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;

import curtains.Curtains;
import curtains.OnRootViewsChangedListener;

/**
 * Regression test for NR-608999: SessionReplay.startRecording()/stopRecording() used to
 * touch curtains.Curtains from both the main thread (via a posted Runnable) and the
 * caller's thread (via a synchronous call), racing on Curtains' internal
 * LazyThreadSafetyMode.NONE delegate and crashing with an NPE from
 * kotlin.UnsafeLazyImpl.getValue.
 *
 * These tests exercise the real (non-mocked) Curtains API from a background thread while
 * the main Looper is paused, asserting that no Curtains mutation is visible until the
 * paused main Looper is explicitly idled - i.e. that all Curtains access is confined to
 * the main thread.
 */
@RunWith(RobolectricTestRunner.class)
public class SessionReplayCurtainsThreadingTest {

    private ShadowLooper shadowLooper;
    private OnRootViewsChangedListener marker;

    @Before
    public void setUp() throws Exception {
        shadowLooper = Shadows.shadowOf(Looper.getMainLooper());
        shadowLooper.pause();

        setStaticField("uiThreadHandler", new Handler(Looper.getMainLooper()));
        setStaticField("viewDrawInterceptor", null);

        // Pre-warm Curtains' lazy on the main (test) thread first, same as the app-side
        // workaround described in NR-608999, so the assertions below aren't themselves
        // racing Curtains' one-time initialization.
        marker = (view, added) -> { };
        Curtains.getOnRootViewsChangedListeners().add(marker);
        Curtains.getOnRootViewsChangedListeners().remove(marker);
    }

    @After
    public void tearDown() {
        Curtains.getOnRootViewsChangedListeners().clear();
    }

    @Test
    public void stopRecording_clearsCurtainsListenersOnlyAfterMainThreadRuns() throws Exception {
        Curtains.getOnRootViewsChangedListeners().add(marker);
        Assert.assertEquals(1, Curtains.getOnRootViewsChangedListeners().size());

        Thread backgroundCaller = new Thread(SessionReplay::stopRecording);
        backgroundCaller.start();
        backgroundCaller.join();

        Assert.assertEquals(
                "stopRecording() must not clear Curtains listeners on the caller's thread; "
                        + "the clear() must be deferred to the main thread",
                1, Curtains.getOnRootViewsChangedListeners().size());

        shadowLooper.idle();

        Assert.assertEquals(
                "once the main thread processes the deferred work, listeners must be cleared",
                0, Curtains.getOnRootViewsChangedListeners().size());
    }

    @Test
    public void startRecording_registersCurtainsListenerOnlyAfterMainThreadRuns() throws Exception {
        Assert.assertEquals(0, Curtains.getOnRootViewsChangedListeners().size());

        Thread backgroundCaller = new Thread(() -> SessionReplay.startRecording(SessionReplayMode.ERROR));
        backgroundCaller.start();
        backgroundCaller.join();

        Assert.assertEquals(
                "startRecording() must not register the Curtains listener on the caller's thread; "
                        + "the add() must be deferred to the main thread",
                0, Curtains.getOnRootViewsChangedListeners().size());

        shadowLooper.idle();

        Assert.assertEquals(
                "once the main thread processes the deferred work, the listener must be registered",
                1, Curtains.getOnRootViewsChangedListeners().size());
    }

    private static void setStaticField(String name, Object value) throws Exception {
        Field field = SessionReplay.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
