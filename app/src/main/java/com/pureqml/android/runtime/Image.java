package com.pureqml.android.runtime;

import android.util.Log;

import com.eclipsesource.v8.V8Function;

public class Image extends Element {
    private final static String TAG = "rt.Image";

    public Image(IExecutionEnvironment env) {
        super(env);
    }

    public void load(String name, V8Function callback) {
        Log.v(TAG, "loading " + name + " " + callback);
        callback.release();
    }

    protected void setStyle(String name, Object value) {
        Log.v(TAG, "setStyle " + name + ": " + value);
    }
}
