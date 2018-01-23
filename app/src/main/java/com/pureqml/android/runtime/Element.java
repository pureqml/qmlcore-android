package com.pureqml.android.runtime;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.Log;

import com.eclipsesource.v8.Releasable;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.pureqml.android.IExecutionEnvironment;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class Element {
    public static final String TAG = "rt.Element";

    public static final class AlreadyHasAParentException extends Exception {
        AlreadyHasAParentException() { super("AlreadyHasAParentException"); }
    };

    IExecutionEnvironment _env;
    protected Element           _parent;
    protected Rect              _rect;
    protected int               _z;
    protected Rect              _nextRect;
    protected Rect              _lastRect;
    protected float             _opacity;
    protected boolean           _visible;
    protected List<Element>     _children;
    protected boolean           _updated;
    private Map<String, List<V8Function>> _callbacks;

    public Element(IExecutionEnvironment env) {
        _env = env;
        _rect = new Rect();
        _opacity = 1;
        _visible = true;
    }

    public void append(Element el) throws AlreadyHasAParentException {
        if (el == null)
            throw new NullPointerException("appending null element");

        if (el._parent != null)
            throw new AlreadyHasAParentException();
        el._parent = this;
        if (_children == null)
            _children = new LinkedList<Element>();
        _children.add(el);
        el.update();
    }

    public void remove() {
        if (_parent != null)
            _parent.removeChild(this);
        _parent = null;
    }

    public void discard() {
        remove();
        //_env.removeElement(this.hashCode()); //fixme: find out why it's not working
    }

    public void on(String name, V8Function callback) {
        Log.i(TAG, "on " + name);
        if (_callbacks == null)
            _callbacks = new HashMap<>();
        List<V8Function> callbacks = _callbacks.get(name);
        if (callbacks == null) {
            callbacks = new LinkedList<V8Function>();
            _callbacks.put(name, callbacks);
        }
        callbacks.add(callback);
    }

    public void emit(V8Object target, String name, Object ... args) {
        Log.i(TAG, "emitting " + name);
        if (_callbacks == null)
            return;
        List<V8Function> callbacks = _callbacks.get(name);
        if (callbacks == null)
            return;
        V8Array v8args = new V8Array(_env.getRuntime());
        for (int i = 0; i < args.length; ++i) {
            v8args.push(args[i]);
        }
        for(V8Function callback : callbacks) {
            try {
                callback.call(target, v8args);
            } catch (Exception e) {
                Log.e(TAG, "callback for " + name + " failed", e);
            }
        }
        v8args.release();
    }

    void update() {
        Element current = this;
        while(current != null && !current._updated) {
            current._updated = true;
            current = current._parent;
        }
    }

    protected void removeChild(Element child) {
        if (_children != null)
            _children.remove(child);
        if (child._lastRect != null)
            _lastRect.union(child._lastRect);
    }

    protected void setStyle(String name, Object value) {
        switch(name) {
            case "left":    { int left = TypeConverter.toInteger(value);    _rect.right += left - _rect.left; _rect.left = left; } break;
            case "top":     { int top = TypeConverter.toInteger(value);     _rect.bottom += top - _rect.top; _rect.top = top; } break;
            case "width":   { int width = TypeConverter.toInteger(value);   _rect.right = _rect.left + width; } break;
            case "height":  { int height = TypeConverter.toInteger(value);  _rect.bottom = _rect.top + height; } break;
            case "opacity":     _opacity = TypeConverter.toFloat(value); break;
            case "z-index":     _z = TypeConverter.toInteger(value); break;
            case "visibility":  _visible = value.equals("visible"); break;
            default:
                Log.v(TAG, "setStyle " + name + ": " + value);
                return;
        }
        update();
    }

    public void style(V8Array arguments) throws Exception {
        Object arg0 = arguments.get(0);
        if (arg0 instanceof V8Object) {
            V8Object styles = (V8Object) arg0;
            for (String key : styles.getKeys())
                setStyle(key, styles.getString(key));
        } else if (arguments.length() == 2) {
            setStyle(arguments.getString(0), Wrapper.getValue(_env, null, arguments.get(1)));
        }
        else
            throw new Exception("invalid setStyle invocation");//fixme: leak of resources here
        if (arg0 instanceof Releasable)
            ((Releasable)arg0).release();
    }

    protected Rect getEffectiveRect()   { return null; }
    public Rect getCombinedDirtyRect()  { return _nextRect; }

    public void updateCurrentGeometry(int baseX, int baseY) {
        if (!_updated)
            return;

        _nextRect = new Rect();
        Rect rect = getEffectiveRect();
        if (rect != null) {
            _nextRect.union(translateRect(rect, baseX, baseY));
        }
        if (_lastRect != null)
            _nextRect.union(_lastRect);

        baseX += _rect.left;
        baseY += _rect.top;
        if (_children != null) {
            for (Element child : _children) {
                child.updateCurrentGeometry(baseX, baseY);
                _nextRect.union(translateRect(child._nextRect, baseX, baseY));
            }
        }
        _updated = false;
    }

    public void paint(Canvas canvas, int baseX, int baseY) {
        if (_children != null) {
            for (Element child : _children) {
                child.paint(canvas, _rect.left + baseX, _rect.top + baseY);
            }
        }
    }

    static final Rect translateRect(Rect rect, int dx, int dy) {
        Rect r = new Rect(rect);
        r.offset(dx, dy);
        return r;
    }
}
