# Async Image Compression Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move bitmap-to-Base64 compression off the main thread for both `SessionReplayImageViewThingy` (View) and `ComposeImageThingy` (Compose), and fix the LRU cache so the rrweb diff stays correct.

**Architecture:** Per-class single-thread executor performs compression off-main. The constructor checks the LRU cache synchronously on the main thread; on a hit the diff sees the resolved Base64 immediately, on a miss `imageData` is `null` for that frame and the worker fills the cache asynchronously so the next captured frame resolves from cache. The View-side cache key is replaced with `ConstantState` identity + `Bitmap.getGenerationId()` so equivalent drawables share an entry. `LruCache.sizeOf` is fixed to use bytes-per-char so small Base64 strings cannot bypass eviction.

**Tech Stack:** Java 17, Kotlin 1.7, Android (minSdk 24, compileSdk 34), AGP 8.1.4, JUnit 4 + Robolectric.

**Spec:** [`docs/superpowers/specs/2026-06-09-async-image-compression-design.md`](../specs/2026-06-09-async-image-compression-design.md)

**Jira:** [NR-572828](https://new-relic.atlassian.net/browse/NR-572828)

---

## File Structure

| Action | Path | Purpose |
|---|---|---|
| Modify | `agent/src/main/java/com/newrelic/agent/android/sessionReplay/viewMapper/SessionReplayImageViewThingy.java` | Async compression, fixed LRU cache, fixed `hasChanged()`, content-stable cache key, `volatile imageData` |
| Modify | `agent/src/main/kotlin/com/newrelic/agent/android/sessionReplay/viewMapper/ComposeImageThingy.kt` | Replace unused `cachedThreadPool` with single-thread executor, async dispatch in init |
| Create | `agent/src/test/java/com/newrelic/agent/android/sessionReplay/viewMapper/SessionReplayImageViewThingyTest.java` | Robolectric tests covering cache, key stability, `hasChanged`, and the async dispatch path |

`ComposeImageThingy` does not get a new unit test — its constructor takes a `SemanticsNode` which requires a live Compose runtime to construct, so the change is verified manually against the sample app. The View-side test covers the shared design (single-thread executor, async-fill semantics, cache hit/miss flow); the Compose change is a structurally identical refactor.

`ImageCompressionUtils.java` is **not** modified in this plan — null-safety / dimension caps were explicitly deferred (see spec, "Out of scope").

**Compose cache key strengthening is also deferred to a follow-up.** The spec mentions reusing `ComposePainterReflectionUtils` to extract painter delegate identity; in practice this would double the reflection cost on the constructor hot path for an incremental dedup gain. The View-side cache key is the high-value fix (it's the one called out in the Jira ticket as broken). The Compose path keeps its existing `painter.hashCode() + intrinsicSize + contentScale` key — weaker, but the async compression fix means a poor cache hit just costs an extra worker task, not a main-thread stall.

---

## Task 1: View — Fix LRU `sizeOf` and bump cache size to 50 MB

**Files:**
- Create: `agent/src/test/java/com/newrelic/agent/android/sessionReplay/viewMapper/SessionReplayImageViewThingyTest.java`
- Modify: `agent/src/main/java/com/newrelic/agent/android/sessionReplay/viewMapper/SessionReplayImageViewThingy.java:46-52`

The current `sizeOf` returns `value.length() / 1024`, which floors small Base64 strings (small icons) to 0 — the cache cannot evict them by size, so a screen of small icons grows the cache unboundedly. Fix: budget in bytes-per-Java-char (consistent with `ComposeImageThingy`), bump the cap to 50 MB.

This task also bootstraps the test file used by later tasks.

- [ ] **Step 1: Write the failing test**

Create `agent/src/test/java/com/newrelic/agent/android/sessionReplay/viewMapper/SessionReplayImageViewThingyTest.java`:

```java
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
                cache.maxSize() >= 50 * 1024 * 1024);
    }

    @SuppressWarnings("unchecked")
    private static LruCache<String, String> getStaticCache() throws Exception {
        Field f = SessionReplayImageViewThingy.class.getDeclaredField("imageCache");
        f.setAccessible(true);
        return (LruCache<String, String>) f.get(null);
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `./gradlew :agent:testReleaseUnitTest --tests "com.newrelic.agent.android.sessionReplay.viewMapper.SessionReplayImageViewThingyTest"`

Expected: both tests FAIL.
- `lruCache_smallBase64String_doesNotSizeToZero` → `cache.size()` returns 0 because `50 / 1024 == 0`.
- `lruCache_capIsAtLeast50MB` → `maxSize()` returns 1024.

- [ ] **Step 3: Implement — bump cache size to 50 MB and fix `sizeOf`**

In `SessionReplayImageViewThingy.java`, replace lines 44-52:

```java
    // Static cache shared across all instances.
    // Sized in bytes (Java chars are 2 bytes), capped at 50 MB so a screen full of
    // small icons cannot bypass eviction the way the previous "value.length() / 1024"
    // floor allowed (small strings sized to 0 KB and never evicted by size).
    private static final int MAX_CACHE_SIZE_BYTES = 50 * 1024 * 1024;
    private static final LruCache<String, String> imageCache = new LruCache<String, String>(MAX_CACHE_SIZE_BYTES) {
        @Override
        protected int sizeOf(String key, String value) {
            return value.length() * 2;
        }
    };
```

- [ ] **Step 4: Run the tests and verify they pass**

Run: `./gradlew :agent:testReleaseUnitTest --tests "com.newrelic.agent.android.sessionReplay.viewMapper.SessionReplayImageViewThingyTest"`

Expected: both tests PASS.

- [ ] **Step 5: Commit**

```bash
git add agent/src/test/java/com/newrelic/agent/android/sessionReplay/viewMapper/SessionReplayImageViewThingyTest.java agent/src/main/java/com/newrelic/agent/android/sessionReplay/viewMapper/SessionReplayImageViewThingy.java
git commit -m "fix(session-replay): correct LRU sizeOf and bump image cache to 50 MB

The previous sizeOf returned length()/1024, which floored small Base64
strings to 0 and let the cache grow unboundedly when a screen contained
many small icons. Switch to bytes-per-Java-char and bump the cap to
50 MB to match ComposeImageThingy.

Refs NR-572828"
```

---

## Task 2: View — Fix `hasChanged()` to compare content

**Files:**
- Modify: `agent/src/main/java/com/newrelic/agent/android/sessionReplay/viewMapper/SessionReplayImageViewThingy.java:448-457`
- Modify: `agent/src/test/java/com/newrelic/agent/android/sessionReplay/viewMapper/SessionReplayImageViewThingyTest.java`

The current `hasChanged()` uses `this.hashCode() != other.hashCode()` but the class never overrides `hashCode()`, so it falls back to identity — every recapture is treated as a change. This forces the diff to emit redundant mutations even when the cache is hit and nothing has changed. Replace with an explicit content comparison.

- [ ] **Step 1: Write the failing tests**

Append to `SessionReplayImageViewThingyTest.java` (before the `getStaticCache` helper):

```java
    // ==================== hasChanged ====================

    @Test
    public void hasChanged_identicalContent_returnsFalse() throws Exception {
        SessionReplayImageViewThingy a = newThingyWithImageData("AAA", false);
        SessionReplayImageViewThingy b = newThingyWithImageData("AAA", false);
        Assert.assertFalse("two thingies with identical content must not register as changed",
                a.hasChanged(b));
    }

    @Test
    public void hasChanged_differentImageData_returnsTrue() throws Exception {
        SessionReplayImageViewThingy a = newThingyWithImageData("AAA", false);
        SessionReplayImageViewThingy b = newThingyWithImageData("BBB", false);
        Assert.assertTrue("different imageData must register as changed",
                a.hasChanged(b));
    }

    @Test
    public void hasChanged_nonImageType_returnsTrue() throws Exception {
        SessionReplayImageViewThingy a = newThingyWithImageData("AAA", false);
        Assert.assertTrue("non-image other must register as changed",
                a.hasChanged(null));
    }

    /**
     * Build a SessionReplayImageViewThingy bypassing the constructor so tests do not
     * need to drive a real ImageView through the bitmap pipeline. Sets the private
     * imageData field via reflection.
     */
    private SessionReplayImageViewThingy newThingyWithImageData(String imageData, boolean masked) throws Exception {
        android.content.Context ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        android.widget.ImageView iv = new android.widget.ImageView(ctx);
        iv.layout(0, 0, 10, 10);
        com.newrelic.agent.android.AgentConfiguration cfg = new com.newrelic.agent.android.AgentConfiguration();
        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(new ViewDetails(iv), iv, cfg);
        Field f = SessionReplayImageViewThingy.class.getDeclaredField("imageData");
        f.setAccessible(true);
        f.set(thingy, imageData);
        return thingy;
    }
```

Add the missing imports at the top of the test class:

```java
import com.newrelic.agent.android.AgentConfiguration;
```

- [ ] **Step 2: Run the tests and verify they fail**

Run: `./gradlew :agent:testReleaseUnitTest --tests "com.newrelic.agent.android.sessionReplay.viewMapper.SessionReplayImageViewThingyTest"`

Expected: `hasChanged_identicalContent_returnsFalse` FAILS — current implementation uses identity hashCode so two separate instances with the same imageData report as changed.

- [ ] **Step 3: Implement — content-based `hasChanged`**

In `SessionReplayImageViewThingy.java`, replace lines 448-457:

```java
    @Override
    public boolean hasChanged(SessionReplayViewThingyInterface other) {
        if (!(other instanceof SessionReplayImageViewThingy)) {
            return true;
        }
        SessionReplayImageViewThingy o = (SessionReplayImageViewThingy) other;
        return !Objects.equals(viewDetails, o.viewDetails)
                || !Objects.equals(imageData, o.imageData)
                || !Objects.equals(backgroundColor, o.backgroundColor)
                || isMasked != o.isMasked;
    }
```

`Objects` is already imported (line 38 of the original file).

- [ ] **Step 4: Run the tests and verify they pass**

Run: `./gradlew :agent:testReleaseUnitTest --tests "com.newrelic.agent.android.sessionReplay.viewMapper.SessionReplayImageViewThingyTest"`

Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add agent/src/main/java/com/newrelic/agent/android/sessionReplay/viewMapper/SessionReplayImageViewThingy.java agent/src/test/java/com/newrelic/agent/android/sessionReplay/viewMapper/SessionReplayImageViewThingyTest.java
git commit -m "fix(session-replay): make SessionReplayImageViewThingy.hasChanged() compare content

The previous implementation compared System.identityHashCode of the two
thingy instances, so every recaptured frame produced 'changed' even on
cache hits. Compare viewDetails, imageData, backgroundColor, and isMasked
directly so the diff only emits mutations when something actually changed.

Refs NR-572828"
```

---

## Task 3: View — Content-stable cache key

**Files:**
- Modify: `agent/src/main/java/com/newrelic/agent/android/sessionReplay/viewMapper/SessionReplayImageViewThingy.java:163-181`
- Modify: `agent/src/test/java/com/newrelic/agent/android/sessionReplay/viewMapper/SessionReplayImageViewThingyTest.java`

`generateCacheKey` currently uses `drawable.hashCode()` — identity by default. Equivalent drawables miss the cache; distinct drawables can collide. Replace with `getConstantState()` identity (stable across drawable instances loaded from the same resource) plus `Bitmap.getGenerationId()` for `BitmapDrawable` (changes when pixels mutate).

- [ ] **Step 1: Write the failing tests**

Append to `SessionReplayImageViewThingyTest.java`:

```java
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
    public void cacheKey_differentBitmapContent_differs() throws Exception {
        android.content.Context ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.drawable.BitmapDrawable d = new android.graphics.drawable.BitmapDrawable(ctx.getResources(), bm);

        android.widget.ImageView iv = new android.widget.ImageView(ctx);
        iv.layout(0, 0, 10, 10);
        iv.setImageDrawable(d);

        String keyBefore = invokeGenerateCacheKey(iv, d);
        bm.eraseColor(android.graphics.Color.RED); // bumps generationId
        String keyAfter = invokeGenerateCacheKey(iv, d);

        Assert.assertNotEquals("mutating bitmap pixels must change the cache key",
                keyBefore, keyAfter);
    }

    private String invokeGenerateCacheKey(android.widget.ImageView iv, android.graphics.drawable.Drawable d) throws Exception {
        com.newrelic.agent.android.AgentConfiguration cfg = new com.newrelic.agent.android.AgentConfiguration();
        SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(new ViewDetails(iv), iv, cfg);
        java.lang.reflect.Method m = SessionReplayImageViewThingy.class.getDeclaredMethod(
                "generateCacheKey", android.graphics.drawable.Drawable.class, android.widget.ImageView.class);
        m.setAccessible(true);
        return (String) m.invoke(thingy, d, iv);
    }
```

- [ ] **Step 2: Run the tests and verify they fail**

Run: `./gradlew :agent:testReleaseUnitTest --tests "com.newrelic.agent.android.sessionReplay.viewMapper.SessionReplayImageViewThingyTest"`

Expected: `cacheKey_differentBitmapContent_differs` FAILS — current key uses `drawable.hashCode()` which is identity and does not change when the underlying bitmap mutates. (`cacheKey_sameDrawableInstance_equal` may already pass on the identity-hash path because the same drawable instance returns the same identity hash; that's fine — it locks in correct behavior under the new key strategy too.)

- [ ] **Step 3: Implement — `ConstantState` + `generationId` cache key**

In `SessionReplayImageViewThingy.java`, replace lines 163-181 with:

```java
    /**
     * Generates a cache key derived from stable identity. Uses Drawable.getConstantState()
     * (shared across drawable instances loaded from the same resource) and, when the drawable
     * is a BitmapDrawable, the Bitmap.getGenerationId() so re-decoded or mutated bitmaps
     * receive a fresh key. Falls back to identityHashCode when ConstantState is null
     * (rare — programmatically constructed drawables).
     */
    private String generateCacheKey(Drawable drawable, ImageView imageView) {
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(drawable.getClass().getSimpleName()).append('_');

        Drawable.ConstantState cs = drawable.getConstantState();
        if (cs != null) {
            keyBuilder.append("cs").append(System.identityHashCode(cs));
        } else {
            keyBuilder.append("d").append(System.identityHashCode(drawable));
        }

        if (drawable instanceof BitmapDrawable) {
            Bitmap bm = ((BitmapDrawable) drawable).getBitmap();
            if (bm != null && !bm.isRecycled()) {
                keyBuilder.append("_g").append(bm.getGenerationId());
            }
        }

        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        if (width <= 0 || height <= 0) {
            width = imageView.getWidth();
            height = imageView.getHeight();
        }
        keyBuilder.append('_').append(width).append('x').append(height);
        keyBuilder.append('_').append(scaleType.name());

        return keyBuilder.toString();
    }
```

`Bitmap`, `BitmapDrawable`, and `Drawable` are already imported.

- [ ] **Step 4: Run the tests and verify they pass**

Run: `./gradlew :agent:testReleaseUnitTest --tests "com.newrelic.agent.android.sessionReplay.viewMapper.SessionReplayImageViewThingyTest"`

Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add agent/src/main/java/com/newrelic/agent/android/sessionReplay/viewMapper/SessionReplayImageViewThingy.java agent/src/test/java/com/newrelic/agent/android/sessionReplay/viewMapper/SessionReplayImageViewThingyTest.java
git commit -m "fix(session-replay): use stable cache key for image dedup

Previous key used drawable.hashCode() (identity by default), so equivalent
drawables missed the cache and distinct drawables could collide. Replace
with ConstantState identity plus Bitmap.getGenerationId() for BitmapDrawable
so equivalent drawables share an entry and pixel mutations invalidate
correctly.

Refs NR-572828"
```

---

## Task 4: View — Async compression dispatch + `volatile imageData`

**Files:**
- Modify: `agent/src/main/java/com/newrelic/agent/android/sessionReplay/viewMapper/SessionReplayImageViewThingy.java`
- Modify: `agent/src/test/java/com/newrelic/agent/android/sessionReplay/viewMapper/SessionReplayImageViewThingyTest.java`

This is the ANR fix. Move bitmap copy + WEBP compress + Base64 encode to a single-thread executor; the main thread synchronously checks the cache and snapshots the bitmap reference, then returns immediately. The diff sees `imageData` resolved on cache hit and `null` on cache miss (one frame of placeholder; subsequent frames hit the cache).

The executor is exposed as a package-private `static volatile Executor` so tests can substitute a deterministic recording executor.

- [ ] **Step 1: Write the failing tests**

Append to `SessionReplayImageViewThingyTest.java`:

```java
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
            SessionReplayImageViewThingy thingy = new SessionReplayImageViewThingy(new ViewDetails(iv), iv, cfg);

            Assert.assertNull("imageData must be null immediately on cache miss", thingy.getImageData());
            Assert.assertEquals("one compression task must be queued on cache miss",
                    1, recordingExecutor.size());

            recordingExecutor.runAll();
            Assert.assertNotNull("imageData must be populated after worker runs",
                    thingy.getImageData());
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
```

- [ ] **Step 2: Run the tests and verify they fail**

Run: `./gradlew :agent:testReleaseUnitTest --tests "com.newrelic.agent.android.sessionReplay.viewMapper.SessionReplayImageViewThingyTest"`

Expected: all three new tests FAIL — `compressionExecutor` field does not exist yet (`NoSuchFieldException` from `installRecordingExecutor`).

- [ ] **Step 3: Implement — async compression**

Add the imports near the top of `SessionReplayImageViewThingy.java` (under the existing `java.util` imports):

```java
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
```

In `SessionReplayImageViewThingy.java`, after the existing `imageCache` declaration (after the closing brace at line 52 of the new code from Task 1), insert:

```java
    /**
     * Single-thread executor for off-main bitmap compression. Single-thread keeps cache
     * writes ordered and avoids races between concurrent fills on the same key.
     * Package-private and non-final so tests can substitute a deterministic executor.
     */
    static volatile Executor compressionExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "nr-sr-image-compress");
        t.setDaemon(true);
        return t;
    });
