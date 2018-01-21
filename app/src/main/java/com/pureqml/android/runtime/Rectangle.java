package com.pureqml.android.runtime;

import android.util.Log;

public class Rectangle extends Element {
    private final static String TAG = "rt.Rectangle";

    protected void style(String name, Object value) {
        Log.i(TAG, "style " + name + ": " + value);
    }
}
