package com.pureqml.android.runtime;

import android.graphics.Rect;
import android.util.Log;

import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Object;
import com.eclipsesource.v8.V8Value;

import java.util.LinkedList;
import java.util.List;

public class Element {
    public static final class AlreadyHasAParentException extends Exception {
        AlreadyHasAParentException() { super("AlreadyHasAParentException"); }
    };

    public static final String TAG = "rt.Element";
    IExecutionEnvironment       _env;
    protected Element           _parent;
    protected Rect              _rect;
    protected Rect              _dirty;
    protected List<Element>     _children;

    public Element(IExecutionEnvironment env) {
        _parent = null;
        _rect = new Rect();
        _dirty = new Rect();
    }

    public void append(Element el) throws AlreadyHasAParentException {
        if (el == null) {
            Log.e(TAG, "appending null element!");
            return;
        }
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

    public Rect getRect() { return _rect; }

    protected void removeChild(Element child) {
        if (_children != null)
            _children.remove(child);
        _dirty.union(child._dirty);
    }

    protected void style(String name, Object value) {
        Log.v(TAG, "style " + name + ": " + value);
    }

    public void style(V8Array arguments) throws Exception {
        Object arg0 = arguments.get(0);
        if (arg0 instanceof V8Object) {
            V8Object styles = (V8Object) arg0;
            for (String key : styles.getKeys())
                style(key, styles.getString(key));
        } else if (arguments.length() == 2)
            style(arguments.getString(0), arguments.get(1));
        else
            throw new Exception("invalid style invocation");
    }

    public void on(String name, V8Value callback) {
        Log.i(TAG, "on " + name);
    }
}
