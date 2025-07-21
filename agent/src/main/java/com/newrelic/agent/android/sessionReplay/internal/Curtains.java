package com.newrelic.agent.android.sessionReplay.internal;

import android.view.View;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * Lift the curtain on Android Windows!
 *
 * Curtains is the entry point to retrieve the list of root views, and listen to newly added or
 * removed ones. This is done by reaching into android.view.WindowManagerGlobal.
 *
 * android.view.WindowManagerGlobal is an internal Android Framework class that maintains the list
 * of "root views" that your process manages. Each of these root view corresponds to a window from
 * the system perspective, however not all of these root views will have a Window associated with
 * it: the window / root view for toasts has no Window, and neither does any root view directly
 * added by calling android.view.WindowManager.addView.
 *
 * rootViews returns a snapshot of the currently attached root views.
 *
 * All properties defined in this class should be accessed from the main thread.
 *
 * onRootViewsChangedListeners allows apps to have a central place where they can interact with
 * newly added or removed windows. It's exposed as a mutable list to allow apps to reorder or
 * remove listeners added by libraries.
 */
public class Curtains {

    private static final RootViewsSpy rootViewsSpy = RootViewsSpy.install();

    /**
     * @return a copy of the list of root views held by android.view.WindowManagerGlobal.
     */
    public static List<View> getRootViews() {
        return rootViewsSpy.copyRootViewList();
    }

    /**
     * The list of listeners for newly attached or detached root views. It is safe to update this
     * list from within OnRootViewsChangedListener.onRootViewsChanged, if called back on the main
     * thread. New windows are typically added on the main thread, but it can also happen from a
     * different thread by calling Dialog.show() from a background thread (and of course Google
     * Mobile Ads does exactly that).
     *
     * If you only care about the attached state, you can implement the SAM interface
     * OnRootViewAddedListener which extends OnRootViewsChangedListener.
     *
     * If you only care about the detached state, you can implement the SAM interface
     * OnRootViewRemovedListener which extends OnRootViewsChangedListener.
     *
     * Note: The listeners are invoked immediately when android.view.WindowManager.addView and
     * android.view.WindowManager.removeView are called. android.view.WindowManager.addView
     * happens BEFORE View.onAttachedToWindow is invoked, as a view only becomes attached on the
     * next view traversal.
     *
     * No-op below Android API 19, i.e. any listener added here will never be triggered.
     */
    public static List<OnRootViewsChangedListener> getOnRootViewsChangedListeners() {
        return rootViewsSpy.getListeners();
    }
}
