package com.pureqml.android.runtime;

import android.util.Log;

import com.eclipsesource.v8.JavaVoidCallback;
import com.eclipsesource.v8.Releasable;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Object;

public final class Console {
    public static final String TAG = "js";

    public static class LogMethod implements JavaVoidCallback {
        @Override
        public void invoke(final V8Object receiver, final V8Array parameters) {
            if (!Log.isLoggable(TAG, Log.INFO))
                return;

            StringBuilder b = new StringBuilder();
            for(int i = 0; i < parameters.length(); ++i) {
                Object value = parameters.get(i);
                if (i > 0)
                    b.append(' ');
                b.append(value != null? value.toString(): "null");
                if (value instanceof Releasable)
                    ((Releasable)value).release();
            }
            Log.i(TAG, b.toString());
        }
    }
}
