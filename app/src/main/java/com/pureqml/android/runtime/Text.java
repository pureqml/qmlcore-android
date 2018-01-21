package com.pureqml.android.runtime;

import android.util.Log;

public class Text extends Element {
    private final static String TAG = "rt.Text";

    protected void style(String name, Object value) {
        Log.v(TAG, "style " + name + ": " + value);
    }
}
