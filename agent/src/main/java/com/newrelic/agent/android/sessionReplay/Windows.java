package com.newrelic.agent.android.sessionReplay;

import android.content.res.Resources;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import curtains.internal.WindowSpy;


public class Windows {

    enum WindowType {
        PHONE_WINDOW,
        POPUP_WINDOW,
        TOOLTIP,
        TOAST,
        UNKNOWN
    }

    public static Window getPhoneWindowForView(View view) {
        return WindowSpy.INSTANCE.pullWindow(view);
    }

    public static Windows.WindowType getWindowType(View view) {
        View rootView = view.getRootView();

        if(rootView.getClass().getName().equals("androidx.compose.ui.window.PopupLayout")) {
            return WindowType.POPUP_WINDOW;
        }

        if(WindowSpy.INSTANCE.attachedToPhoneWindow(rootView)) {
            return WindowType.PHONE_WINDOW;
        }

        WindowManager.LayoutParams windowLayoutParams = (WindowManager.LayoutParams) rootView.getLayoutParams();
        if(windowLayoutParams == null) {
            return WindowType.UNKNOWN;
        } else {
            CharSequence title = windowLayoutParams.getTitle();
            if(title.equals("Toast")) {
                return WindowType.TOAST;
            } else if (title.equals(getTooltipString())) {
                return WindowType.TOOLTIP;
            } else if (title.equals("TooltipPopup")) {
                return WindowType.TOOLTIP;
            } else if (title.toString().startsWith("PopupWindow")) {
                return WindowType.POPUP_WINDOW;
            } else {
                return WindowType.UNKNOWN;
            }
        }
    }

    private static String getTooltipString() {
        int tooltipStringResource = Resources.getSystem().getIdentifier("tooltip_popup_title", "string", "android");
        try {
            return Resources.getSystem().getString(tooltipStringResource);
        } catch(Exception e) {
            return "Tooltip";
        }
    }
}