```

Mark `imageData` `volatile` (line 60 of the original file):

```java
    private volatile String imageData; // Base64 encoded image data — written off-main
```

Replace the `getImageFromImageView` method (lines 82-114 of the original file) with the async flow:

```java
    /**
     * Synchronously checks the LRU cache. On hit, returns the cached Base64. On miss,
     * snapshots the bitmap reference and dispatches compression to a worker thread,
     * which populates the cache and back-fills this instance's imageData when done.
     * Returns null on miss so the caller renders a placeholder for this frame; the
     * next captured frame will resolve from cache.
     */
    private String getImageFromImageView(ImageView imageView) {
        try {
            Drawable drawable = imageView.getDrawable();
            if (drawable == null) {
                return null;
            }

            final String cacheKey = generateCacheKey(drawable, imageView);

            String cachedData = imageCache.get(cacheKey);
            if (cachedData != null) {
                log.debug("Cache hit for image: " + cacheKey);
                return cachedData;
            }

            final Bitmap bitmap = drawableToBitmap(drawable, imageView);
            if (bitmap == null || bitmap.isRecycled()) {
                return null;
            }

            compressionExecutor.execute(() -> {
                try {
                    String b64 = bitmapToBase64(bitmap);
                    if (b64 != null) {
                        imageCache.put(cacheKey, b64);
                        SessionReplayImageViewThingy.this.imageData = b64;
                        log.debug("Cached image data for key: " + cacheKey);
                    }
                } catch (Exception e) {
                    log.error("Async image compression failed", e);
                }
            });
        } catch (Exception e) {
            log.error("Error processing image", e);
        }
        return null;
    }
