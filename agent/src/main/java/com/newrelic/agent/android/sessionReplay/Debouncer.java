package com.newrelic.agent.android.sessionReplay;

import android.os.Handler;
import android.os.Looper;

public class Debouncer {
    private final Handler handler;
    private final long delayMs;
    private Runnable pendingRunnable;

    public Debouncer(long delayMs) {
        this.handler = new Handler(Looper.getMainLooper());
        this.delayMs = delayMs;
    }

    public void debounce(Runnable runnable) {
        // Cancel any pending execution
        if (pendingRunnable != null) {
            handler.removeCallbacks(pendingRunnable);
        }

        // Schedule new execution
        pendingRunnable = runnable;
        handler.postDelayed(pendingRunnable, delayMs);
    }

    public void cancel() {
        if (pendingRunnable != null) {
            handler.removeCallbacks(pendingRunnable);
            pendingRunnable = null;
        }
    }
}