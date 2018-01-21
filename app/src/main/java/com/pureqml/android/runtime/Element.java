package com.pureqml.android.runtime;

import android.graphics.Rect;
import android.util.Log;

import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Value;

import java.util.LinkedList;
import java.util.List;

public class Element {
    public static final class AlreadyHasAParentException extends Exception {
        AlreadyHasAParentException() { super("AlreadyHasAParentException"); }
    };

    public static final String TAG = "rt.Element";
    protected Element           _parent;
    protected Rect              _rect;
    protected Rect              _dirty;
    protected List<Element>     _children;

    public Element() {
        _parent = null;
        _rect = new Rect();
        _dirty = new Rect();
    }

    public void append(Element el) throws AlreadyHasAParentException {
        if (el._parent != null)
            throw new AlreadyHasAParentException();
        el._parent = this;
        if (_children == null)
            _children = new LinkedList<Element>();
        _children.add(el);
    }

    public void remove() {
        if (_parent != null)
            _parent.removeChild(this);
        _parent = null;
    }

    protected void removeChild(Element child) {
        Log.i(TAG, "remove child");
        _dirty.union(child._dirty);
    }

    public void style(V8Array arguments) {
        Log.i(TAG, "style " + arguments.toString());
    }

    public void on(String name, V8Value callback) {
        Log.i(TAG, "on " + name);
    }
}
