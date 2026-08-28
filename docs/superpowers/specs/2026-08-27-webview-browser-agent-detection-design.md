# WebView Browser Agent Detection — Design

**Jira:** [NR-461357](https://new-relic.atlassian.net/browse/NR-461357) — [Android] Determine WebView Usage of New Relic Browser Agent
**Parent:** NR-454170 — [Web Views] Enhancements and any new features to support for web views
**Date:** 2026-08-27 (revised 2026-08-28 — agent-side `WebViewClient` installation)

## Problem

The agent already emits supportability metrics for WebView *usage* (`loadUrl`, `postUrl`, `onPageFinished` — see `WebViewInstrumentationCallbacks`). What's missing is a signal for how many of those WebViews are loading pages that already run the New Relic Browser (JS) agent (`window.newrelic`). That number decides whether Mobile should inject the Browser agent into WebViews that lack it, or instead unify the experience via a common attribute across platforms.

## Acceptance Criteria (from ticket)

1. Inject a check for `window.newrelic` into the WebView's page.
2. Inject a JavaScript method that can be called (from our own injected script, and manually from a JS console for debugging).
3. Emit a supportability metric if the page is running the NR Browser agent.

## Chosen Approach

**Agent-installed `WebViewClient` + `evaluateJavascript` detection script + `@JavascriptInterface` bridge**, hooked into WebView bytecode-instrumentation call sites — no new public API, no new Gradle DSL flag.

### Why a bridge rather than a callback

- **(chosen) `evaluateJavascript` + JS interface bridge.** The detection script itself is the "JS method" the AC asks for, and it's also manually callable from Chrome DevTools console against the same bridge object — satisfying AC #2 literally, not just in spirit.
- **(rejected) `evaluateJavascript` `ValueCallback<String>` only, no bridge.** Simpler and avoids a WebView JS-context timing quirk (below), but doesn't expose anything callable from a console — AC #2 would still require bolting the bridge on anyway, erasing the simplification.

### Why the agent installs the `WebViewClient` itself

The first iteration of this design relied on the customer's own `WebViewClient` subclass being bytecode-instrumented, and documented the resulting coverage hole as a known limitation. That hole is large: `WebViewMethodClassVisitor.isInstrumentable` compares a class's **immediate** `superName` against a regex via `Matcher.matches()`, with no ancestor-chain walk anywhere in the instrumentation module — so any `WebViewClient` reached through an intermediate base class is invisible, and both the `PageFinished` metric and the detection script silently never run for that app.

Installing our own client removes the dependency on customer class shape entirely. It cannot be done the way `feature/capacitor_poc`'s `RRWebRecorder.setupWebView()` does it, though: that calls `webView.setWebViewClient(new WebViewClient() { … })` outright (its own "preserve existing client" `try` block is empty at `RRWebRecorder.java:52-58`), which drops the customer's `shouldOverrideUrlLoading`, `shouldInterceptRequest`, `onReceivedSslError`, `onReceivedError`, and `onPageStarted`. In a shipped agent that means broken deep links, broken offline caching, silently failing cert pinning, and missing error screens — attributed to the agent. Acceptable in a POC that only ran in the sample app; not acceptable here.

So the client we install **delegates every callback** to whatever client was already there.

## Design

### 1. Runtime bridge — `agent/src/main/kotlin/com/newrelic/agent/android/webView/WebViewJSInterface.kt`

```kotlin
class WebViewJSInterface {
    companion object {
        const val INTERFACE_NAME = "NRWebViewBridge"
    }

    @JavascriptInterface
    fun reportBrowserAgentDetected(detected: Boolean) {
        if (detected) {
            StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_BROWSER_AGENT_DETECTED)
        }
    }
}
```

Only increments on `detected == true`, per the AC's wording ("emit a metric *if* the page is running the browser agent"). The existing `PageFinished` metric already gives a denominator (total page loads observed), so a numerator-only metric is sufficient to compute adoption %.

`INTERFACE_NAME` is a `const val` in a `companion object`, which compiles to a `public static final` field on the outer class — so the Java call site (`WebViewJSInterface.INTERFACE_NAME`) works unchanged.

### 2. Agent-side `WebViewClient` installation

#### 2.1 Two install points, one safety rule

| Install point | Layer | Catches | Delegate source |
|---|---|---|---|
| `setWebViewClient(client)` call site | new bytecode interception | customer sets a client (any API level) | the client they passed |
| `loadUrl` / `postUrl` call site | existing hook, extended | WebView whose `setWebViewClient` we never saw | `getWebViewClient()` (API 26+), else none |

**Safety rule: never install unless the existing client can be preserved.** `WebView.getWebViewClient()` is API 26 and this module's `minSdk` is 24 (`buildconfig.gradle:60`), so on Android 7.0/7.1 there is no way to read a client we didn't capture ourselves — and no way to distinguish "customer never set a client" from "customer set one through a call site we didn't intercept". Since those two cases are indistinguishable and one of them is a clobber, the `loadUrl`/`postUrl` install point **does nothing on API < 26**: it installs only when `getWebViewClient()` is available. On API 24–25, wrapper installation therefore happens exclusively via the `setWebViewClient` interception (which carries the delegate by construction).

Consequence: an Android 7.x app that never calls `setWebViewClient` at all gets no wrapper, and falls back to the bytecode `onPageFinished` path. Detection coverage is lost there; app behavior is never altered. This is the deliberate trade — the alternative (install with a null delegate and accept a rare clobber) violates the safety rule above.

#### 2.2 Bytecode interception — `WebViewCallSiteVisitor`

The three existing interceptions in this visitor are observe-only: they DUP the receiver and call a `void` callback. This one **rewrites the argument**, substituting our wrapper for the client the customer passed.

```
// stack on entry: [webView, client]
ASTORE 100        // [webView]                     client → temp slot
DUP               // [webView, webView]
ALOAD 100         // [webView, webView, client]
INVOKESTATIC  WebViewInstrumentationCallbacks.setWebViewClientCalled
              (Landroid/webkit/WebView;Landroid/webkit/WebViewClient;)Landroid/webkit/WebViewClient;
                  // [webView, clientToSet]        ← wrapper; delegate recorded
INVOKEVIRTUAL android/webkit/WebView.setWebViewClient (Landroid/webkit/WebViewClient;)V
```

Gated on `owner.equals("android/webkit/WebView")` like its siblings, and using the same hardcoded temp local slots (100/101) the existing `loadUrl(String, Map)` / `postUrl` cases use. That slot convention is fragile, but it is the file's existing pattern and is not being refactored as part of this change.

#### 2.3 Delegating wrapper — `agent/src/main/kotlin/com/newrelic/agent/android/webView/NRWebViewClient.kt` (new)

```kotlin
class NRWebViewClient(private var delegate: WebViewClient?) : WebViewClient() {

    override fun onPageFinished(view: WebView, url: String) {
        WebViewInstrumentationCallbacks.pageFinished(view, url)   // metric + detection script
        delegate?.onPageFinished(view, url) ?: super.onPageFinished(view, url)
    }

    // ~21 remaining callbacks, mechanical: delegate?.X(…) ?: super.X(…)
}
```

`onPageFinished` is the only callback that adds behavior. Every other `WebViewClient` callback is forwarded verbatim: `shouldOverrideUrlLoading`, `shouldInterceptRequest`, `onReceivedError`, `onReceivedHttpError`, `onReceivedSslError`, `onReceivedHttpAuthRequest`, `onReceivedClientCertRequest`, `onPageStarted`, `onPageCommitVisible`, `onLoadResource`, `doUpdateVisitedHistory`, `onFormResubmission`, `shouldOverrideKeyEvent`, `onUnhandledKeyEvent`, `onScaleChanged`, `onReceivedLoginRequest`, `onRenderProcessGone`, `onSafeBrowsingHit`.

Three details that are easy to get wrong:

- **Both overloads of each deprecated pair are overridden** (`shouldOverrideUrlLoading`, `shouldInterceptRequest`, `onReceivedError`). Forwarding the modern variant is enough to reach a customer who only overrode the deprecated one — the framework's own default implementation bridges new→old *inside their class*.
- **API-gated callbacks** (`onPageCommitVisible` 23, `onReceivedHttpError` 23, `onRenderProcessGone` 26, `onSafeBrowsingHit` 27) are overridden and annotated `@RequiresApi`; on older devices they are simply never invoked.
- **`delegate` is a `var`** so the "load, then set client" sequence re-parents the existing wrapper rather than stacking a second one.

#### 2.4 Ordering — both sequences work

- **Client set, then load** (common case): the interception wraps at `setWebViewClient`; `loadUrl` finds a wrapper installed and no-ops.
- **Load, then client set**: `loadUrl` installs a wrapper carrying the `getWebViewClient()` delegate; the later `setWebViewClient` is intercepted and re-parents, so the customer's client lands *inside* our wrapper instead of replacing it.

Installation is always **inline on the caller's thread — never `webView.post(…)`**. `WebViewCallSiteVisitor` injects `loadUrlCalled` *before* the real `loadUrl` (DUP2/POP, INVOKESTATIC, then the original INVOKEVIRTUAL), so inline work lands pre-navigation. The POC posts instead, which is fine for rrweb but would race here: a posted install can land after `onPageFinished` has already fired for the page we wanted to inspect.

### 3. Detection script

Run from `NRWebViewClient.onPageFinished` (and from the fallback bytecode path) via `webView.evaluateJavascript(DETECTION_SCRIPT, null)`:

```js
(function() {
    if (!window.NRWebViewBridge) { return; }
    var attempts = 0;
    var maxAttempts = 8;
    var intervalMs = 250;
    var check = function() {
        if (typeof window.newrelic !== 'undefined') {
            window.NRWebViewBridge.reportBrowserAgentDetected(true);
            return;
        }
        attempts++;
        if (attempts >= maxAttempts) {
            window.NRWebViewBridge.reportBrowserAgentDetected(false);
            return;
        }
        setTimeout(check, intervalMs);
    };
    check();
})();
```

The check **polls** (every 250 ms, up to 8 attempts ≈ 2 s) rather than checking once: a synchronous one-shot check at `onPageFinished` would false-negative on a Browser agent snippet loaded via `<script async>`/`defer` or injected dynamically, since it may not have executed yet at that exact moment. Found by comparing against `feature/capacitor_poc`'s rrweb injector, which solves the analogous "wait for an async-loaded script" problem with `script.onload`.

Detection runs once per `onPageFinished` — no additional handling for SPA-style in-page navigation (same WebView, URL changes without a full page load). This matches the ticket's goal (an adoption-rate signal) without added complexity.

### 4. Wiring and state — `WebViewInstrumentationCallbacks.java`

Existing Java file, edited in place. Three parallel `WeakHashMap`s would drift, so per-WebView state consolidates into one map guarded by `static synchronized` accessors (`WeakHashMap` avoids retaining WebView references):

```java
private static final class WebViewState {
    boolean jsInterfaceInjected;
    NRWebViewClient nrClient;
}
private static final Map<WebView, WebViewState> states = new WeakHashMap<>();
```

**The wrapper and the bytecode hook must not share an entry point**, or the dedup guard would swallow the wrapper's own call:

```java
// bytecode entry point — customer's instrumented WebViewClient subclass
public static void onPageFinishedCalled(WebViewClient c, WebView v, String url) {
    if (hasNRClient(v)) return;        // wrapper already counted this page load
    pageFinished(v, url);              // fallback path only
}

// wrapper entry point — unconditional
static void pageFinished(WebView v, String url) {
    StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_PAGE_FINISHED);
    v.evaluateJavascript(DETECTION_SCRIPT, null);
}
```

The bytecode `onPageFinished` instrumentation in `WebViewMethodClassVisitor` is **kept**, not removed: it remains the fallback for WebViews we never saw a `loadUrl`/`postUrl`/`setWebViewClient` call site for (e.g. content loaded via `loadData` / `loadDataWithBaseURL`).

On that fallback path the metric still fires, but detection generally will not: the JS bridge is registered in `prepare()`, which only runs from `loadUrl`/`postUrl`, so a WebView that never went through those call sites has no `window.NRWebViewBridge` and the script's first line returns early. Detection coverage is therefore bounded by `loadUrl`/`postUrl` interception — unchanged from the pre-wrapper design — while `PageFinished` coverage is bounded by the union of all paths.

`loadUrlCalled` / `postUrlCalled` gain a shared `prepare(webView)` that runs pre-navigation: register the JS bridge (idempotent per WebView, as today) → force-enable JavaScript → install the NR client. Each step is individually try/caught, so a failure in one doesn't skip the others, and nothing propagates into the host app.

`setWebViewClientCalled` handles three edges:

| Input | Behavior |
|---|---|
| `client instanceof NRWebViewClient` | return unchanged (no double-wrap) |
| a wrapper already exists for this WebView | re-parent it to the new delegate, return the existing wrapper |
| `client == null` | wrap null → `super` behavior, preserving the framework's reset-to-default semantics |

### 5. JavaScript force-enable

`evaluateJavascript` is inert when JavaScript is disabled, so `prepare()` enables it:

```java
@SuppressLint("SetJavaScriptEnabled")
WebSettings s = webView.getSettings();
if (!s.getJavaScriptEnabled()) {
    s.setJavaScriptEnabled(true);
    log.warn("New Relic enabled JavaScript on a WebView for browser agent detection");
}
```

**This is a deliberate, signed-off change to host-app behavior and security posture, and it should be reviewed as such.** A WebView the developer intentionally kept script-free will begin executing page scripts (network calls, third-party tracking, DOM rewrites), and enabling JS widens XSS surface on any WebView loading remote content — the reason Android lint flags `SetJavaScriptEnabled`. There is no clean undo: the detection script polls for ~2 s, so the setting cannot be restored afterward without racing our own script.

The alternative considered and rejected was to leave `WebSettings` untouched and instead count a `WebView/JsDisabled` metric, excluding those page loads from the denominator (a JS-disabled WebView cannot be running the Browser agent, so it is a true 0%). That would have produced an unbiased adoption ratio with no behavior change; force-enabling was chosen instead to guarantee the script always executes. The `log.warn` above is the audit trail for support cases.

`DOM storage` and `MIXED_CONTENT_ALWAYS_ALLOW` — which the rrweb POC also sets — are **not** touched; detection does not need them.

### 6. New metric — `MetricNames.java`

```java
public static final String SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_BROWSER_AGENT_DETECTED =
    SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW + "BrowserAgentDetected";
```

Resolves to `Supportability/Mobile/Android/WebView/BrowserAgentDetected`, recorded via `StatsEngine.SUPPORTABILITY.inc(...)` — the same convention already used by `LoadUrl`/`PostUrl`/`PageFinished`.

Note that `PageFinished` volume will **rise** for apps whose `WebViewClient` was previously un-instrumentable, since the wrapper now reports those page loads. That is the intended effect, but it makes the metric non-comparable across the release boundary.

### 7. Gating

No new Gradle DSL property. Everything above executes only when the existing `webviewInstrumentationEnabled` flag is on — the same flag gating today's `loadUrl`/`postUrl`/`onPageFinished` metrics. Customers who already opted into WebView metrics get this automatically; there is no separate opt-out for the JS-injection piece.

### 8. Residual limitation

The wrapper closes the multi-level-`WebViewClient`-subclass gap, because it hooks the *assignment* rather than matching the class hierarchy. What remains is narrower: `WebViewCallSiteVisitor` gates on `owner.equals("android/webkit/WebView")`, and a receiver typed as a customer subclass emits `owner = com/example/MyWebView`. So `MyWebView wv = new MyWebView(); wv.setWebViewClient(…)` is not intercepted unless `MyWebView` overrides the method (in which case `WebViewMethodClassVisitor` sees it). This is why the `loadUrl` install point exists as a second net — and it is the same pre-existing gate that already applies to `loadUrl`/`postUrl` today, not a new limitation introduced here.

### 9. Testing

| Test | Asserts |
|---|---|
| `WebViewCallSiteVisitorTest` (extend) | `setWebViewClient` call site emits `INVOKESTATIC setWebViewClientCalled` and retains the original `INVOKEVIRTUAL` |
| `NRWebViewClientTest.kt` (new) | `onPageFinished` both forwards to the delegate and triggers detection; `shouldOverrideUrlLoading` returns the delegate's value; a null delegate falls through to `super` without crashing |
| `WebViewJSInterfaceTest.kt` (exists) | metric increments only when `detected == true` |
| `WebViewInstrumentationCallbacksTest.kt` (extend) | wrapper installed once per WebView; `setWebViewClientCalled` wraps a customer client and is idempotent; `onPageFinishedCalled` early-returns when a wrapper is present; JavaScript enabled when it was off |

Runtime tests use Robolectric + Mockito (`mock(WebView::class.java)`), matching the existing convention in this module; `android.webkit.WebView`/`WebViewClient` are not final, so `mockito-core` suffices. Run with `./gradlew :agent:testReleaseUnitTest` — this module registers no `testDebugUnitTest` task.

**Functional:** sample app WebView loading a fixture page that defines `window.newrelic` vs. one that doesn't; confirm `Supportability/Mobile/Android/WebView/BrowserAgentDetected` fires only in the first case. Additionally verify a customer `WebViewClient` still receives its callbacks through the wrapper (e.g. a `shouldOverrideUrlLoading` deep-link handler keeps working).

## Out of Scope

- SPA-style in-page navigation detection (URL changes without a full page load).
- A runtime opt-in/opt-out API separate from `webviewInstrumentationEnabled`.
- iOS (tracked separately as NR-464470, cloned from this ticket).
- Injecting/polyfilling `window.newrelic` itself, or the Browser agent — this ticket is detection-only; injection is a follow-up decision gated on what this metric shows.
- Refactoring `WebViewCallSiteVisitor`'s hardcoded temp local slots (100/101).