```

Remove the `@WorkerThread` annotation from this method's signature — it now runs on the calling (main) thread, doing only cache lookup and bitmap reference grabs. The `@WorkerThread` annotation on `bitmapToBase64` (line 199) remains correct because that helper is now only invoked from inside the executor.

- [ ] **Step 4: Run the tests and verify they pass**

Run: `./gradlew :agent:testReleaseUnitTest --tests "com.newrelic.agent.android.sessionReplay.viewMapper.SessionReplayImageViewThingyTest"`

Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add agent/src/main/java/com/newrelic/agent/android/sessionReplay/viewMapper/SessionReplayImageViewThingy.java agent/src/test/java/com/newrelic/agent/android/sessionReplay/viewMapper/SessionReplayImageViewThingyTest.java
git commit -m "fix(session-replay): move ImageView bitmap compression off the main thread

Bitmap copy + WEBP compression + Base64 encoding now run on a per-class
single-thread executor. The main thread does only the cache lookup and
bitmap reference grab. On cache hit the diff sees the resolved Base64;
on cache miss imageData is null for that frame and the worker fills the
cache so the next captured frame resolves synchronously.

Fixes NR-572828"
```

---

## Task 5: Compose — Replace cached thread pool with single-thread executor and dispatch async

**Files:**
- Modify: `agent/src/main/kotlin/com/newrelic/agent/android/sessionReplay/viewMapper/ComposeImageThingy.kt`

