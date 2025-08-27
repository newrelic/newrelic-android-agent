

package com.newrelic.agent.android.sessionReplay;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.TimeUnit;



public class Debouncer {
    // one frame time (approx. 16.6ms), but using a higher value for debouncing
    private static final long MAX_DELAY_THRESHOLD_NS = TimeUnit.MILLISECONDS.toNanos(1000);
    private static final long DEBOUNCE_TIME_IN_MS = 64;
    private final Handler handler;
    private final long maxRecordDelayInNs;// For telemetry

    private long lastTimeRecordWasPerformed = 0L;
    private boolean firstRequest = true;

    public Debouncer( boolean dynamicOptimization) {
        this.handler = new Handler(Looper.getMainLooper());
        this.maxRecordDelayInNs = MAX_DELAY_THRESHOLD_NS;
    }

    public void debounce(Runnable runnable) {
        if (firstRequest) {
            // Initialize the timestamp on the first call to ensure the first
            // task isn't always executed immediately.
            lastTimeRecordWasPerformed = System.nanoTime();
            firstRequest = false;
        }

        // Always cancel any previously scheduled runnable.
        handler.removeCallbacksAndMessages(null);

        long timePassedSinceLastExecution = System.nanoTime() - lastTimeRecordWasPerformed;

        if (timePassedSinceLastExecution >= maxRecordDelayInNs) {
            // If enough time has passed, execute immediately to keep the UI capture fresh.
            executeRunnable(runnable);
        } else {
            // Otherwise, debounce by posting with a delay.
            handler.postDelayed(() -> executeRunnable(runnable), DEBOUNCE_TIME_IN_MS);
        }
    }

    public void cancel() {
        handler.removeCallbacksAndMessages(null);
    }

    private void executeRunnable(Runnable runnable) {
        runnable.run();
        lastTimeRecordWasPerformed = System.nanoTime();
    }
}