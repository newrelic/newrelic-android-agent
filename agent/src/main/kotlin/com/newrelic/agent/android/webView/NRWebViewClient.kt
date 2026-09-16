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
