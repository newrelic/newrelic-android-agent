# Agent-Side WebViewClient Installation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Install a delegating `WebViewClient` from the agent so browser-agent detection runs regardless of the host app's `WebViewClient` class shape, without dropping any of the app's own callbacks.

**Architecture:** A new `NRWebViewClient` forwards every `WebViewClient` callback to the app's client (or to a plain `WebViewClient` sentinel when there is none) and adds detection in `onPageFinished`. It is installed from two places: a new bytecode interception of `WebView.setWebViewClient(…)` that rewrites the argument, and the existing `loadUrl`/`postUrl` hooks (API 26+ only, where `getWebViewClient()` can preserve the existing client). Per-WebView state consolidates into one `WeakHashMap`, and a guard keeps the retained bytecode `onPageFinished` hook from double-counting.

**Tech Stack:** Java 17 + Kotlin (agent module, Android library), ASM 9 (instrumentation module), JUnit 4 + Robolectric + Mockito.

**Spec:** `docs/superpowers/specs/2026-08-27-webview-browser-agent-detection-design.md`

---

## File Structure

| File | Responsibility | Change |
|---|---|---|
| `agent/src/main/kotlin/com/newrelic/agent/android/webView/NRWebViewClient.kt` | Delegating `WebViewClient`; only `onPageFinished` adds behavior | **Create** |
| `agent/src/main/java/com/newrelic/agent/android/webView/WebViewInstrumentationCallbacks.java` | Static bytecode entry points, per-WebView state, install/dedup logic | Modify |
| `agent/src/test/java/com/newrelic/agent/android/webView/NRWebViewClientTest.kt` | Wrapper delegation behavior | **Create** |
| `agent/src/test/java/com/newrelic/agent/android/webView/WebViewInstrumentationCallbacksTest.kt` | Entry points, install, dedup, JS enable | Modify |
| `instrumentation/src/main/java/com/newrelic/agent/compile/visitor/WebViewCallSiteVisitor.java` | Rewrite `setWebViewClient` call sites | Modify |
| `instrumentation/src/test/java/com/newrelic/agent/compile/visitor/WebViewCallSiteVisitorTest.java` | Bytecode assertions | Modify |

**Already done on this branch — do not redo:** `MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_BROWSER_AGENT_DETECTED` (commit `efffb916`), `WebViewJSInterface.kt` + its test (`118122ef`), the retry-poll `DETECTION_SCRIPT` (`4a36043f`).

**No gating code is needed.** `WebViewCallSiteVisitor` and `WebViewMethodClassVisitor` are registered only inside `if (webviewInstrumentationEnabled)` at `InvocationDispatcher.java:173-178`, so everything in this plan inherits that flag automatically. Do not add a second gate — and do not move the new interception outside that block, or apps that opted out would start having their `WebViewClient` wrapped.

**Test command for the agent module:** `./gradlew :agent:testReleaseUnitTest --tests "com.newrelic.agent.android.webView.*"`
There is **no** `testDebugUnitTest` task in this module — using it fails with "Unknown command-line option '--tests'".

**Test command for the instrumentation module:** `./gradlew :instrumentation:test --tests "com.newrelic.agent.compile.visitor.WebViewCallSiteVisitorTest"`

---

### Task 1: Consolidate per-WebView state and extract `pageFinished`

Groundwork. Three parallel `WeakHashMap`s would drift as later tasks add fields, and `NRWebViewClient` (Task 2) needs a public seam to report a page load through. No behavior change.

**Files:**
- Modify: `agent/src/main/java/com/newrelic/agent/android/webView/WebViewInstrumentationCallbacks.java`
- Test: `agent/src/test/java/com/newrelic/agent/android/webView/WebViewInstrumentationCallbacksTest.kt`

- [ ] **Step 1: Write the failing test**

Append this test inside the existing `WebViewInstrumentationCallbacksTest` class in `agent/src/test/java/com/newrelic/agent/android/webView/WebViewInstrumentationCallbacksTest.kt`:

```kotlin
    @Test
    fun pageFinished_incrementsMetricAndEvaluatesDetectionScript() {
        WebViewInstrumentationCallbacks.pageFinished(webView, "https://example.com")

        assertTrue(StatsEngine.SUPPORTABILITY.statsMap
                .containsKey(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_PAGE_FINISHED))

        val scriptCaptor = ArgumentCaptor.forClass(String::class.java)
        verify(webView, times(1)).evaluateJavascript(scriptCaptor.capture(), isNull())
        assertTrue(scriptCaptor.value.contains(WebViewJSInterface.INTERFACE_NAME))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :agent:testReleaseUnitTest --tests "com.newrelic.agent.android.webView.WebViewInstrumentationCallbacksTest"`