`ComposeImageThingy` already declares `imageExtractionExecutor = Executors.newCachedThreadPool()` (line 56) but it is unused — `extractImageFromModifierInfo()` runs synchronously in `init`. Replace it with a single-thread executor and dispatch the same way as the View side.

No new unit test: `SemanticsNode` requires a live Compose runtime so this constructor is not unit-testable in Robolectric without significant scaffolding. The change is verified manually against the sample app's Compose screen (see Task 6). The async pattern is identical to the View side, which is unit-tested in Task 4.

- [ ] **Step 1: Replace the executor**

In `ComposeImageThingy.kt`, replace lines 55-61 (inside the `companion object`) with:

```kotlin
        private const val MAX_CACHE_SIZE_BYTES = 50 * 1024 * 1024 // 50 MB

        /**
         * Single-thread executor for off-main bitmap compression. Single-thread keeps cache
         * writes ordered and avoids races between concurrent fills on the same key.
         */
        @JvmField
        internal var compressionExecutor: java.util.concurrent.Executor =
            java.util.concurrent.Executors.newSingleThreadExecutor { r ->
                Thread(r, "nr-sr-compose-image-compress").apply { isDaemon = true }
            }

        private val imageCache = object : LruCache<String, String>(MAX_CACHE_SIZE_BYTES) {
            override fun sizeOf(key: String, value: String): Int {
                return value.length * 2
            }
        }
```

