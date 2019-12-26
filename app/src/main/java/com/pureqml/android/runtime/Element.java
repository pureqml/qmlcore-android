package com.pureqml.android.runtime;

import android.graphics.Canvas;
import android.graphics.Paint;
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

    protected Rect              _rect           = new Rect();
    protected Rect              _combinedRect   = new Rect();
    protected Rect              _lastRect       = new Rect();

    protected float             _opacity        = 1;
    protected boolean           _visible        = true;
    protected boolean           _updated        = true;
    protected boolean           _updatedChild   = true;
    protected Element           _parent;
    protected int               _z;
    protected List<Element>     _children;
    private Map<String, List<V8Function>> _callbacks;

    public Element(IExecutionEnvironment env) {
        _env = env;
    }

    public Rect getRect()
    { return _rect; }
    public Rect getCombinedRect()
    { return _combinedRect; }
    public Rect getLastRenderedRect()
    { return _lastRect; }

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
        if (_callbacks != null) {
            for(String name : _callbacks.keySet()) {
                List<V8Function> callbacks = _callbacks.get(name);
                for(V8Function callback : callbacks)
                    callback.close();
            }
            _callbacks = null;
        }
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

    protected boolean hasCallbackFor(String name) {
        return _callbacks != null && _callbacks.get(name) != null;
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
                Object r = callback.call(target, v8args);
                if (r instanceof Releasable)
                    ((Releasable)r).release();
            } catch (Exception e) {
                Log.e(TAG, "callback for " + name + " failed", e);
            }
        }
        _env.schedulePaint();
        v8args.close();
    }

    void update() {
        _updated = true;
        Element current = this._parent;
        while(current != null && !current._updatedChild) {
            current._updatedChild = true;
            current = current._parent;
        }
    }

    protected void removeChild(Element child) {
        if (_children != null)
            _children.remove(child);
        _lastRect.union(child._lastRect);
        update();
    }

    protected void setStyle(String name, Object value) throws Exception {
        switch(name) {
            case "left":    { int left = TypeConverter.toInteger(value);    _rect.right += left - _rect.left; _rect.left = left; } break;
            case "top":     { int top = TypeConverter.toInteger(value);     _rect.bottom += top - _rect.top; _rect.top = top; } break;
            case "width":   { int width = TypeConverter.toInteger(value);   _rect.right = _rect.left + width; } break;
            case "height":  { int height = TypeConverter.toInteger(value);  _rect.bottom = _rect.top + height; } break;
            case "opacity":     _opacity = TypeConverter.toFloat(value); break;
            case "z-index":     _z = TypeConverter.toInteger(value); break;
            case "visibility":  _visible = value.equals("inherit") || value.equals("visible"); break;
            case "cursor": break; //ignoring
            default:
                Log.v(TAG, "ignoring setStyle " + name + ": " + value);
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
            Object value = arguments.get(1);
            setStyle(arguments.getString(0), Wrapper.getValue(_env, null, value));
            if (value instanceof Releasable)
                ((Releasable)value).release();
        }
        else
            throw new Exception("invalid setStyle invocation");//fixme: leak of resources here
        if (arg0 instanceof Releasable)
            ((Releasable)arg0).release();
    }

    protected final void beginPaint() {
        _lastRect.setEmpty();
        _combinedRect.setEmpty();
    }

    protected final void endPaint() {
    }

    public final void paintChildren(PaintState parent) {
        if (_children != null) {
            for (Element child : _children) {
                PaintState state = new PaintState(parent, _rect.left, _rect.top, _opacity);
                child.paint(state);
                _combinedRect.union(child.getRect());
                _combinedRect.union(child.getCombinedRect());
                _lastRect.union(child.getLastRenderedRect());
            }
        }
    }

    public void paint(PaintState state) {
        beginPaint();
        if (_visible)
            paintChildren(state);
        endPaint();
    }

    public Rect getScreenRect() {
        Rect rect = new Rect(_rect);
        Element el = _parent;
        while(el != null) {
            rect.offset(el._rect.left, el._rect.top);
            el = el._parent;
        }
        return rect;
    }

    public boolean sendEvent(String name, int x, int y) {
        //fixme: optimize me, calculate combined rect for all children and remove out of bound elements
        int baseX = _rect.left;
        int baseY = _rect.top;
        int offsetX = x - baseX;
        int offsetY = y - baseY;

        //Log.v(TAG, this + ": " + name + ", position " + x + ", " + y + " " + _rect + " " + _rect.contains(x, y) + " " + hasCallbackFor(name));

        if (_children != null) {
            for (Element child : _children) {
                if (child.sendEvent(name, offsetX, offsetY))
                    return true;
            }
        }

        if (_rect.contains(x, y) && hasCallbackFor(name)) {
            V8Object mouseEvent = new V8Object(_env.getRuntime());
            mouseEvent.add("offsetX", offsetX);
            mouseEvent.add("offsetY", offsetY);
            emit(null, "click", mouseEvent);
            mouseEvent.close();
            return true;
        }
        return false;
    }

    static final Rect translateRect(Rect rect, int dx, int dy) {
        Rect r = new Rect(rect);
        r.offset(dx, dy);
        return r;
    }

    static final Paint patchAlpha(Paint paint, float opacity) {
        Paint alphaPaint = new Paint(paint);
        alphaPaint.setAlpha((int)(alphaPaint.getAlpha() * opacity));
        return alphaPaint;
    }
}