Expected: FAIL — Kotlin compile error, `Unresolved reference: pageFinished`.

- [ ] **Step 3: Write minimal implementation**

In `WebViewInstrumentationCallbacks.java`, replace the field declaration:

```java
    private static final Map<WebView, Boolean> jsInterfaceInjected = new WeakHashMap<>();
```

with:

```java
    private static final Map<WebView, WebViewState> states = new WeakHashMap<>();

    /**
     * Per-WebView instrumentation state. Held in a WeakHashMap so tracking a WebView never
     * keeps it (and its Activity) alive.
     */
    static final class WebViewState {
        boolean jsInterfaceInjected;
    }

    private static synchronized WebViewState stateFor(WebView webView) {
        WebViewState state = states.get(webView);
        if (state == null) {
            state = new WebViewState();
            states.put(webView, state);
        }
        return state;
    }
```

Replace `ensureJsInterfaceInjected` with the state-object version:

```java
    static synchronized void ensureJsInterfaceInjected(WebView webView) {
        if (webView == null) {
            return;
        }
        WebViewState state = stateFor(webView);
        if (state.jsInterfaceInjected) {
            return;
        }
        state.jsInterfaceInjected = true;
        try {
            webView.addJavascriptInterface(new WebViewJSInterface(), WebViewJSInterface.INTERFACE_NAME);
        } catch (Exception e) {
            log.error("Failed to inject NR WebView JS interface", e);
        }
    }
```

Replace `onPageFinishedCalled` with a thin delegation to a new public seam. Note the metric increments before the null check, preserving the existing behavior exactly:

```java
    /**
     * Records a completed page load and runs browser-agent detection. Called by
     * {@link NRWebViewClient} and, on WebViews with no NR client installed, by the
     * bytecode-instrumented {@code onPageFinished} override in the host app.
     */
    public static void pageFinished(WebView webView, String url) {
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_PAGE_FINISHED);
        if (webView == null) {
            return;
        }
        try {
            webView.evaluateJavascript(DETECTION_SCRIPT, null);
        } catch (Exception e) {
            log.error("Failed to run NR browser agent detection script", e);
        }
    }

    public static void onPageFinishedCalled(WebViewClient var0, WebView var1, String var2) {
        pageFinished(var1, var2);
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :agent:testReleaseUnitTest --tests "com.newrelic.agent.android.webView.*"`
Expected: PASS — the new test plus all four pre-existing tests (`loadUrlCalled_incrementsMetricAndInjectsJsInterfaceOnce`, `postUrlCalled_doesNotReinjectJsInterfaceIfLoadUrlAlreadyDid`, `onPageFinishedCalled_incrementsMetricAndEvaluatesDetectionScript`, and the two `WebViewJSInterfaceTest` cases). The pre-existing tests passing unchanged is what proves this refactor is behavior-neutral.

- [ ] **Step 5: Commit**

```bash
git add agent/src/main/java/com/newrelic/agent/android/webView/WebViewInstrumentationCallbacks.java \
        agent/src/test/java/com/newrelic/agent/android/webView/WebViewInstrumentationCallbacksTest.kt
git commit -m "refactor: consolidate per-WebView state and extract pageFinished seam (NR-461357)"
```

---

### Task 2: `NRWebViewClient` delegating wrapper

**Files:**
- Create: `agent/src/main/kotlin/com/newrelic/agent/android/webView/NRWebViewClient.kt`
- Create: `agent/src/test/java/com/newrelic/agent/android/webView/NRWebViewClientTest.kt`

Note the source roots: **main** Kotlin goes in `src/main/kotlin/` (wired via `agent/build.gradle:32`), **test** Kotlin goes in `src/test/java/` alongside the existing `.kt` tests.

- [ ] **Step 1: Write the failing test**

Create `agent/src/test/java/com/newrelic/agent/android/webView/NRWebViewClientTest.kt`:

