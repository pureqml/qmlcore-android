package com.pureqml.android.runtime;

import android.util.Log;

import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Value;

public class Element {
    public static final class AlreadyHasAParentException extends Exception {
        AlreadyHasAParentException() { super("AlreadyHasAParentException"); }
    };

    public static final String TAG = "rt.Element";
    protected Element _parent;

    public Element() {
        _parent = null;
    }

    private void setParent(Element parent) {
        _parent = parent;
    }

    public void append(Element el) throws AlreadyHasAParentException {
        if (el._parent != null)
            throw new AlreadyHasAParentException();
        Log.i(TAG, "append element!");
    }

    public void remove() {
        if (_parent != null)
            _parent.removeChild(this);
        _parent = null;
    }

    protected void removeChild(Element child) {
        Log.i(TAG, "remove child");
    }

    public void style(V8Array arguments) {
        Log.i(TAG, "style");
    }

    public void on(String name, V8Value callback) {
        Log.i(TAG, "on " + name);
    }
}
