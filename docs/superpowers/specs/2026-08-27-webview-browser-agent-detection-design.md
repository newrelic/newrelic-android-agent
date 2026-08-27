# WebView Browser Agent Detection — Design

**Jira:** [NR-461357](https://new-relic.atlassian.net/browse/NR-461357) — [Android] Determine WebView Usage of New Relic Browser Agent
**Parent:** NR-454170 — [Web Views] Enhancements and any new features to support for web views
**Date:** 2026-08-27

## Problem

The agent already emits supportability metrics for WebView *usage* (`loadUrl`, `postUrl`, `onPageFinished` — see `WebViewInstrumentationCallbacks`). What's missing is a signal for how many of those WebViews are loading pages that already run the New Relic Browser (JS) agent (`window.newrelic`). That number decides whether Mobile should inject the Browser agent into WebViews that lack it, or instead unify the experience via a common attribute across platforms.

## Acceptance Criteria (from ticket)

1. Inject a check for `window.newrelic` into the WebView's page.
2. Inject a JavaScript method that can be called (from our own injected script, and manually from a JS console for debugging).
3. Emit a supportability metric if the page is running the NR Browser agent.

## Chosen Approach

**`evaluateJavascript` detection script + `@JavascriptInterface` bridge**, hooked into the existing WebView bytecode-instrumentation call sites — no new public API, no new Gradle DSL flag.

### Why this approach

Two approaches were considered:

- **(A, chosen) `evaluateJavascript` + JS interface bridge.** The detection script itself is the "JS method" the AC asks for, and it's also manually callable from Chrome DevTools console against the same bridge object — satisfying AC #2 literally, not just in spirit.
- **(B, rejected) `evaluateJavascript` `ValueCallback<String>` only, no bridge.** Simpler and avoids a WebView JS-context timing quirk (below), but doesn't expose anything callable from a console — AC #2 would still require bolting the bridge on anyway, erasing the simplification.

## Design

### 1. Runtime bridge — `agent/src/main/java/com/newrelic/agent/android/webView/WebViewJSInterface.java` (new)

```java
public class WebViewJSInterface {
    @JavascriptInterface
    public void reportBrowserAgentDetected(boolean detected) {
        if (detected) {
            StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_BROWSER_AGENT_DETECTED);
        }
    }
}
```

Only increments on `detected == true`, per the AC's wording ("emit a metric *if* the page is running the browser agent"). The existing `PageFinished` metric already gives a denominator (total page loads observed), so a numerator-only metric is sufficient to compute adoption %.

### 2. Wiring — `WebViewInstrumentationCallbacks.java`

- **`loadUrlCalled(WebView)` / `postUrlCalled(WebView)`**: before incrementing their existing metrics, call a new `ensureJsInterfaceInjected(WebView)`. This registers the bridge via `webView.addJavascriptInterface(new WebViewJSInterface(), INTERFACE_NAME)` **once per `WebView` instance**, tracked via a `WeakHashMap<WebView, Boolean>` (avoids re-registering on repeated navigations in the same WebView, and avoids leaking WebView references).
  - Registering here — at `loadUrl`/`postUrl`, *before* the navigation actually happens — is deliberate: Android only reliably exposes a JS interface to a page's JS context if the interface was added before that page's navigation started. Adding it after `onPageFinished` would miss the current page.
- **`onPageFinishedCalled(WebViewClient, WebView, String)`**: after the existing `PageFinished` metric increment, call `webView.evaluateJavascript(DETECTION_SCRIPT, null)` where:
  ```js
  (function() {
      if (window.NRWebViewBridge) {
          window.NRWebViewBridge.reportBrowserAgentDetected(typeof window.newrelic !== 'undefined');
      }
  })();
  ```
  (`INTERFACE_NAME` = `"NRWebViewBridge"`, matched between step 2's `addJavascriptInterface` call and this script.)

Detection runs once per `onPageFinished` — no additional handling for SPA-style in-page navigation (same WebView, URL changes without a full page load). This matches the ticket's goal (an adoption-rate signal) without added complexity.

### 3. New metric — `MetricNames.java`

```java
public static final String SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_BROWSER_AGENT_DETECTED =
    SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW + "BrowserAgentDetected";
```

Resolves to `Supportability/Mobile/Android/WebView/BrowserAgentDetected`, recorded via `StatsEngine.SUPPORTABILITY.inc(...)` — same call convention already used by `LoadUrl`/`PostUrl`/`PageFinished` in `WebViewInstrumentationCallbacks`.

### 4. Coverage gap — known limitation, not fixed by this ticket

Today, `onPageFinished` is only instrumented when the app's class's **immediate** superclass is exactly `android.webkit.WebViewClient` (`WebViewMethodClassVisitor`'s `isInstrumentable` check compares ASM's single, per-class `superName` against a regex via `Matcher.matches()` — there is no ancestor-chain walk anywhere in the instrumentation module). A `WebViewClient` subclassed through an intermediate class (e.g. a library's own base client) is invisible to this visitor, silently skipping both the existing `PageFinished` metric and the new detection script for that app.

**A "broaden the regex" fix was investigated and rejected as ineffective**: `WEBVIEW_CLASSES`'s existing non-anchored `"^android/webkit/WebView"` pattern was found to behave *identically* to its anchored sibling under `Matcher.matches()` (the anchor is redundant under `matches()`, which requires a full-string match regardless) — it's dead code today, not a working example of "transitive subclass matching" to mirror. More fundamentally, no regex change can fix this: `isInstrumentable` only ever sees one class's immediate `superName` at a time, never a resolved ancestor chain, so a class two levels below `WebViewClient` presents a `superName` (its direct parent's name) that contains no trace of `"WebViewClient"` at all.

A real fix would require either (a) a two-pass class-hierarchy map across all module class inputs, or (b) the reflective `Class.forName(...).getSuperclass()` loop `PatchedClassWriter` uses elsewhere for stack-map-frame computation — which other comments in this codebase already flag as fragile under stricter classloader isolation (Gradle 9 `LinkageError`/`IllegalAccessError` risk). Both are materially bigger, riskier changes than this ticket's scope. **Decision: leave this as a known limitation, documented here, with a follow-up ticket to address it separately** (candidate approach: wrap the app's `WebViewClient` at `setWebViewClient()` call sites with a proxy, the same call-site-interception style `WebViewCallSiteVisitor` already uses for `loadUrl`/`postUrl` — sidesteps subclass-depth matching entirely since it hooks the assignment, not the class hierarchy).

### 5. Gating

No new Gradle DSL property. Everything above only executes when the existing `webviewInstrumentationEnabled` flag is on (same flag gating today's `loadUrl`/`postUrl`/`onPageFinished` metrics). Customers already opted into WebView usage metrics get this automatically; there's no separate opt-out for just the JS-injection piece.

### 6. Testing

- **Bytecode (`instrumentation` module):** verify the `WebViewCallSiteVisitor`-injected call sites call `ensureJsInterfaceInjected`; verify `WebViewMethodClassVisitor`'s broadened regex matches a 2-level-deep `WebViewClient` subclass fixture (and still matches the direct-subclass case).
- **Runtime (`agent` module):** unit tests on `WebViewInstrumentationCallbacks` / `WebViewJSInterface` — idempotent interface registration (no double `addJavascriptInterface` across repeated `loadUrl` calls on the same `WebView`), and metric increments only when `detected == true`.
- **Functional:** sample app WebView loading a fixture page that defines `window.newrelic` vs. one that doesn't; confirm `Supportability/Mobile/Android/WebView/BrowserAgentDetected` fires only in the first case.

## Out of Scope

- SPA-style in-page navigation detection (URL changes without a full page load).
- A runtime opt-in/opt-out API separate from `webviewInstrumentationEnabled`.
- iOS (tracked separately as NR-464470, cloned from this ticket).
- Injecting/polyfilling `window.newrelic` itself, or the Browser agent — this ticket is detection-only; injection is a follow-up decision gated on what this metric shows.
