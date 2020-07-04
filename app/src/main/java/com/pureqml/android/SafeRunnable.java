package com.pureqml.android;
import android.util.Log;

public abstract class SafeRunnable implements Runnable {
    private static final String TAG = "SafeRunnable";

    protected abstract void doRun();

    protected void handleException(Throwable t) {
        Log.e(TAG, "exception in SafeRunnable", t);
    }

    @Override
    public void run() {
        try { this.doRun(); }
        catch(Throwable t) { handleException(t); }
    }
}