```kotlin
package com.newrelic.agent.android.webView

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

import com.newrelic.agent.android.metric.MetricNames
import com.newrelic.agent.android.stats.StatsEngine

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NRWebViewClientTest {

    private lateinit var webView: WebView
    private lateinit var delegate: WebViewClient

    @Before
    fun setUp() {
        webView = mock(WebView::class.java)
        delegate = mock(WebViewClient::class.java)
        StatsEngine.SUPPORTABILITY.statsMap.clear()
    }

    @After
    fun tearDown() {
        StatsEngine.SUPPORTABILITY.statsMap.clear()
    }

    @Test
    fun onPageFinished_forwardsToDelegateAndRecordsPageLoad() {
        NRWebViewClient(delegate).onPageFinished(webView, "https://example.com")

        verify(delegate, times(1)).onPageFinished(webView, "https://example.com")
        assertTrue(StatsEngine.SUPPORTABILITY.statsMap
                .containsKey(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_PAGE_FINISHED))
        verify(webView, times(1)).evaluateJavascript(org.mockito.ArgumentMatchers.anyString(), isNull())
    }

    @Test
    fun shouldOverrideUrlLoading_returnsDelegateDecision() {
        val request = mock(WebResourceRequest::class.java)
        `when`(delegate.shouldOverrideUrlLoading(webView, request)).thenReturn(true)

        assertTrue(NRWebViewClient(delegate).shouldOverrideUrlLoading(webView, request))
    }

    @Test
    fun nullDelegate_behavesLikePlainWebViewClient() {
        val request = mock(WebResourceRequest::class.java)
        // WebViewClient's default shouldOverrideUrlLoading(View, Request) dereferences request.url,
        // so it must be stubbed even though the assertion is about the return value.
        `when`(request.url).thenReturn(Uri.parse("https://example.com"))

        val client = NRWebViewClient(null)

        assertFalse(client.shouldOverrideUrlLoading(webView, request))
        assertNull(client.shouldInterceptRequest(webView, request))
    }

    @Test
    fun delegate_isReparentable() {
        val client = NRWebViewClient(delegate)
        val replacement = mock(WebViewClient::class.java)

        client.delegate = replacement
        client.onPageFinished(webView, "https://example.com")

        assertSame(replacement, client.delegate)
        verify(replacement, times(1)).onPageFinished(webView, "https://example.com")
        verify(delegate, times(0)).onPageFinished(webView, "https://example.com")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :agent:testReleaseUnitTest --tests "com.newrelic.agent.android.webView.NRWebViewClientTest"`
Expected: FAIL — Kotlin compile error, `Unresolved reference: NRWebViewClient`.

- [ ] **Step 3: Write minimal implementation**

Create `agent/src/main/kotlin/com/newrelic/agent/android/webView/NRWebViewClient.kt`:

```kotlin
/*
 * Copyright (c) 2026-present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.webView

import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.os.Message
import android.view.KeyEvent
import android.webkit.ClientCertRequest
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

import androidx.annotation.RequiresApi

/**
 * The [WebViewClient] the agent installs on instrumented WebViews so that
 * [WebViewInstrumentationCallbacks.pageFinished] runs regardless of whether the host app's own
 * client was reachable by bytecode instrumentation.
 *
 * Every callback is forwarded to [delegate]. When the host app had no client of its own,
 * [delegate] is a plain [WebViewClient] instance whose behavior is by definition identical to
 * `super` — which is why no callback below needs a null branch. Only [onPageFinished] adds
 * behavior; everything else is pass-through.
 */
class NRWebViewClient(delegate: WebViewClient?) : WebViewClient() {

    @Volatile
    var delegate: WebViewClient = delegate ?: WebViewClient()

    override fun onPageFinished(view: WebView, url: String?) {
        WebViewInstrumentationCallbacks.pageFinished(view, url)
        delegate.onPageFinished(view, url)
    }

    // ---- page lifecycle ----

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        delegate.onPageStarted(view, url, favicon)
    }

    override fun onLoadResource(view: WebView, url: String?) {
        delegate.onLoadResource(view, url)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onPageCommitVisible(view: WebView, url: String?) {
        delegate.onPageCommitVisible(view, url)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        delegate.doUpdateVisitedHistory(view, url, isReload)
    }

    // ---- navigation ----

    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean =
        delegate.shouldOverrideUrlLoading(view, url)

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        delegate.shouldOverrideUrlLoading(view, request)

    override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) {
        delegate.onFormResubmission(view, dontResend, resend)
    }

    // ---- resource interception ----

    @Suppress("DEPRECATION")
    override fun shouldInterceptRequest(view: WebView, url: String?): WebResourceResponse? =
        delegate.shouldInterceptRequest(view, url)

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
        delegate.shouldInterceptRequest(view, request)

    // ---- errors ----

    @Suppress("DEPRECATION")
    override fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {
        delegate.onReceivedError(view, errorCode, description, failingUrl)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        delegate.onReceivedError(view, request, error)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
        delegate.onReceivedHttpError(view, request, errorResponse)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean =
        delegate.onRenderProcessGone(view, detail)

    // ---- security ----

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        delegate.onReceivedSslError(view, handler, error)
    }

    override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
        delegate.onReceivedClientCertRequest(view, request)
    }

    override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String?, realm: String?) {
        delegate.onReceivedHttpAuthRequest(view, handler, host, realm)
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    override fun onSafeBrowsingHit(
        view: WebView,
        request: WebResourceRequest,
        threatType: Int,
        callback: SafeBrowsingResponse
    ) {
        delegate.onSafeBrowsingHit(view, request, threatType, callback)
    }

    override fun onReceivedLoginRequest(view: WebView, realm: String?, account: String?, args: String?) {
        delegate.onReceivedLoginRequest(view, realm, account, args)
    }

    // ---- input / display ----

    override fun shouldOverrideKeyEvent(view: WebView, event: KeyEvent): Boolean =
        delegate.shouldOverrideKeyEvent(view, event)

    override fun onUnhandledKeyEvent(view: WebView, event: KeyEvent) {
        delegate.onUnhandledKeyEvent(view, event)
    }

    override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) {
        delegate.onScaleChanged(view, oldScale, newScale)
    }
}
```

