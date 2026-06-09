# Async image compression for Session Replay (NR-572828)

**Status:** Draft — pending implementation plan
**Date:** 2026-06-09
**Jira:** [NR-572828](https://new-relic.atlassian.net/browse/NR-572828)
**Affected files:**
- `agent/src/main/java/com/newrelic/agent/android/sessionReplay/viewMapper/SessionReplayImageViewThingy.java`
- `agent/src/main/kotlin/com/newrelic/agent/android/sessionReplay/viewMapper/ComposeImageThingy.kt`

## Problem

Customer-reported ANR/crash from Firebase. Stack ends in `ImageCompressionUtils.bitmapToBase64`; the
underlying problem is that the entire capture-and-compression chain runs on the UI thread, in
violation of the `@WorkerThread` contract on the methods being called.

```
OnDrawListener (main)
  → Debouncer (Looper.getMainLooper(), main)
    → SessionReplayCapture.capture (main)
        → SessionReplayImageViewThingy.<init> (main)
            → ImageCompressionUtils.bitmapToBase64 (main)   ← ANR origin
                ├─ bitmap.copy(ARGB_8888) — full pixel copy
                ├─ compress(WEBP_LOSSY, 10) — CPU-heavy encode
                └─ Base64.encodeToString — large alloc + copy
```

For a screen with several `ImageView`s, each frame draw can stall the UI thread for hundreds of ms
— easily 5 s on low-end devices (the ANR threshold).

## Scope

In scope:

1. Move bitmap copy + WEBP compression + Base64 encoding off the main thread (the ANR fix).
2. Fix the existing image cache so that the async-fill flow does not produce visible diff
   artifacts.

Out of scope (separate ticket):

- Defensive null-safety in `ImageCompressionUtils.bitmapToBase64`.
- Vector-drawable dimension caps.
- HARDWARE-bitmap fallback on API 26-29 OEM bugs.

## Goals

- Bitmap copy / WEBP compression / Base64 encoding no longer execute on the main thread under any
  code path triggered from `ViewDrawInterceptor`.
- The image cache cannot grow without bound when the page contains many small images.
- Repeat appearances of any cached image are immediate; the rrweb diff sees the resolved Base64
  on every cache hit.
- No regression in captured-frame fidelity for screens with images, modulo a one-frame placeholder
  on first sighting of a new image.

## Non-goals

- Eliminating the placeholder frame on first sighting. That trade-off is intentional — see
  *Diff fidelity contract* below.
- Parallelising image compression across CPUs.
- Changing the existing `ViewDrawInterceptor → Debouncer → SessionReplayCapture →
  SessionReplay.onFrameTaken → SessionReplayProcessor` pipeline. The fix is contained to the two
  thingy classes.

## Design

### Threading model

```
OnDraw (main)
  └─ capture (main)
       └─ ImageView/Compose thingy ctor (main)
            ├─ cache.get(key)                       ← sync, fast
            │     ├─ HIT  → imageData = cached       (diff sees real image)
            │     └─ MISS → imageData = null
            │                + grab Bitmap reference
            │                + executor.execute(compress task)   ← off-main
            │
            └─ return thingy immediately to capture loop

  └─ onFrameTaken → processFrames → diff → write   (main, unchanged)

[Worker]  compress + Base64 → cache.put(key, b64) → thingy.imageData = b64
```

The View-tree walk and the diff stay on the main thread because they must — `ViewGroup.getChildAt`
and `View.getDrawable` are main-thread-only. The expensive part (the `@WorkerThread` block of
`bitmapToBase64`) moves to a single-thread executor per thingy class.

### Diff fidelity contract

The rrweb diff in `SessionReplayProcessor.processIncrementalFrame` compares each thingy's `imageData`
between consecutive frames and emits a `background-image` mutation when they differ. With async fill,
the contract is:

- **Cache hit** → `imageData` is set synchronously in the constructor → diff sees the real image.
- **Cache miss** (first sighting of a new image) → `imageData == null` for that frame → diff emits
  the AddRecord with no `background-image`; the user sees the gray placeholder for one frame. The
  worker completes shortly after, populates the cache. The next captured frame builds a fresh thingy
  whose constructor hits the cache, and the diff between the placeholder frame and the resolved frame
  emits a single `background-image` AttributeRecord.

This is the price of not blocking main. Every subsequent appearance of any cached image is instant.

### Per-class static executors

Each of `SessionReplayImageViewThingy` and `ComposeImageThingy` owns:

```java
static final ExecutorService COMPRESSION_EXECUTOR =
    Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "nr-sr-image-compress");
        t.setDaemon(true);
        return t;
    });
```

Single-thread is deliberate: it keeps cache writes ordered and avoids racing the diff with
concurrent fills on the same key. Per-class is per the chosen design — symmetric to the existing
per-class LRU caches and consistent with the existing dead-code stub in `ComposeImageThingy`.

`ComposeImageThingy`'s unused `imageExtractionExecutor = Executors.newCachedThreadPool()` is deleted
and replaced with the single-thread executor above.

Lifecycle is implicit (`static` with daemon threads). No explicit shutdown on
`SessionReplay.stopRecording()` in v1; can be added if profiling shows it matters.

### Cache key strategy

Replace the current `drawable.hashCode()` (identity) keys with a hybrid:

```
key = drawable.getConstantState() identity   (when non-null)
    + width × height
    + scaleType
    + bitmap.getGenerationId()                (when drawable is BitmapDrawable)
```

Falls back to `System.identityHashCode(drawable)` when `getConstantState()` returns null (rare —
mostly programmatically-built drawables). `ConstantState` identity is shared across all drawable
instances created from the same source resource, so `R.drawable.foo` produces the same key
regardless of which `ImageView` it was loaded into. `getGenerationId()` mutates when bitmap pixels
change, so a re-decoded bitmap gets a fresh key.

For Compose, reuse the existing `ComposePainterReflectionUtils` to extract the painter delegate
identity where reachable; combine with `intrinsicSize` and `contentScale` (current key already does
the latter two).

### Cache size accounting

`SessionReplayImageViewThingy` aligns with the existing `ComposeImageThingy` budget:

```java
private static final int MAX_CACHE_SIZE_BYTES = 50 * 1024 * 1024; // 50 MB
imageCache = new LruCache<String, String>(MAX_CACHE_SIZE_BYTES) {
    @Override
    protected int sizeOf(String key, String value) {
        return value.length() * 2; // bytes per Java char
    }
};
```

`* 2` (bytes per char) replaces the broken `value.length() / 1024` floor that sized small icons to 0
KB. With WEBP-q10, most encoded images are 1-20 KB, so 50 MB is effectively unbounded for typical
apps; the cap is a safety valve. Two caches at 50 MB each is acceptable heap commitment for the
session replay feature.

### `hasChanged()` correction

`SessionReplayImageViewThingy.hasChanged()` currently compares `this.hashCode() != other.hashCode()`
but the class does not override `hashCode()`, so this falls back to identity and every recapture is
treated as a change. Replace with content comparison:

```java
return !Objects.equals(viewDetails, otherImg.viewDetails)
    || !Objects.equals(imageData, otherImg.imageData)
    || !Objects.equals(backgroundColor, otherImg.backgroundColor)
    || isMasked != otherImg.isMasked;
```

`ComposeImageThingy.hasChanged()` is already content-based; no change needed there.

### Thread visibility

`SessionReplayImageViewThingy.imageData` becomes `volatile String`. `ComposeImageThingy.imageData` is
already `@Volatile`.

The thingy is constructed on main and the worker may write `imageData` after the constructor
returns. In practice, by the time anyone reads `imageData` again, the next frame has built a fresh
thingy that resolved via the cache — the only realistic cross-thread read is in the rare case where
the same thingy instance is reused. `volatile` is cheap insurance.

## Failure modes

| Failure | Behavior |
|---|---|
| Compress throws or returns null | `imageData` stays null on this thingy; cache not populated. Next frame's miss re-dispatches. Worst case: image never resolves and replay shows a gray placeholder. Logged via existing error path. |
| Bitmap recycled before worker runs | `bitmap.copy(...)` on a recycled bitmap throws → caught in `bitmapToBase64`'s existing try/catch, returns null. Same as above. Relies on Android not recycling drawables underneath a live ImageView between capture and compression — safe at OnDraw cadence. |
| Worker queue backs up under heavy churn | Single-thread executor — tasks run FIFO. New captures see `cache.get(key)` miss until that key's task drains. Diff continues to emit placeholders for that key in the meantime. No memory blowup because the executor's task queue holds only Runnables, not bitmap copies. |
| App backgrounded between frame N and worker completion | The worker still runs and populates the cache. Next foregrounded session benefits. No harm. |
| Two thingies on the same frame share a drawable | First miss dispatches; second sees the same miss and dispatches again. **Mitigation:** in-flight set keyed by cache key, second caller short-circuits. *Not in v1.* Add only if profiling shows redundant work matters. |

## Testing

### Unit tests (Robolectric for the View path; JUnit + mocks for utilities)

| Test | What it asserts |
|---|---|
| Cache hit → sync resolution | Pre-populate cache; construct thingy; `imageData` non-null at end of constructor; executor not invoked. |
| Cache miss → async dispatch, immediate null | Empty cache; `imageData == null` at end of constructor; mock executor records 1 submission. |
| Worker completes → cache populated + thingy filled | Run the submitted task via a direct executor; verify cache populated and `thingy.imageData == b64`. |
| Cache key stability | Two thingies wrapping `getDrawable(R.drawable.foo)` produce equal keys. |
| Cache key separation | Mutating the bitmap (forcing generationId increment) produces different keys. |
| `sizeOf` floors correctly | 50-char Base64 → `sizeOf > 0`. |
| `hasChanged` uses content | Two thingies, same content → `false`. Different `imageData` → `true`. |
| `generateDifferences` cold→hot transition | thingy_N (`imageData=null`) vs thingy_N+1 (`imageData=b64`) → exactly one AttributeRecord with `background-image: url(...)`. |
| Masked image bypass | `isMasked == true` → no executor call, `imageData == null`, CSS is gray placeholder. |
| Executor failure does not poison cache | Mock `bitmapToBase64` returns null → `cache.get(key) == null` → next ctor re-dispatches. |

Compose path: symmetric versions where the painter→bitmap path is reachable. Use `BitmapPainter`
with a deterministic bitmap; `VectorPainter` and `AsyncImagePainter` cases are already covered by
`ComposePainterReflectionUtilsTest`.

### Manual verification (mirrors ticket acceptance criteria)

1. Run the sample app with several `ImageView`s on a screen; traceview confirms `bitmapToBase64`
   does not appear on the main thread.
2. No `compress` or `Base64.encodeToString` frames in any trace originating from `OnDrawListener`.
3. Replay viewer renders the image correctly with one frame of placeholder on first appearance.
4. `getCacheStats()` shows cache size stabilising within budget on a screen with many small icons.

## Acceptance criteria (from ticket)

- ✅ Bitmap copy + WEBP compression + Base64 encoding no longer execute on the main thread under
  any code path triggered from `ViewDrawInterceptor`.
- ⏭️ `ImageCompressionUtils.bitmapToBase64` null-safety — *deferred to follow-up ticket*.
- ✅ The image cache cannot grow without bound when the page contains many small images.
- ✅ No regression in captured-frame fidelity for screens with images (modulo the one-frame
  placeholder on first appearance, which is documented).
