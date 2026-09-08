package com.newrelic.agent.android.webView;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.RadioGroup;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.sessionReplay.SessionReplay;
import com.newrelic.agent.android.sessionReplay.SessionReplayMode;
import com.newrelic.agent.android.stats.StatsEngine;

import java.util.Map;
import java.util.WeakHashMap;

public class WebViewInstrumentationCallbacks {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    private static final Map<WebView, WebViewState> states = new WeakHashMap<>();

    /**
     * Per-WebView instrumentation state. Held in a WeakHashMap so tracking a WebView never
     * keeps it (and its Activity) alive.
     */
    static final class WebViewState {
        boolean jsInterfaceInjected;
        NRWebViewClient nrClient;
    }

    private static synchronized WebViewState stateFor(WebView webView) {
        WebViewState state = states.get(webView);
        if (state == null) {
            state = new WebViewState();
            states.put(webView, state);
        }
        return state;
    }

    // Polls for window.newrelic instead of checking once, since a Browser agent snippet loaded
    // via <script async>/defer, or injected dynamically, may not exist yet when onPageFinished fires.
    private static final int DETECTION_POLL_INTERVAL_MS = 250;
    private static final int DETECTION_MAX_POLL_ATTEMPTS = 8;

    /**
     * License key the POC's injected observation-mode agent advertises. Deliberately recognizable:
     * it is never used for egress (observation mode sends nothing), and it lets
     * {@link #DETECTION_SCRIPT} tell a page-owned agent from the one this POC injected.
     */
    private static final String OBSERVATION_LICENSE_KEY = "NRWV_OBSERVATION_MODE";

    private static final String DETECTION_SCRIPT =
            "(function() {" +
            "if (!window." + WebViewJSInterface.INTERFACE_NAME + ") { return; }" +
            "var attempts = 0;" +
            "var maxAttempts = " + DETECTION_MAX_POLL_ATTEMPTS + ";" +
            "var intervalMs = " + DETECTION_POLL_INTERVAL_MS + ";" +
            "var check = function() {" +
            "if (typeof window.newrelic !== 'undefined') {" +
            // Report only a PRE-EXISTING agent. Detection polls asynchronously (8 x 250ms), so on a
            // page where the POC injected its own observation-mode agent, a naive check would see
            // ours and fire a false positive on this shipped supportability metric.
            //
            // Two discriminators, because the sentinel alone is not enough. A page whose own snippet
            // is async/deferred may not have run when onPageFinished fired -- the very case this
            // polling exists for -- so the POC's collision check misses it and injects, setting the
            // sentinel. Their snippet then runs and overwrites NREUM.info with its REAL license key.
            // A license key that is not our sentinel is therefore positive proof of a page-owned
            // agent even when our sentinel is set, which keeps that true positive from being lost.
            "var nrOurs=!!window.__nrWvInjected;" +
            "var nrForeign=!!(window.NREUM&&window.NREUM.info&&" +
            "window.NREUM.info.licenseKey!=='" + OBSERVATION_LICENSE_KEY + "');" +
            "window." + WebViewJSInterface.INTERFACE_NAME + ".reportBrowserAgentDetected(!nrOurs||nrForeign);" +
            "return;" +
            "}" +
            "attempts++;" +
            "if (attempts >= maxAttempts) {" +
            "window." + WebViewJSInterface.INTERFACE_NAME + ".reportBrowserAgentDetected(false);" +
            "return;" +
            "}" +
            "setTimeout(check, intervalMs);" +
            "};" +
            "check();" +
            "})();";

    /**
     * Records whether the page already had a New Relic Browser agent, sampled at onPageStarted —
     * before the POC could have injected one. The collision check at onPageFinished reads this,
     * because by then our own agent may be present and indistinguishable from the page's.
     */
    private static final String PROBE_SCRIPT =
            "(function(){try{" +
            "if(window.__nrWvProbe===undefined){" +
            "window.__nrWvProbe={hadNREUM:!!window.NREUM,hadNewrelic:!!window.newrelic};" +
            "}}catch(e){}})();";