Two rules to follow if the compiler objects, rather than guessing:

1. **Nullability mismatch on a forwarded parameter** (e.g. "Type mismatch: inferred type is String? but String was expected"): the SDK annotates that parameter `@NonNull`. Change the override's parameter to non-null to match. Do **not** work around it with `!!` — that converts a framework-supplied value into a crash.
2. **Never widen an object parameter to nullable just to silence a warning.** Kotlin inserts a null check on non-null parameters, so a wrong choice here surfaces as an `IllegalArgumentException` thrown *inside a framework callback* — a hard crash in the host app. The nullable parameters above (`url`, `favicon`, `description`, `failingUrl`, `host`, `realm`, `account`, `args`) are exactly the ones the framework can legitimately pass null for.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :agent:testReleaseUnitTest --tests "com.newrelic.agent.android.webView.*"`
Expected: PASS — 4 new `NRWebViewClientTest` cases plus all existing ones.

- [ ] **Step 5: Commit**

```bash
git add agent/src/main/kotlin/com/newrelic/agent/android/webView/NRWebViewClient.kt \
        agent/src/test/java/com/newrelic/agent/android/webView/NRWebViewClientTest.kt
git commit -m "feat: add delegating NRWebViewClient wrapper (NR-461357)"
```

---

### Task 3: `setWebViewClientCalled` runtime hook and dedup guard

The runtime half of the bytecode interception, plus the guard that stops the wrapper and the retained bytecode `onPageFinished` hook from both counting the same page load.

**Files:**
- Modify: `agent/src/main/java/com/newrelic/agent/android/webView/WebViewInstrumentationCallbacks.java`
- Test: `agent/src/test/java/com/newrelic/agent/android/webView/WebViewInstrumentationCallbacksTest.kt`

- [ ] **Step 1: Write the failing test**

Append to `WebViewInstrumentationCallbacksTest`:

```kotlin
    @Test
    fun setWebViewClientCalled_wrapsCustomerClient() {
        val customer = mock(WebViewClient::class.java)

        val result = WebViewInstrumentationCallbacks.setWebViewClientCalled(webView, customer)

        assertTrue("Should return an NR wrapper", result is NRWebViewClient)
        assertSame(customer, (result as NRWebViewClient).delegate)
    }

    @Test
    fun setWebViewClientCalled_doesNotDoubleWrap() {
        val alreadyOurs = NRWebViewClient(mock(WebViewClient::class.java))

        val result = WebViewInstrumentationCallbacks.setWebViewClientCalled(webView, alreadyOurs)

        assertSame(alreadyOurs, result)
    }

    @Test
    fun setWebViewClientCalled_reparentsExistingWrapper() {
        val first = mock(WebViewClient::class.java)
        val second = mock(WebViewClient::class.java)

        val wrapper = WebViewInstrumentationCallbacks.setWebViewClientCalled(webView, first)
        val again = WebViewInstrumentationCallbacks.setWebViewClientCalled(webView, second)

        assertSame("Should reuse the same wrapper instance", wrapper, again)
        assertSame(second, (again as NRWebViewClient).delegate)
    }

    @Test
    fun setWebViewClientCalled_nullClient_wrapsWithDefaultBehavior() {
        val result = WebViewInstrumentationCallbacks.setWebViewClientCalled(webView, null)

        assertTrue(result is NRWebViewClient)
        assertNotNull("Delegate must never be null", (result as NRWebViewClient).delegate)
    }

    @Test
    fun onPageFinishedCalled_isSuppressedWhenNRClientInstalled() {
        WebViewInstrumentationCallbacks.setWebViewClientCalled(webView, mock(WebViewClient::class.java))

        WebViewInstrumentationCallbacks.onPageFinishedCalled(
                mock(WebViewClient::class.java), webView, "https://example.com")

        assertFalse("Wrapper already counts this page load",
                StatsEngine.SUPPORTABILITY.statsMap
                        .containsKey(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_PAGE_FINISHED))
        verify(webView, times(0)).evaluateJavascript(org.mockito.ArgumentMatchers.anyString(), isNull())
    }
