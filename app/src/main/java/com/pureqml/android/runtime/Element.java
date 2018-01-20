package com.pureqml.android.runtime;

import android.util.Log;

import com.eclipsesource.v8.JavaCallback;
import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Object;
import com.eclipsesource.v8.V8Value;

public class Element {
    public static final String TAG = "rt.Element";
    protected Element _parent;

    public Element() {
        _parent = null;
    }

    private void setParent(Element parent) {
    }

    public void append(Element el) {
        Log.i(TAG, "append element!");
    }

    public void remove(Element el) {
        Log.i(TAG, "append element!");
    }

    public void style(V8Array arguments) {
        Log.i(TAG, "style");
    }

    public void on(String name, V8Value callback) {
        Log.i(TAG, "on " + name);
    }
}