    /** Experimental browser agent build — the only one exposing observation_mode and beforeHarvest. */
    private static final String LOADER_URL =
            "https://js-agent.newrelic.com/experiments/dev/before-send-hook/nr-loader-spa.min.js";

    private static final int HOOK_POLL_INTERVAL_MS = 50;
    private static final int HOOK_MAX_POLL_ATTEMPTS = 100;

    /**
     * Injects the browser agent in observation mode and registers a beforeHarvest hook that
     * forwards session_replay payloads to the native bridge. Sentinel-guarded, so repeat
     * onPageFinished fires and SPA re-renders are no-ops.
     *
     * Observation mode means the agent builds every payload it would send but sends nothing.
     * Most other features are disabled. page_view_event is deliberately LEFT ENABLED: its
     * postHarvestCleanup is where activateWithSyntheticRumResponse lives, which is the only
     * source of the srs/sr flags replay blocks on via waitForFlags -- disabling it would make
     * replay structurally incapable of harvesting. Sampling is pinned at 100 for determinism;
     * the synthetic RUM response is what actually selects the mode (it lands in FULL).
     */
    private static final String INJECTION_SCRIPT =
            "(function(){" +
            "var B=window." + WebViewJSInterface.INTERFACE_NAME + ";" +
            "if(!B){return;}" +
            "try{" +
            // 1. Sentinel: never inject twice into one document.
            "if(window.__nrWvInjected){return;}" +
            // 2. Collision: the page's own agent wins. Probe first, live checks as fallback.
            "var p=window.__nrWvProbe;" +
            "if((p&&(p.hadNREUM||p.hadNewrelic))||window.NREUM||window.newrelic){" +
            "B.reportInjectionSkipped('existing-agent');return;}" +
            // 3. Claim the document before doing any work.
            "window.__nrWvInjected=true;" +
            // 4. Configuration.
            "window.NREUM=window.NREUM||{};" +
            // beacon/sa and loader_config are populated by the standard snippet. Omitting them
            // risks the agent rejecting an incomplete config or the SPA loader not considering
            // itself configured -- and that failure looks identical to "wrong build" (R3) at the
            // 5s poll timeout. They are free placeholders here since observation mode sends nothing.
            "window.NREUM.info={beacon:'bam.nr-data.net',errorBeacon:'bam.nr-data.net'," +
            "licenseKey:'" + OBSERVATION_LICENSE_KEY + "',applicationID:'0',sa:1};" +
            "window.NREUM.loader_config={licenseKey:'" + OBSERVATION_LICENSE_KEY + "'," +
            "applicationID:'0',agentID:'0',trustKey:'0'};" +
            "window.NREUM.init={" +
            "observation_mode:{enabled:true}," +
            // Default harvest interval is 30s, and the timer only starts at synthetic RUM
            // activation -- so the first replay harvest is ~30s out. An operator who taps for
            // 15s and navigates away would see injection succeed then silence, and conclude
            // replay never harvested: the exact wrong answer to the risk phase 1 exists to settle.
            "harvest:{interval:5}," +
            // session_trace is REQUIRED, not optional. Device run showed the hook registering and
            // page_view_event harvesting, but session_replay never harvesting -- with session_trace
            // disabled. Replay is coupled to trace through session identity, so turning it off
            // prevented replay from recording at all.
            "session_trace:{enabled:true}," +
            // Every other feature is left at its default. Disabling them was meant to cut noise,
            // but two turned out to be load-bearing (page_view_event supplies the srs/sr flags
            // replay waits on; session_trace as above), and a config that suppresses the signal is
            // worth nothing. The hook filters to session_replay, so extra features cost log noise
            // rather than wrong data. Re-disable individually only after replay is confirmed working.
            "session_replay:{enabled:true,sampling_rate:100,error_sampling_rate:100}};" +
            // 5. Hook registration, polling until the agent exposes beforeHarvest.
            "var hookSeen=false;" +
            "var register=function(n){" +
            "try{" +
            "if(window.newrelic&&typeof window.newrelic.beforeHarvest==='function'){" +
            "window.newrelic.beforeHarvest(function(h){" +
            "try{" +
            // One-shot proof-of-life, reported BEFORE the feature filter. Without it, silence after
            // successful registration is ambiguous between "replay never harvested" (R1, the risk
            // that can silently sink this POC) and "the wrapper shape is not {feature,payload}"
            // (R3). This collapses the two to a single logcat line on the first harvest of any
            // feature, and reveals the actual wrapper shape.
            "if(!hookSeen){hookSeen=true;" +
            "try{B.reportHarvestObserved('first harvest: typeof='+(typeof h)" +
            "+' keys='+((h&&typeof h==='object'&&!(typeof ArrayBuffer!=='undefined'"
            +"&&ArrayBuffer.isView&&ArrayBuffer.isView(h)))"
            +"?Object.keys(h).slice(0,16).join('|'):'n/a')" +
            "+' feature='+(h&&h.feature));}catch(e){}}" +
            // Never return null. Per the beforeHarvest contract, null CANCELS the harvest while
            // undefined means "send the original, unmodified". `h&&h.payload` evaluates to null
            // when h is null, which would silently drop a harvest -- a direct violation of the
            // spec's rule that a bug in our code must never alter what the agent does.
            "if(!h||h.feature!=='session_replay'){return (h&&h.payload!=null)?h.payload:undefined;}" +
            // Binary-safe envelope. JSON.stringify does not fail on a typed array — it silently
            // expands it to {"0":31,"1":139,...}, one key per byte. Measured: a payload whose body
            // is a 200KB Uint8Array produces a 2,551,465-char envelope, and a payload that IS a
            // 200KB Uint8Array produces 4,283,525 chars with 204,800 entries in `keys`. That would
            // blow the bridge and the logcat ring buffer, and report a size 12x the real one — in
            // exactly the compressed-payload case this POC exists to detect. So describe binary
            // values by byte length instead of expanding them, and cap `keys`.
            "var pl=h.payload;" +
            "var nBytes=function(v){" +
            "try{" +
            "if(!v||typeof v!=='object'){return -1;}" +
            "if(typeof Blob!=='undefined'&&v instanceof Blob){return v.size;}" +
            "if(typeof ArrayBuffer!=='undefined'){" +
            "if(v instanceof ArrayBuffer){return v.byteLength;}" +
            "if(ArrayBuffer.isView&&ArrayBuffer.isView(v)){return v.byteLength;}}" +
            "return -1;}catch(e){return -1;}};" +
            "var descOf=function(v){" +
            "return '[binary '+Object.prototype.toString.call(v)+' bytes='+nBytes(v)+']';};" +
            "var plBytes=nBytes(pl);" +
            "var bodyBytes=(plBytes<0&&pl&&typeof pl==='object')?nBytes(pl.body):-1;" +
            // Any binary value at the top level, not just `body`. Keying only on `body` moved the
            // blowup one key over: {qs, body:Uint8Array(16), extra:Uint8Array(200KB)} measured a
            // 2,551,555-char envelope. Since the payload shape is precisely what is unknown here,
            // "only body is ever binary" is not an assumption available to us.
            "var anyBin=false;" +
            "if(plBytes<0&&pl&&typeof pl==='object'){" +
            "var ak=Object.keys(pl);" +
            "for(var ai=0;ai<ak.length&&ai<64;ai++){" +
            "if(nBytes(pl[ak[ai]])>=0){anyBin=true;break;}}}" +
            "var ser;" +
            "try{" +
            // Payload itself is binary: describe it, never expand it.
            "if(plBytes>=0){ser=descOf(pl);}" +
            // Some field is binary: keep every non-binary sibling (qs and friends are diagnostic)
            // and swap each binary one for its descriptor.
            "else if(anyBin){" +
            "var sh={};var bk=Object.keys(pl);" +
            "for(var bi=0;bi<bk.length&&bi<64;bi++){" +
            "var bv=pl[bk[bi]];var nb=nBytes(bv);" +
            "sh[bk[bi]]=(nb>=0)?descOf(bv):bv;}" +
            // Mark the cap rather than truncating silently, mirroring how `keys` reports its own.
            "if(bk.length>64){sh.__nrTruncated=(bk.length-64)+' more keys';}" +
            "ser=JSON.stringify(sh);}" +
            // Plain JSON payload: verbatim, byte-identical to the unpatched behavior.
            "else{ser=JSON.stringify(pl);}" +
            "}catch(e){ser='[unserializable: '+(e&&e.message)+']';}" +
            "var kz=null;" +
            "if(plBytes<0&&pl&&typeof pl==='object'){" +
            "var ka=Object.keys(pl);" +
            "kz=(ka.length>32)?ka.slice(0,32).concat(['...+'+(ka.length-32)+' more']):ka;}" +
            "B.reportSessionReplayPayload(JSON.stringify({" +
            "url:location.href," +
            "feature:h.feature," +
            "shape:Object.prototype.toString.call(pl)," +
            "keys:kz," +
            "bodyShape:pl?Object.prototype.toString.call(pl.body):null," +
            "payloadBytes:plBytes," +
            "bodyBytes:bodyBytes," +
            "serialized:ser}));" +
            "}catch(e){}" +
            "return (h&&h.payload!=null)?h.payload:undefined;" +   // never null; see above
            "});" +
            "return;}" +
            "if(n>=" + HOOK_MAX_POLL_ATTEMPTS + "){" +
            "B.reportInjectionSkipped('beforeHarvest-unavailable (typeof newrelic='"
            +"+(typeof window.newrelic)+')');return;}" +
            "setTimeout(function(){register(n+1);}," + HOOK_POLL_INTERVAL_MS + ");" +
            "}catch(e){}" +
            "};" +
            // 6. Loader tag. onerror is the CSP / network failure signal.
            "var s=document.createElement('script');" +
            "s.src='" + LOADER_URL + "';" +
            "s.type='text/javascript';" +
            "s.onerror=function(){try{B.reportInjectionSkipped('loader-load-failed');}catch(e){}};" +
            "(document.head||document.documentElement).appendChild(s);" +
            // 7. Start polling immediately: a loader that never fires onload still gets a hook attempt.
            "register(0);" +
            "}catch(e){}" +
            "})();";