```

Add these imports to the test file's import block:

```kotlin
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :agent:testReleaseUnitTest --tests "com.newrelic.agent.android.webView.WebViewInstrumentationCallbacksTest"`
Expected: FAIL — Kotlin compile error, `Unresolved reference: setWebViewClientCalled`.

- [ ] **Step 3: Write minimal implementation**

In `WebViewInstrumentationCallbacks.java`, add the `nrClient` field to `WebViewState`:

```java
    static final class WebViewState {
        boolean jsInterfaceInjected;
        NRWebViewClient nrClient;
    }
```

Add the hook and the guard helper:

```java
    /**
     * Bytecode entry point for {@code WebView.setWebViewClient(WebViewClient)}. Returns the client
     * that should actually be set — an {@link NRWebViewClient} wrapping the caller's client, so
     * every callback the host app registered still fires.
     *
     * Fails open: any problem returns the original client unchanged rather than breaking the
     * host app's call.
     */
    public static synchronized WebViewClient setWebViewClientCalled(WebView webView, WebViewClient client) {
        if (client instanceof NRWebViewClient) {
            return client;              // already ours (also covers the agent's own install call)
        }
        if (webView == null) {
            return client;              // nothing to key state on; don't alter behavior
        }
        try {
            WebViewState state = stateFor(webView);
            if (state.nrClient != null) {
                state.nrClient.setDelegate(client != null ? client : new WebViewClient());
                return state.nrClient;  // re-parent rather than stacking a second wrapper
            }
            NRWebViewClient wrapper = new NRWebViewClient(client);
            state.nrClient = wrapper;
            return wrapper;
        } catch (Throwable t) {
            log.error("Failed to wrap WebViewClient; using the original", t);
            return client;
        }
    }

    private static synchronized boolean hasNRClient(WebView webView) {
        if (webView == null) {
            return false;
        }
        WebViewState state = states.get(webView);   // deliberately not stateFor(): no entry created
        return state != null && state.nrClient != null;
    }
```

Add the guard to `onPageFinishedCalled`:

```java
    public static void onPageFinishedCalled(WebViewClient var0, WebView var1, String var2) {
        if (hasNRClient(var1)) {
            return;     // NRWebViewClient.onPageFinished already reported this page load
        }
        pageFinished(var1, var2);
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :agent:testReleaseUnitTest --tests "com.newrelic.agent.android.webView.*"`
Expected: PASS. Note that `onPageFinishedCalled_incrementsMetricAndEvaluatesDetectionScript` (from before this change) still passes because it uses a fresh `mock(WebView)` with no wrapper installed — that is the fallback path.

- [ ] **Step 5: Commit**

```bash
git add agent/src/main/java/com/newrelic/agent/android/webView/WebViewInstrumentationCallbacks.java \
        agent/src/test/java/com/newrelic/agent/android/webView/WebViewInstrumentationCallbacksTest.kt
git commit -m "feat: wrap host WebViewClient at setWebViewClient and dedup onPageFinished (NR-461357)"
```

---

### Task 4: Install the wrapper and enable JavaScript at `loadUrl` / `postUrl`

**Files:**
- Modify: `agent/src/main/java/com/newrelic/agent/android/webView/WebViewInstrumentationCallbacks.java`
- Test: `agent/src/test/java/com/newrelic/agent/android/webView/WebViewInstrumentationCallbacksTest.kt`

- [ ] **Step 1: Write the failing test**

Append to `WebViewInstrumentationCallbacksTest`:

```kotlin
    @Test
    fun loadUrlCalled_enablesJavaScriptWhenDisabled() {
        val settings = mock(WebSettings::class.java)
        `when`(webView.getSettings()).thenReturn(settings)
        `when`(settings.getJavaScriptEnabled()).thenReturn(false)

        WebViewInstrumentationCallbacks.loadUrlCalled(webView)

        verify(settings, times(1)).setJavaScriptEnabled(true)
    }

    @Test
    fun loadUrlCalled_leavesJavaScriptAloneWhenAlreadyEnabled() {
        val settings = mock(WebSettings::class.java)
        `when`(webView.getSettings()).thenReturn(settings)
        `when`(settings.getJavaScriptEnabled()).thenReturn(true)

        WebViewInstrumentationCallbacks.loadUrlCalled(webView)

        verify(settings, times(0)).setJavaScriptEnabled(anyBoolean())
    }

    @Test
    fun loadUrlCalled_installsWrapperPreservingExistingClient() {
        val customer = mock(WebViewClient::class.java)
        `when`(webView.getWebViewClient()).thenReturn(customer)

        WebViewInstrumentationCallbacks.loadUrlCalled(webView)

        val clientCaptor = ArgumentCaptor.forClass(WebViewClient::class.java)
        verify(webView, times(1)).setWebViewClient(clientCaptor.capture())
        val installed = clientCaptor.value
        assertTrue(installed is NRWebViewClient)
        assertSame("Existing client must be preserved as the delegate",
                customer, (installed as NRWebViewClient).delegate)
    }

    @Test
    fun loadUrlCalled_doesNotReinstallWhenSetWebViewClientAlreadyWrapped() {
        // Ordering case from spec 2.4: client set first, then load.
        WebViewInstrumentationCallbacks.setWebViewClientCalled(webView, mock(WebViewClient::class.java))

        WebViewInstrumentationCallbacks.loadUrlCalled(webView)

        verify(webView, times(0)).setWebViewClient(org.mockito.ArgumentMatchers.any())
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.N])
    fun loadUrlCalled_belowApi26_doesNotInstallWrapper() {
        WebViewInstrumentationCallbacks.loadUrlCalled(webView)

        // getWebViewClient() does not exist below API 26, so an existing client could not be
        // preserved; the safety rule is to skip installation rather than risk clobbering it.
        verify(webView, times(0)).setWebViewClient(org.mockito.ArgumentMatchers.any())
    }