Remove the now-stale import on line 28:

```kotlin
import java.util.concurrent.Executors
```

— and re-add it (still needed for the new executor) but remove the unused `CountDownLatch` and `TimeUnit` imports on lines 27 and 29 if they are not referenced elsewhere in the file:

```bash
grep -n "CountDownLatch\|TimeUnit" agent/src/main/kotlin/com/newrelic/agent/android/sessionReplay/viewMapper/ComposeImageThingy.kt
```

If the only references are the imports themselves, remove them. Keep `Executors` since it is still used.

- [ ] **Step 2: Convert `imageDataUrl` from `by lazy` to a getter**

`imageDataUrl` (line 335 of the original file) is currently a `by lazy` property:

```kotlin
private val imageDataUrl: String? by lazy {
    imageData?.let { ImageCompressionUtils.toImageDataUrl(it) }
}
```

With async fill this is a correctness bug: if `imageDataUrl` is first read before the worker has set `imageData`, the lazy initializer captures `null` and never re-evaluates — so the image is permanently missing from the rendered CSS even after the cache is populated.

Replace it with a plain getter so each read sees the current `imageData`:

```kotlin
private val imageDataUrl: String?
    get() = imageData?.let { ImageCompressionUtils.toImageDataUrl(it) }
```

- [ ] **Step 3: Refactor the init block to dispatch compression asynchronously**