    public static void ButtonClicked(View view) {
        try {
            // Do something
            String viewName = view.getResources().getResourceName(view.getId());
            log.debug("ViewCapture ButtonClicked: " + viewName);
        } catch (Exception e) {
            // Do something
        }

    }

    public static void ButtonLongClicked(View view) {
        try {
            // Do something
            String viewName = view.getResources().getResourceName(view.getId());
            log.debug("ViewCapture ButtonLongClicked: " + viewName);
        } catch (Exception e) {
            // Do something
        }
    }

    public static void OnListItemClicked(AdapterView adapterView,View view) {
        try {
            // Do something
            String viewName = view.getResources().getResourceName(view.getId());
            log.debug("ViewCapture ListItemClicked: " + viewName);
        } catch (Exception e) {
            // Do something
        }

    }

    public static void OnDialogButtonClicked(DialogInterface dialog, int which) {
        try {
            // Do something

            if(dialog instanceof AlertDialog) {
                AlertDialog alertDialog = (AlertDialog) dialog;
                String dialogTitle = alertDialog.getButton(which).getText().toString();
                log.debug("ViewCapture: Dialog Button Clicked: " + dialogTitle);
            }

            log.debug("ViewCapture: Dialog Button Clicked");
        } catch (Exception e) {
            // Do something
        }

    }