```

Mockito verification uses the explicit Java accessor forms (`getSettings()`, `setWebViewClient(…)`) rather than Kotlin's synthetic property syntax — `verify(mock).property = value` is valid Kotlin but reads ambiguously in a verification chain, and `getWebViewClient()` only became API 26, so the synthetic property is the less obvious spelling here.

Add these imports to the test file:

```kotlin
import android.os.Build
import android.webkit.WebSettings
import org.mockito.ArgumentMatchers.anyBoolean
import org.robolectric.annotation.Config
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :agent:testReleaseUnitTest --tests "com.newrelic.agent.android.webView.WebViewInstrumentationCallbacksTest"`
Expected: FAIL — `loadUrlCalled_enablesJavaScriptWhenDisabled` fails with "Wanted but not invoked: settings.setJavaScriptEnabled(true)", and `loadUrlCalled_installsWrapperPreservingExistingClient` fails with "Wanted but not invoked: webView.setWebViewClient(…)". The two negative tests (`leavesJavaScriptAlone…`, `doesNotReinstall…`, `belowApi26…`) pass trivially before the implementation exists — that is expected; they are regression guards, and Step 4 is what makes them meaningful.

- [ ] **Step 3: Write minimal implementation**

Add these imports to `WebViewInstrumentationCallbacks.java`:

```java
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.os.Build;
import android.webkit.WebSettings;
```

Add the three helpers:

```java
    /**
     * Enables JavaScript so the detection script can run. This deliberately overrides a host-app
     * setting — see section 5 of the design spec. The warning is the audit trail for support cases.
     */
    @SuppressLint("SetJavaScriptEnabled")
    static void enableJavaScript(WebView webView) {
        if (webView == null) {
            return;
        }
        try {
            WebSettings settings = webView.getSettings();
            if (settings != null && !settings.getJavaScriptEnabled()) {
                settings.setJavaScriptEnabled(true);
                log.warn("New Relic enabled JavaScript on a WebView for browser agent detection");
            }
        } catch (Throwable t) {
            log.error("Failed to enable JavaScript for NR browser agent detection", t);
        }
    }

    /**
     * Installs an {@link NRWebViewClient} on this WebView, preserving any client already set.
     *
     * Skipped below API 26: {@code getWebViewClient()} does not exist there, so an existing client
     * could neither be read nor distinguished from "no client set" — and installing blind would
     * silently drop the host app's callbacks. On those API levels the wrapper arrives only via
     * {@link #setWebViewClientCalled}, which carries the delegate by construction.
     */
    @TargetApi(Build.VERSION_CODES.O)
    static synchronized void ensureNRClientInstalled(WebView webView) {
        if (webView == null) {
            return;
        }
        WebViewState state = stateFor(webView);
        if (state.nrClient != null) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            log.debug("Skipping NR WebViewClient install below API 26: existing client cannot be preserved");
            return;
        }
        try {
            WebViewClient existing = webView.getWebViewClient();
            if (existing instanceof NRWebViewClient) {
                state.nrClient = (NRWebViewClient) existing;
                return;
            }
            NRWebViewClient wrapper = new NRWebViewClient(existing);
            state.nrClient = wrapper;
            webView.setWebViewClient(wrapper);
        } catch (Throwable t) {
            log.error("Failed to install NR WebViewClient", t);
        }
    }

    /**
     * Pre-navigation setup, run inline on the caller's thread from the loadUrl/postUrl call sites.
     * Never posted to the WebView's handler: a posted install can land after onPageFinished has
     * already fired for the page we wanted to inspect.
     */
    static void prepare(WebView webView) {
        ensureJsInterfaceInjected(webView);
        enableJavaScript(webView);
        ensureNRClientInstalled(webView);
    }
