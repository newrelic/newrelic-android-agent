package com.newrelic.agent.android.sessionReplay.internal;

import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A utility that holds the list of root views that WindowManager updates.
 */
public class RootViewsSpy {

    private final CopyOnWriteArrayList<OnRootViewsChangedListener> listeners = new CopyOnWriteArrayList<>();

    private final List<View> delegatingViewList = new ArrayList<View>() {
        @Override
        public boolean add(View element) {
            for (OnRootViewsChangedListener listener : listeners) {
                listener.onRootViewsChanged(element, true);
            }
            return super.add(element);
        }

        @Override
        public View remove(int index) {
            View removedView = super.remove(index);
            for (OnRootViewsChangedListener listener : listeners) {
                listener.onRootViewsChanged(removedView, false);
            }
            return removedView;
        }
    };

    private RootViewsSpy() {
    }

    public List<OnRootViewsChangedListener> getListeners() {
        return listeners;
    }

    public List<View> copyRootViewList() {
        return new ArrayList<>(delegatingViewList);
    }

    public static RootViewsSpy install() {
        RootViewsSpy rootViewsSpy = new RootViewsSpy();
        WindowManagerSpy.swapWindowManagerGlobalMViews(mViews -> {
            rootViewsSpy.delegatingViewList.addAll(mViews);
            return (ArrayList<View>) rootViewsSpy.delegatingViewList;
        });
        return rootViewsSpy;
    }
}