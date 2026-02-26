package com.newrelic.agent.android.webView;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.RadioGroup;

import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.sessionReplay.SessionReplay;
import com.newrelic.agent.android.stats.StatsEngine;

public class WebViewInstrumentationCallbacks {

    private static RRWebRecorder rrwebRecorder;

    public static void ButtonClicked(View view) {
        try {
            // Do something
            String viewName = view.getResources().getResourceName(view.getId());
            Log.d("ViewCapture ButtonClicked", viewName);
        } catch (Exception e) {
            // Do something
        }

    }

    public static void ButtonLongClicked(View view) {
        try {
            // Do something
            String viewName = view.getResources().getResourceName(view.getId());
            Log.d("ViewCapture ButtonLongClicked", viewName);
        } catch (Exception e) {
            // Do something
        }
    }

    public static void OnListItemClicked(AdapterView adapterView,View view) {
        try {
            // Do something
            String viewName = view.getResources().getResourceName(view.getId());
            Log.d("ViewCapture ListItemClicked",  viewName);
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
                Log.d("ViewCapture", "Dialog Button Clicked: " + dialogTitle);
            }

            Log.d("ViewCapture",  "Dialog Button Clicked");
        } catch (Exception e) {
            // Do something
        }

    }

    public static void OnCheckedChange(CompoundButton buttonView, boolean isChecked) {
        try {
            // Do something
            Log.d("ViewCapture", buttonView.getText().toString() + "Checked: " + isChecked);
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
                        Log.d("ViewCapture", compoundButton.getText().toString() + "Checked: " + checkedId);
                    }
                }
            }
            // Do something
        } catch (Exception e) {
            // Do something
        }

    }

    public static void loadUrlCalled(WebView var0) {
        var0.addJavascriptInterface(new RRWebJavaScriptInterface(null), "RRWebAndroid");


           var0.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {

                super.onPageStarted(view, url, favicon);

            }

            @Override
            public void onPageFinished(WebView view, String url) {
                try {
                    if (rrwebRecorder == null) {
                        rrwebRecorder = new RRWebRecorder(view);
                    }
                    rrwebRecorder.startRecording();
                    Log.d("WebViewInstrumentation", "RRWeb recording enabled");
                } catch (Exception e) {
                    Log.e("WebViewInstrumentation", "Failed to enable RRWeb recording", e);
                }
                super.onPageFinished(view, url);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
            }


        });

        var0.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                Log.d("WebViewLifecycle", "WebView attached to window");
                // WebView is now visible and active

            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                Log.d("WebViewLifecycle", "WebView detached from window - cleaning up");
                // WebView is being removed/destroyed
                SessionReplay.getInstance().setTakeFullSnapshot(true);
            }
        });


        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_LOAD_URL);
    }

    public static void postUrlCalled(WebView var0) {

        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_POST_URL);
    }

    public static void onPageFinishedCalled(WebViewClient var0, WebView var1, String var2) {
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_PAGE_FINISHED);
    }
}