```

Replace the bodies of the two load hooks:

```java
    public static void loadUrlCalled(WebView var0) {
        prepare(var0);
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_LOAD_URL);
    }

    public static void postUrlCalled(WebView var0) {
        prepare(var0);
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_POST_URL);
    }
```

The `webView.setWebViewClient(wrapper)` call above sits inside the AAR, which the plugin also transforms in host builds — so it can itself be rewritten to route through `setWebViewClientCalled`. That is harmless: the `client instanceof NRWebViewClient` check returns the wrapper unchanged.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :agent:testReleaseUnitTest --tests "com.newrelic.agent.android.webView.*"`
Expected: PASS.

If `loadUrlCalled_belowApi26_doesNotInstallWrapper` fails because Robolectric cannot obtain the API 24 `android-all` jar (offline build), delete **only that test** and note the gap in the commit message — the guard is still covered by the functional check in the "After Implementation" section. Do not weaken the production guard to make a test pass.

- [ ] **Step 5: Commit**

```bash
git add agent/src/main/java/com/newrelic/agent/android/webView/WebViewInstrumentationCallbacks.java \
        agent/src/test/java/com/newrelic/agent/android/webView/WebViewInstrumentationCallbacksTest.kt
git commit -m "feat: install NRWebViewClient and enable JavaScript at loadUrl/postUrl (NR-461357)"
```

---

### Task 5: Intercept `setWebViewClient` call sites in bytecode

**Files:**
- Modify: `instrumentation/src/main/java/com/newrelic/agent/compile/visitor/WebViewCallSiteVisitor.java`
- Test: `instrumentation/src/test/java/com/newrelic/agent/compile/visitor/WebViewCallSiteVisitorTest.java`

- [ ] **Step 1: Write the failing test**

The existing `/WebViewTest.class` fixture contains no `setWebViewClient` call, so the test generates its own fixture with ASM instead of committing a new binary resource. Append to `WebViewCallSiteVisitorTest`:

```java
    @Test
    public void testSetWebViewClientIsWrapped() {
        byte[] classBytes = fixtureCallingSetWebViewClient();
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor visitor = new WebViewCallSiteVisitor(classWriter, instrumentationContext, InstrumentationAgent.LOGGER);
        new ClassReader(classBytes).accept(visitor, ClassReader.EXPAND_FRAMES);
        Assert.assertTrue("Class should be modified by the visitor", instrumentationContext.isClassModified());

        byte[] modifiedBytes = classWriter.toByteArray();

        final MethodCallCounter callbackCounter = new MethodCallCounter(
                "com/newrelic/agent/android/webView/WebViewInstrumentationCallbacks",
                "setWebViewClientCalled"
        );
        new ClassReader(modifiedBytes).accept(callbackCounter, ClassReader.EXPAND_FRAMES);
        Assert.assertEquals("Should have wrapped one setWebViewClient call",
                1, callbackCounter.getCount("setWebViewClientCalled"));

        final MethodCallCounter originalCounter = new MethodCallCounter(
                "android/webkit/WebView",
                "setWebViewClient"
        );
        new ClassReader(modifiedBytes).accept(originalCounter, ClassReader.EXPAND_FRAMES);
        Assert.assertEquals("The original setWebViewClient call must be preserved",
                1, originalCounter.getCount("setWebViewClient"));
    }

    /**
     * Builds a class equivalent to:
     * {@code static void install(WebView v, WebViewClient c) { v.setWebViewClient(c); } }
     */
    private static byte[] fixtureCallingSetWebViewClient() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "com/example/SetClientFixture",
                null, "java/lang/Object", null);

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "install",
                "(Landroid/webkit/WebView;Landroid/webkit/WebViewClient;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/webkit/WebView", "setWebViewClient",
                "(Landroid/webkit/WebViewClient;)V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :instrumentation:test --tests "com.newrelic.agent.compile.visitor.WebViewCallSiteVisitorTest"`