    public static void OnCheckedChange(CompoundButton buttonView, boolean isChecked) {
        try {
            // Do something
            log.debug("ViewCapture: " + buttonView.getText().toString() + " Checked: " + isChecked);
        } catch (Exception e) {
            // Do something
        }

    }

    public static void OnCheckedChange(RadioGroup radioGroup, int checkedId) {
        try {

            for (int i = 0; i < radioGroup.getChildCount(); i++) {
                View view = radioGroup.getChildAt(i);
                if (view instanceof CompoundButton) {
                    CompoundButton compoundButton = (CompoundButton) view;
                    if (compoundButton.getId() == checkedId) {
                        log.debug("ViewCapture: " + compoundButton.getText().toString() + " Checked: " + checkedId);
                    }
                }
            }
            // Do something
        } catch (Exception e) {
            // Do something
        }

    }

    static synchronized void ensureJsInterfaceInjected(WebView webView) {
        if (webView == null) {
            return;
        }
        WebViewState state = stateFor(webView);
        if (state.jsInterfaceInjected) {
            return;
        }
        try {
            webView.addJavascriptInterface(new WebViewJSInterface(), WebViewJSInterface.INTERFACE_NAME);
            state.jsInterfaceInjected = true;
        } catch (Exception e) {
            // Flag deliberately left unset so the next navigation retries. Marking it before the
            // call would let one transient failure disable detection for this WebView permanently.
            log.error("Failed to inject NR WebView JS interface", e);
        }
    }

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
            webView.setWebViewClient(wrapper);
            // Recorded only after the install actually succeeded. Recording first would make
            // hasNRClient() claim a wrapper that isn't there, suppressing the fallback path and
            // losing the page load from both paths at once.
            state.nrClient = wrapper;
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

