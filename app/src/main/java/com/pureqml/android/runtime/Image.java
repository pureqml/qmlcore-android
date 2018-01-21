package com.pureqml.android.runtime;

import android.util.Log;

public class Image extends Element {
    private final static String TAG = "rt.Image";
    protected void style(String name, Object value) {
        Log.v(TAG, "style " + name + ": " + value);
    }
}
