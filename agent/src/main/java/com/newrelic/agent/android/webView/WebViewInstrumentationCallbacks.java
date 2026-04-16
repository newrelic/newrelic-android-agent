package com.newrelic.agent.android.webView;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.RadioGroup;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;

public class WebViewInstrumentationCallbacks {
    private static final AgentLog log = AgentLogManager.getAgentLog();


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

    public static void loadUrlCalled(WebView var0) {
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_LOAD_URL);
    }

    public static void postUrlCalled(WebView var0) {
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_POST_URL);
    }

    public static void onPageFinishedCalled(WebViewClient var0, WebView var1, String var2) {
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_PAGE_FINISHED);
    }
}