Expected: FAIL — `Class should be modified by the visitor` assertion fails (the visitor passes `setWebViewClient` straight through today).

- [ ] **Step 3: Write minimal implementation**

In `WebViewCallSiteVisitor.WebViewMethodVisitor.visitMethodInsn`, add a branch inside the existing `if (owner.equals("android/webkit/WebView"))` block, after the `postUrl` branch:

```java
                } else if (name.equals("setWebViewClient") && descriptor.equals("(Landroid/webkit/WebViewClient;)V")) {
                    // Wrap the client so the agent's onPageFinished runs without dropping the app's
                    instrumentSetWebViewClient(owner, name, descriptor, isInterface);
                    return;
                }
```

Add the method alongside the other `instrument*` methods:

```java
        /**
         * Instruments setWebViewClient(WebViewClient) by replacing the argument with an
         * NRWebViewClient that delegates to it. Unlike the other interceptions in this class,
         * which only observe, this one rewrites the call's argument.
         *
         * Stack before: [webView, client]
         *
         * Injected bytecode:
         * ASTORE 100       // [webView]                    stash client
         * DUP              // [webView, webView]
         * ALOAD 100        // [webView, webView, client]
         * INVOKESTATIC     // [webView, clientToSet]       wrap + record
         * INVOKEVIRTUAL    // []                           call original with the wrapper
         */
        private void instrumentSetWebViewClient(String owner, String name, String descriptor, boolean isInterface) {
            log.debug("[WebViewCallSiteVisitor] Instrumenting setWebViewClient(WebViewClient) call in class: " + className);

            // Stack: [webView, client]
            mv.visitVarInsn(Opcodes.ASTORE, 100);   // Stack: [webView]
            mv.visitInsn(Opcodes.DUP);              // Stack: [webView, webView]
            mv.visitVarInsn(Opcodes.ALOAD, 100);    // Stack: [webView, webView, client]

            mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "com/newrelic/agent/android/webView/WebViewInstrumentationCallbacks",
                    "setWebViewClientCalled",
                    "(Landroid/webkit/WebView;Landroid/webkit/WebViewClient;)Landroid/webkit/WebViewClient;",
                    false
            );
            // Stack: [webView, clientToSet]

            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, name, descriptor, isInterface);

            context.markModified();
        }
```

Local slot 100 is the same convention `instrumentLoadUrlWithHeaders` and `instrumentPostUrl` already use in this file. It is not being changed here.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :instrumentation:test --tests "com.newrelic.agent.compile.visitor.WebView*"`
Expected: PASS — the new test plus the pre-existing `testWebViewInstrumentation` and `WebViewMethodClassVisitorTest`.

- [ ] **Step 5: Commit**

```bash
git add instrumentation/src/main/java/com/newrelic/agent/compile/visitor/WebViewCallSiteVisitor.java \
        instrumentation/src/test/java/com/newrelic/agent/compile/visitor/WebViewCallSiteVisitorTest.java
git commit -m "feat: intercept setWebViewClient call sites to install NR client (NR-461357)"
```

---

## After Implementation

Run both modules' WebView tests together to confirm nothing regressed across the split:

```bash
./gradlew :agent:testReleaseUnitTest --tests "com.newrelic.agent.android.webView.*" \
          :instrumentation:test --tests "com.newrelic.agent.compile.visitor.WebView*"
```

**Functional verification** (`samples/agent-test-app`; there is no WebView screen there yet, so one has to be added):

1. A page defining `window.newrelic` → `Supportability/Mobile/Android/WebView/BrowserAgentDetected` is recorded.
2. A page not defining it → that metric is absent, while `…/WebView/PageFinished` is present.
3. A page that loads its Browser agent snippet via `<script async>` → still detected, proving the retry poll works.
4. **Callback preservation:** set a custom `WebViewClient` whose `shouldOverrideUrlLoading` intercepts a known link, then confirm the interception still fires with the agent installed. This is the regression this design exists to prevent.
5. **Dedup:** with a custom `WebViewClient` that directly extends `WebViewClient` (so it is bytecode-instrumented too), confirm `PageFinished` increments **once** per page load, not twice.
6. On an API 24/25 emulator with a custom client, confirm the client still works and that the debug log shows the "Skipping NR WebViewClient install below API 26" line.

**Follow-up ticket to file** (out of scope here, documented in spec section 8): `WebViewCallSiteVisitor` gates on `owner.equals("android/webkit/WebView")`, so `MyWebView wv; wv.setWebViewClient(…)` — a receiver typed as a WebView subclass that does not override the method — is not intercepted. Pre-existing gate, already applies to `loadUrl`/`postUrl`.
