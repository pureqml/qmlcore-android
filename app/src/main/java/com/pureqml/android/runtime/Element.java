package com.pureqml.android.runtime;

import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;
import android.view.MotionEvent;

import com.eclipsesource.v8.Releasable;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Object;
import com.pureqml.android.IExecutionEnvironment;
import com.pureqml.android.TypeConverter;

import java.util.LinkedList;
import java.util.List;

public class Element extends BaseObject {
    public static final String TAG = "rt.Element";

    public static final class AlreadyHasAParentException extends Exception {
        AlreadyHasAParentException() { super("AlreadyHasAParentException"); }
    };

    protected Rect              _rect               = new Rect();
    protected Rect              _combinedRect       = new Rect();
    protected Rect              _lastRect           = new Rect();

    private   float             _opacity            = 1;
    protected boolean           _visible            = true;
    protected boolean           _globallyVisible    = false;
    protected Element           _parent;
    protected int               _z;
    protected List<Element>     _children;
    private boolean             _scrollX = false;
    private boolean             _scrollY = false;
    private int                 _scrollOffsetX = 0;
    private int                 _scrollOffsetY = 0;

    public Element(IExecutionEnvironment env) {
        super(env);
    }

    public final Rect getRect()
    { return _rect; }
    public final Rect getCombinedRect()
    { return _combinedRect; }
    public final Rect getLastRenderedRect()
    { return _lastRect; }
    public final boolean visible() {
        return _visible && _opacity >= PaintState.opacityThreshold;
    }

    public void append(BaseObject child) throws AlreadyHasAParentException {
        if (child == null)
            throw new NullPointerException("appending null element");

        if (!(child instanceof Element)) {
            Log.i(TAG, "skipping append(), not an Element instance");
            return;
        }

        Element el = (Element)child;
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
        super.discard();
    }

    void update() {
        _env.update(this);
    }

    protected void removeChild(Element child) {
        if (_children != null)
            _children.remove(child);
        _lastRect.union(child._lastRect);
        update();
    }

    private boolean getOverflowValue(Object objValue) {
        if (!(objValue instanceof String)) {
            Log.v(TAG, "ignoring overflow: " + objValue);
            return false;
        }
        String value = (String)objValue;
        switch(value) {
            case "auto":
                return true;
            case "hidden":
                return false;
            default:
                Log.v(TAG, "ignoring overflow: " + value);
                return false;
        }
    }

    protected void setStyle(String name, Object value) {
        switch(name) {
            case "left":    { int left = TypeConverter.toInteger(value);    _rect.right += left - _rect.left; _rect.left = left; } break;
            case "top":     { int top = TypeConverter.toInteger(value);     _rect.bottom += top - _rect.top; _rect.top = top; } break;
            case "width":   { int width = TypeConverter.toInteger(value);   _rect.right = _rect.left + width; } break;
            case "height":  { int height = TypeConverter.toInteger(value);  _rect.bottom = _rect.top + height; } break;
            case "opacity":     _opacity = TypeConverter.toFloat(value); break;
            case "z-index":     _z = TypeConverter.toInteger(value); break;
            case "visibility":  _visible = value.equals("inherit") || value.equals("visible"); break;
            case "recursive-visibility": {
                boolean globallyVisible = _globallyVisible;
                boolean visible = TypeConverter.toBoolean(value);
                if (globallyVisible != visible) {
                    _globallyVisible = visible;
                    onGloballyVisibleChanged(visible);
                }
                break;
            }
            case "cursor": break; //ignoring

            case "overflow":    _scrollX = _scrollY = getOverflowValue(value); break;
            case "overflow-x":  _scrollX = getOverflowValue(value);  break;
            case "overflow-y":  _scrollY = getOverflowValue(value);  break;

            default:
                Log.w(TAG, "ignoring setStyle " + name + ": " + value);
                return;
        }
        update();
    }

    protected void onGloballyVisibleChanged(boolean value) { }

    public void style(V8Array arguments) throws Exception {
        Object arg0 = arguments.get(0);
        if (arg0 instanceof V8Object) {
            V8Object styles = (V8Object) arg0;
            for (String key : styles.getKeys())
                setStyle(key, styles.get(key));
        } else if (arguments.length() == 2) {
            Object value = arguments.get(1);
            setStyle(arguments.getString(0), TypeConverter.getValue(_env, null, value));
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
                PaintState state = new PaintState(parent, _rect.left + _scrollOffsetX, _rect.top + _scrollOffsetY, child._opacity);
                if (child._visible && state.visible()) {
                    child.paint(state);

                    _combinedRect.union(child.getRect());
                    _combinedRect.union(child.getCombinedRect());
                    _lastRect.union(child.getLastRenderedRect());
                }
            }
        }
    }

    public void paint(PaintState state) {
        beginPaint();
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

    public boolean sendEvent(int x, int y, MotionEvent event) {
        //fixme: optimize me, calculate combined rect for all children and remove out of bound elements
        if (!_globallyVisible)
            return false;

        int baseX = _rect.left;
        int baseY = _rect.top;
        int offsetX = x - baseX;
        int offsetY = y - baseY;
        //  Log.v(TAG, this + ": position " + x + ", " + y + " " + _rect + " " + _rect.contains(x, y));

        if (_children != null) {
            for(int i = _children.size() - 1; i >= 0; --i) {
                Element child = _children.get(i);
                if (child.sendEvent(offsetX, offsetY, event))
                    return true;
            }
        }

        final String click = "click";

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                if (hasCallbackFor(click))
                    return true;
                else
                    return false;
            }

            case MotionEvent.ACTION_MOVE: {
                if (_scrollX || _scrollY) {
                    Log.i(TAG, "SCROLLABLE: " + event);
                    return true;
                } else
                    return false;
            }
        }

        String name = click;
        if (_rect.contains(x, y) && hasCallbackFor(name)) {
            V8Object mouseEvent = new V8Object(_env.getRuntime());
            mouseEvent.add("offsetX", offsetX);
            mouseEvent.add("offsetY", offsetY);
            emit(null, "click", mouseEvent);
            mouseEvent.close();
            return true;
        }
        else
            return false;
    }

    public void setAttribute(String name, String value) {
        Log.w(TAG, "ignoring setAttribute " + name + ", " + value);
    }

    public String getAttribute(String name) {
        Log.w(TAG, "ignoring getAttribute " + name);
        return "";
    }

    public void setProperty(String name, String value) {
        setAttribute(name, value);
    }

    public String getProperty(String name) {
        return getAttribute(name);
    }

    public void focus() {}
    public void blur() {}

    static final Rect translateRect(Rect rect, int dx, int dy) {
        Rect r = new Rect(rect);
        r.offset(dx, dy);
        return r;
    }

    static final Paint patchAlpha(Paint paint, int alpha, float opacity) {
        alpha = (int)(alpha * opacity);
        if (alpha <= 0)
            return null;

        Paint alphaPaint = new Paint(paint);
        alphaPaint.setAlpha(alpha);
        return alphaPaint;
    }
}