Replace `init` (lines 97-108) with:

```kotlin
    init {
        contentScale = extractContentScale()

        isMasked = !shouldUnMaskImage(semanticsNode)
        if (!isMasked) {
            try {
                dispatchImageExtraction()
            } catch (e: Exception) {
                log.error("Error scheduling image extraction", e)
            }
        }
    }

    /**
     * Performs the synchronous cache lookup on the calling thread. On a cache hit,
     * imageData is set immediately. On a miss, painter extraction (which still touches
     * the Compose tree and must stay on the original thread) runs synchronously, but
     * the bitmap-to-Base64 compression is dispatched to compressionExecutor.
     */
    private fun dispatchImageExtraction() {
        val modifierInfoList = semanticsNode.layoutInfo.getModifierInfo()
        for (modifierInfo in modifierInfoList) {
            val modifier = modifierInfo.modifier
            val modifierClassName = modifier.javaClass.simpleName
            if (modifierClassName.contains("Painter") ||
                modifier.javaClass.name.contains("foundation.Image") ||
                modifier.javaClass.name.contains("PainterModifier")
            ) {
                val painter = extractPainterFromModifier(modifier) ?: continue
                val cacheKey = generateCacheKey(painter)
                val cached = imageCache.get(cacheKey)
                if (cached != null) {
                    log.debug("Cache hit for image: $cacheKey")
                    imageData = cached
                    return
                }
                val bitmap = extractBitmapForPainter(painter) ?: continue
                compressionExecutor.execute {
                    try {
                        val b64 = bitmapToBase64(bitmap)
                        if (b64 != null) {
                            imageCache.put(cacheKey, b64)
                            imageData = b64
                            log.debug("Cached image data for key: $cacheKey")
                        }
                    } catch (e: Exception) {
                        log.error("Async Compose image compression failed", e)
                    }
                }
                return
            }
        }
    }

    /**
     * Resolves a Bitmap for the painter on the calling thread (BitmapPainter,
     * VectorPainter, AsyncImagePainter, or other). Bitmap is then handed to the
     * worker for compression.
     */
    private fun extractBitmapForPainter(painter: Painter): Bitmap? = when {
        painter is BitmapPainter -> extractBitmapFromBitmapPainter(painter)
        painter is VectorPainter -> createBitmapFromVectorPainter(painter)
        painter.javaClass.simpleName.contains("AsyncImagePainter") -> extractBitmapFromAsyncImagePainter(painter)
        else -> createBitmapFromPainter(painter)
    }
```