    public static void loadUrlCalled(WebView var0) {
        prepare(var0);
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_LOAD_URL);
    }

    public static void postUrlCalled(WebView var0) {
        prepare(var0);
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_POST_URL);
    }

    /**
     * Whether WebView replay capture should run for this page.
     *
     * Deliberately avoids {@link SessionReplay#isReplayRecording()}: that method's null guard uses
     * {@code &&} where it needs {@code ||}, so it dereferences a null modeManager and throws NPE in
     * exactly the "session replay never initialized" case it was written to handle. Reading the mode
     * is null-safe, and SessionReplayModeManager.isRecording() is defined as {@code mode != OFF},
     * so this is semantically identical.
     */
    private static boolean shouldCaptureWebViewReplay() {
        try {
            SessionReplayMode mode = SessionReplay.getCurrentMode();
            return mode != null && mode != SessionReplayMode.OFF;
        } catch (Throwable t) {
            log.debug("Could not read the session replay mode; skipping WebView replay capture");
            return false;
        }
    }

    /**
     * Pre-injection probe, run from {@link NRWebViewClient#onPageStarted}. Deliberately does not
     * inject the browser agent: at onPageStarted the page's own head scripts have not run, so a
     * collision check here would pass almost unconditionally and we would inject on top of a page
     * that carries its own agent.
     */
    public static void pageStarted(WebView webView, String url) {
        if (webView == null) {
            return;
        }
        if (!shouldCaptureWebViewReplay()) {
            return;
        }
        try {
            webView.evaluateJavascript(PROBE_SCRIPT, null);
            log.debug("NR WebView replay probe evaluated for " + url);
        } catch (Exception e) {
            log.error("Failed to run the NR WebView replay probe script", e);
        }
    }

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
        if (!shouldCaptureWebViewReplay()) {
            return;
        }
        try {
            webView.evaluateJavascript(INJECTION_SCRIPT, null);
            log.debug("NR browser agent injection script evaluated for " + url);
        } catch (Exception e) {
            log.error("Failed to run the NR browser agent injection script", e);
        }
    }

    public static void onPageFinishedCalled(WebViewClient var0, WebView var1, String var2) {
        if (hasNRClient(var1)) {
            return;     // NRWebViewClient.onPageFinished already reported this page load
        }
        pageFinished(var1, var2);
    }

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

    /**
     * Whether an {@link NRWebViewClient} is currently this WebView's client — meaning
     * {@link NRWebViewClient#onPageFinished} already reported the page load.
     *
     * Prefers the live client over tracked state. The host app can replace our wrapper through a
     * call site we did not intercept (see the residual gap in the design spec), and trusting stale
     * bookkeeping there would suppress the fallback path and lose the page load entirely.
     */
    @TargetApi(Build.VERSION_CODES.O)
    private static synchronized boolean hasNRClient(WebView webView) {
        if (webView == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                return webView.getWebViewClient() instanceof NRWebViewClient;
            } catch (Throwable t) {
                log.debug("Could not read the current WebViewClient; falling back to tracked state");
            }
        }
        WebViewState state = states.get(webView);   // deliberately not stateFor(): no entry created
        return state != null && state.nrClient != null;
    }
}
