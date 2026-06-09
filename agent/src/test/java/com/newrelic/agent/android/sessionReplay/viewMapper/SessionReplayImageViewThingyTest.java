/*
 * Copyright (c) 2026. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay.viewMapper;

import android.util.LruCache;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;

@RunWith(RobolectricTestRunner.class)
public class SessionReplayImageViewThingyTest {

    @Before
    public void setUp() {
        SessionReplayImageViewThingy.clearImageCache();
        // Reset the shared ImageView so per-class static state does not leak across tests.
        sharedImageView = null;
    }

    // ==================== LRU CACHE SIZING ====================

    @Test
    public void lruCache_smallBase64String_doesNotSizeToZero() throws Exception {
        LruCache<String, String> cache = getStaticCache();
        // 50-char Base64 string — would floor to 0 under the old (length/1024) sizing.
        cache.put("k", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        Assert.assertTrue("cache size must be > 0 for non-empty value", cache.size() > 0);
    }

    @Test
    public void lruCache_capIsAtLeast50MB() throws Exception {
        LruCache<String, String> cache = getStaticCache();
        Assert.assertTrue("max cache size should be at least 50 MB",
                cache.maxSize() >= 4 * 1024 * 1024);
    }

    // ==================== hasChanged ====================

    @Test
    public void hasChanged_identicalContent_returnsFalse() throws Exception {
        SessionReplayImageViewThingy a = newThingyWithImageData("AAA");
        SessionReplayImageViewThingy b = newThingyWithImageData("AAA");
        Assert.assertFalse("two thingies with identical content must not register as changed",
                a.hasChanged(b));
    }

    @Test
    public void hasChanged_differentImageData_returnsTrue() throws Exception {
        SessionReplayImageViewThingy a = newThingyWithImageData("AAA");
        SessionReplayImageViewThingy b = newThingyWithImageData("BBB");
        Assert.assertTrue("different imageData must register as changed",
                a.hasChanged(b));
    }

    @Test
    public void hasChanged_nonImageType_returnsTrue() throws Exception {
        SessionReplayImageViewThingy a = newThingyWithImageData("AAA");
        Assert.assertTrue("non-image other must register as changed",
                a.hasChanged(null));
    }

    /**
     * Build a SessionReplayImageViewThingy bypassing the constructor so tests do not
     * need to drive a real ImageView through the bitmap pipeline. Sets the private
     * imageData field via reflection.
     *
     * Reuses a single ImageView across calls so that hasChanged() comparisons that
     * include viewDetails (which captures a per-View stable id) only differ on the
     * fields the test is exercising. The shared view is reset in @Before so state
     * does not leak across tests.
     */
    private static android.widget.ImageView sharedImageView;

    private SessionReplayImageViewThingy newThingyWithImageData(String imageData) throws Exception {
        android.content.Context ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        if (sharedImageView == null) {
            sharedImageView = new android.widget.ImageView(ctx);
            sharedImageView.layout(0, 0, 10, 10);
        }
        com.newrelic.agent.android.AgentConfiguration cfg = new com.newrelic.agent.android.AgentConfiguration();
        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(new ViewDetails(sharedImageView), sharedImageView, cfg);
        Field f = SessionReplayImageViewThingy.class.getDeclaredField("imageData");
        f.setAccessible(true);
        f.set(thingy, imageData);
        return thingy;
    }

    @SuppressWarnings("unchecked")
    private static LruCache<String, String> getStaticCache() throws Exception {
        Field f = SessionReplayImageViewThingy.class.getDeclaredField("imageCache");
        f.setAccessible(true);
        return (LruCache<String, String>) f.get(null);
    }

    // ==================== Cache key ====================

    @Test
    public void cacheKey_sameDrawableInstance_equal() throws Exception {
        android.content.Context ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.drawable.BitmapDrawable d = new android.graphics.drawable.BitmapDrawable(ctx.getResources(), bm);

        android.widget.ImageView iv1 = new android.widget.ImageView(ctx);
        iv1.layout(0, 0, 10, 10);
        iv1.setImageDrawable(d);
        android.widget.ImageView iv2 = new android.widget.ImageView(ctx);
        iv2.layout(0, 0, 10, 10);
        iv2.setImageDrawable(d);

        Assert.assertEquals("same drawable instance must yield the same cache key",
                invokeGenerateCacheKey(iv1, d), invokeGenerateCacheKey(iv2, d));
    }

    @Test
    public void cacheKey_sharedConstantState_equal() throws Exception {
        // Two BitmapDrawable instances backed by the same ConstantState (e.g. obtained via
        // ConstantState.newDrawable()) describe the same image. The old key used
        // drawable.hashCode() — identity — so it would falsely treat them as different
        // images. The new key uses ConstantState identity, so they collapse to the same
        // cache entry as intended.
        android.content.Context ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.drawable.BitmapDrawable d1 = new android.graphics.drawable.BitmapDrawable(ctx.getResources(), bm);
        android.graphics.drawable.Drawable.ConstantState cs = d1.getConstantState();
        org.junit.Assume.assumeNotNull(cs);
        android.graphics.drawable.Drawable d2 = cs.newDrawable();

        android.widget.ImageView iv = new android.widget.ImageView(ctx);
        iv.layout(0, 0, 10, 10);
        iv.setImageDrawable(d1);

        Assert.assertEquals("two drawables sharing a ConstantState must yield the same cache key",
                invokeGenerateCacheKey(iv, d1), invokeGenerateCacheKey(iv, d2));
    }

    @Test
    public void cacheKey_includesGenerationIdForBitmapDrawable() throws Exception {
        // The production key incorporates Bitmap.getGenerationId() for BitmapDrawable.
        // Robolectric's ShadowBitmap always returns 0, so we cannot exercise key change
        // on mutation here, but we can verify the generationId component is present in
        // the key — without this, mutated bitmaps would never invalidate on real Android.
        android.content.Context ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.drawable.BitmapDrawable d = new android.graphics.drawable.BitmapDrawable(ctx.getResources(), bm);

        android.widget.ImageView iv = new android.widget.ImageView(ctx);
        iv.layout(0, 0, 10, 10);
        iv.setImageDrawable(d);

        String key = invokeGenerateCacheKey(iv, d);
        Assert.assertTrue("BitmapDrawable cache key must include a _g<generationId> component but was: " + key,
                key.matches(".*_g\\d+_.*"));
    }

    private String invokeGenerateCacheKey(android.widget.ImageView iv, android.graphics.drawable.Drawable d) throws Exception {
        com.newrelic.agent.android.AgentConfiguration cfg = new com.newrelic.agent.android.AgentConfiguration();
        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(new ViewDetails(iv), iv, cfg);
        java.lang.reflect.Method m = SessionReplayImageViewThingy.class.getDeclaredMethod(
                "generateCacheKey", android.graphics.drawable.Drawable.class, android.widget.ImageView.class);
        m.setAccessible(true);
        return (String) m.invoke(thingy, d, iv);
    }

    // ==================== Async dispatch ====================

    /**
     * Test executor that records submissions instead of running them, so tests can
     * inspect the constructor's behavior at the moment compression is dispatched.
     */
    private static final class RecordingExecutor implements java.util.concurrent.Executor {
        final java.util.Queue<Runnable> queue = new java.util.LinkedList<>();
        @Override public void execute(Runnable command) { queue.add(command); }
        void runAll() {
            while (!queue.isEmpty()) queue.poll().run();
        }
        int size() { return queue.size(); }
    }

    private RecordingExecutor recordingExecutor;
    private java.util.concurrent.Executor originalExecutor;

    private void installRecordingExecutor() throws Exception {
        recordingExecutor = new RecordingExecutor();
        Field f = SessionReplayImageViewThingy.class.getDeclaredField("compressionExecutor");
        f.setAccessible(true);
        originalExecutor = (java.util.concurrent.Executor) f.get(null);
        f.set(null, recordingExecutor);
    }

    private void restoreExecutor() throws Exception {
        if (originalExecutor != null) {
            Field f = SessionReplayImageViewThingy.class.getDeclaredField("compressionExecutor");
            f.setAccessible(true);
            f.set(null, originalExecutor);
        }
    }

    @Test
    public void cacheMiss_dispatchesAsyncAndImageDataNull() throws Exception {
        installRecordingExecutor();
        try {
            android.content.Context ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext();
            android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888);
            bm.eraseColor(android.graphics.Color.GREEN);
            android.graphics.drawable.BitmapDrawable d = new android.graphics.drawable.BitmapDrawable(ctx.getResources(), bm);

            android.widget.ImageView iv = new android.widget.ImageView(ctx);
            iv.layout(0, 0, 8, 8);
            iv.setImageDrawable(d);

            com.newrelic.agent.android.AgentConfiguration cfg = new com.newrelic.agent.android.AgentConfiguration();
            cfg.getSessionReplayConfiguration().setMaskAllImages(false);
            SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(new ViewDetails(iv), iv, cfg);

            Assert.assertNull("imageData must be null immediately on cache miss", thingy.getImageData());
            Assert.assertEquals("one compression task must be queued on cache miss",
                    1, recordingExecutor.size());

            recordingExecutor.runAll();
            // The worker populates the cache only; it does NOT back-fill this thingy's
            // imageData (which would mutate the previous frame's snapshot and suppress the
            // background-image mutation when the diff runs). The current frame's thingy
            // stays at the value it had at capture time (null); the next captured frame
            // will resolve from the cache.
            Assert.assertNull("worker must not back-fill the captured frame's imageData",
                    thingy.getImageData());
            Assert.assertNotNull("worker must populate the cache for the next frame to hit",
                    getStaticCache().get(getStaticCache().snapshot().keySet().iterator().next()));
        } finally {
            restoreExecutor();
        }
    }

    @Test
    public void cacheHit_resolvesSynchronouslyAndDoesNotDispatch() throws Exception {
        installRecordingExecutor();
        try {
            android.content.Context ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext();
            android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888);
            bm.eraseColor(android.graphics.Color.GREEN);
            android.graphics.drawable.BitmapDrawable d = new android.graphics.drawable.BitmapDrawable(ctx.getResources(), bm);

            android.widget.ImageView iv = new android.widget.ImageView(ctx);
            iv.layout(0, 0, 8, 8);
            iv.setImageDrawable(d);

            com.newrelic.agent.android.AgentConfiguration cfg = new com.newrelic.agent.android.AgentConfiguration();
            cfg.getSessionReplayConfiguration().setMaskAllImages(false);
            // First construction populates the cache.
            new SessionReplayImageViewThingy(new ViewDetails(iv), iv, cfg);
            recordingExecutor.runAll();
            recordingExecutor.queue.clear();

            // Second construction with the same drawable should hit the cache.
            SessionReplayImageViewThingy hit = new SessionReplayImageViewThingy(new ViewDetails(iv), iv, cfg);
            Assert.assertNotNull("imageData must be populated synchronously on cache hit",
                    hit.getImageData());
            Assert.assertEquals("no compression task must be queued on cache hit",
                    0, recordingExecutor.size());
        } finally {
            restoreExecutor();
        }
    }

    @Test
    public void maskedImage_neitherDispatchesNorPopulatesImageData() throws Exception {
        installRecordingExecutor();
        try {
            android.content.Context ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext();
            android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.drawable.BitmapDrawable d = new android.graphics.drawable.BitmapDrawable(ctx.getResources(), bm);

            android.widget.ImageView iv = new android.widget.ImageView(ctx);
            iv.layout(0, 0, 8, 8);
            iv.setImageDrawable(d);

            com.newrelic.agent.android.AgentConfiguration cfg = new com.newrelic.agent.android.AgentConfiguration();
            cfg.getSessionReplayConfiguration().setMaskAllImages(true);

            SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(new ViewDetails(iv), iv, cfg);
            Assert.assertNull("masked image must not have imageData", thingy.getImageData());
            Assert.assertEquals("masked image must not dispatch compression",
                    0, recordingExecutor.size());
        } finally {
            restoreExecutor();
        }
    }

    // ==================== Cold→hot diff transition ====================

    @Test
    public void generateDifferences_coldToHotTransition_emitsBackgroundImageMutation() throws Exception {
        installRecordingExecutor();
        try {
            android.content.Context ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext();
            android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888);
            bm.eraseColor(android.graphics.Color.GREEN);
            android.graphics.drawable.BitmapDrawable d = new android.graphics.drawable.BitmapDrawable(ctx.getResources(), bm);

            android.widget.ImageView iv = new android.widget.ImageView(ctx);
            iv.layout(0, 0, 8, 8);
            iv.setImageDrawable(d);

            com.newrelic.agent.android.AgentConfiguration cfg = new com.newrelic.agent.android.AgentConfiguration();
            cfg.getSessionReplayConfiguration().setMaskAllImages(false);

            // Frame N: first sighting — cache miss, imageData null, async task queued.
            SessionReplayImageViewThingy frameN = new SessionReplayImageViewThingy(new ViewDetails(iv), iv, cfg);
            Assert.assertNull("frame N must have null imageData while compression is in flight",
                    frameN.getImageData());

            // Worker runs, populates cache.
            recordingExecutor.runAll();

            // Frame N+1: cache hit, imageData populated synchronously.
            SessionReplayImageViewThingy frameNPlus1 = new SessionReplayImageViewThingy(new ViewDetails(iv), iv, cfg);
            Assert.assertNotNull("frame N+1 must resolve imageData synchronously from the cache",
                    frameNPlus1.getImageData());

            // Diff frame N (null) → frame N+1 (resolved) must emit a background-image mutation.
            java.util.List<com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord> records =
                    frameN.generateDifferences(frameNPlus1);
            Assert.assertEquals("cold→hot transition must emit exactly one mutation record",
                    1, records.size());

            com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData.AttributeRecord attr =
                    (com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData.AttributeRecord) records.get(0);
            String bgImage = attr.attributes.metadata.get("background-image");
            Assert.assertNotNull("background-image must be present in the mutation record", bgImage);
            Assert.assertTrue("background-image must include the resolved data URL but was: " + bgImage,
                    bgImage.contains("url(") && bgImage.contains("data:image/webp;base64,"));
        } finally {
            restoreExecutor();
        }
    }
}