Delete the now-unused `extractImageFromModifierInfo()` (lines 116-141) and `convertPainterToBase64()` (lines 148-193) — their logic has moved into `dispatchImageExtraction` and `extractBitmapForPainter`.

- [ ] **Step 4: Build to verify the refactor compiles**

Run: `./gradlew :agent:compileReleaseKotlin`

Expected: BUILD SUCCESSFUL. If compilation fails on a missing reference, re-check the deletions in Step 2 — only `extractImageFromModifierInfo` and `convertPainterToBase64` should be removed; the per-painter helpers (`extractBitmapFromBitmapPainter`, `createBitmapFromVectorPainter`, `extractBitmapFromAsyncImagePainter`, `createBitmapFromPainter`, `bitmapToBase64`, `generateCacheKey`) all remain in use.

- [ ] **Step 5: Run the existing test suite to verify no regressions**

Run: `./gradlew :agent:testReleaseUnitTest`

Expected: BUILD SUCCESSFUL with no failures. (The Compose path has no dedicated unit tests; this confirms the View-side tests still pass and the Kotlin module still compiles cleanly.)

- [ ] **Step 6: Commit**

```bash
git add agent/src/main/kotlin/com/newrelic/agent/android/sessionReplay/viewMapper/ComposeImageThingy.kt
git commit -m "fix(session-replay): move Compose image compression off the main thread

Replaces the unused cached thread pool with a single-thread executor that
runs bitmap compression off-main, mirroring the SessionReplayImageViewThingy
fix. Painter extraction stays on the calling (typically main) thread because
it traverses Compose modifier info; only the bitmap copy + WEBP compress +
Base64 encode is dispatched.

Fixes NR-572828"
```

---

## Task 6: Manual verification on the sample app

This is the acceptance gate from the ticket. Not a code task — run it and capture evidence before merging.

**Files:**
- None modified.

- [ ] **Step 1: Build and install the sample app**

Run:

```bash
./gradlew :samples:agent-test-app:installDebug
```

Expected: APK installed on a connected device/emulator. Use a low-end emulator profile if available (e.g., Pixel 2 with 1 GB RAM) — the ANR shows up most clearly there.

- [ ] **Step 2: Capture a method trace while exercising image-heavy screens**

In Android Studio, open Profiler → CPU → Record method trace (sampled). In the sample app, navigate to a screen with multiple `ImageView`s and a Compose screen with `Image` composables; let session replay capture for ~20 s. Stop the trace.

In the trace, search the **main thread** for the following symbols. **None** should appear on the main thread:

- `ImageCompressionUtils.bitmapToBase64`
- `Bitmap.compress`
- `Base64.encodeToString`

Confirm they DO appear under a worker thread named `nr-sr-image-compress` (View) and/or `nr-sr-compose-image-compress` (Compose).

- [ ] **Step 3: Verify replay fidelity in the viewer**

Trigger session replay upload from the sample app, then load the session in the New Relic replay viewer. Confirm:

- Images render correctly (not gray placeholders) after the first capture frame for each new image.
- No console errors about malformed `background-image` URLs.

- [ ] **Step 4: Verify the cache stabilises**

Add a temporary log call in the sample app (do not commit) or call `SessionReplayImageViewThingy.getCacheStats()` from a debug button. After scrolling through a list of many small icons, verify `Cache stats - Size: <N>, Hits: <H>, Misses: <M>` shows the size stabilises rather than growing unboundedly (under 50 MB), and hit count grows on repeat scrolls.

- [ ] **Step 5: Final commit (if any verification adjustments needed)**

If Steps 2-4 surface a defect, fix it and add a regression test. Otherwise, no commit needed — the verification is the gate, not a code change.

---

## Acceptance Criteria Verification

After completing all tasks above, verify against the spec acceptance criteria:

- ✅ Bitmap copy + WEBP compression + Base64 encoding no longer execute on the main thread under any code path triggered from `ViewDrawInterceptor`. **Verified in Task 6 Step 2.**
- ⏭️ `ImageCompressionUtils.bitmapToBase64` null-safety — *deferred to follow-up ticket (out of scope).*
- ✅ The image cache cannot grow without bound when the page contains many small images. **Verified in Task 1 (`sizeOf` floor) and Task 6 Step 4 (cache stabilises).**
- ✅ No regression in captured-frame fidelity for screens with images. **Verified in Task 6 Step 3.**
