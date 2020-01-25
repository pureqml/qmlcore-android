package com.pureqml.android.runtime;

import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.eclipsesource.v8.Releasable;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Object;
import com.pureqml.android.IExecutionEnvironment;
import com.pureqml.android.TypeConverter;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Element extends BaseObject {
    public static final String TAG = "rt.Element";

    public static final class AlreadyHasAParentException extends Exception {
        AlreadyHasAParentException() { super("AlreadyHasAParentException"); }
    };

    private Rect                _rect               = new Rect();
    protected Rect              _combinedRect       = new Rect();
    protected Rect              _lastRect           = new Rect();
    private Point               _translate;

    private   float             _opacity            = 1;
    protected boolean           _visible            = true;
    protected boolean           _globallyVisible    = false;
    protected Element           _parent;
    protected int               _z;
    protected List<Element>     _children;
    private boolean             _scrollX = false;
    private boolean             _scrollY = false;
    private boolean             _useScrollX = false;
    private boolean             _useScrollY = false;
    private Point               _scrollOffset;
    private Point               _scrollBase;
    private int                 _eventId;

    public Element(IExecutionEnvironment env) {
        super(env);
    }

    public final Rect getRect()
    {
        Rect rect = new Rect(_rect);
        if (_translate != null)
            rect.offset(_translate.x, _translate.y);
        return rect;
    }

    public final Rect getCombinedRect()
    { return _combinedRect; }
    public final Rect getLastRenderedRect()
    { return _lastRect; }
    public final boolean visible() {
        return _visible && _opacity >= PaintState.opacityThreshold;
    }

    public final int getScrollX() { return _scrollOffset != null? -_scrollOffset.x: 0; }
    public final int getScrollY() { return _scrollOffset != null? -_scrollOffset.y: 0; }

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

    static private Pattern _transformPattern = Pattern.compile("(\\w+)\\s*\\(([-+\\d]+)\\s*(\\w*)\\s*\\)\\s*");

    private void setTransform(String value) {
        Matcher matcher = _transformPattern.matcher(value);
        while(matcher.find()) {
            try {
                String unit = matcher.group(3);
                String transform = matcher.group(1);
                if (!unit.equals("px")) {
                    Log.w(TAG, "unknown unit '" + unit + "' used for '" + transform + "', skipping");
                    continue;
                }

                int n = Integer.parseInt(matcher.group(2));

                switch (transform) {
                    case "translateX":
                        if (_translate == null)
                            _translate = new Point();
                        _translate.x = n;
                        break;
                    case "translateY":
                        if (_translate == null)
                            _translate = new Point();
                        _translate.y = n;
                        break;
                    default:
                        Log.w(TAG, "skipping transform " + transform);
                }
            } catch (Exception ex) {
                Log.e(TAG, "transform parsing failed", ex);
            }
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
            case "transform": setTransform(value.toString()); break;
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

    protected final int getBaseX(int w) {
        return _rect.left + (_scrollOffset != null && w > _rect.width()? _scrollOffset.x: 0) + (_translate != null? _translate.x: 0);
    }

    protected final int getBaseY(int h) {
        return _rect.top + (_scrollOffset != null && h > _rect.height()? _scrollOffset.y: 0) + (_translate != null? _translate.y: 0);
    }

    public final void paintChildren(PaintState parent) {
        if (_children != null) {
            for (Element child : _children) {
                Rect childRect = child.getRect();
                PaintState state = new PaintState(parent, getBaseX(childRect.width()), getBaseY(childRect.height()), child._opacity);
                if (child._visible && state.visible()) {
                    child.paint(state);

                    _combinedRect.union(childRect);
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
            Rect elRect = el.getRect();
            rect.offset(elRect.left, elRect.top);
            el = el._parent;
        }
        return rect;
    }

    public boolean sendEvent(String keyName, KeyEvent event) {
        return emitUntilTrue(null, "keydown", keyName);
    }

    public boolean sendEvent(int eventId, int x, int y, MotionEvent event) throws Exception {
        if (!_globallyVisible)
            return false;

        boolean handled = false;
        Rect rect = getRect();

        //Log.v(TAG, this + ": position " + x + ", " + y + " " + rect + ", in " + rect.contains(x, y) + ", scrollable: " + (_scrollX || _scrollY));

        if (_children != null) {
            for(int i = _children.size() - 1; i >= 0; --i) {
                Element child = _children.get(i);
                Rect childRect = child.getRect();
                int offsetX = x - getBaseX(childRect.width());
                int offsetY = y - getBaseY(childRect.height());
                if (child.sendEvent(eventId, offsetX, offsetY, event)) {
                    handled = true;
                    break;
                }
            }
        }

        final String click = "click";

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                if (rect.contains(x, y) && (_scrollX || _scrollY || hasCallbackFor(click))) {
                    if (_scrollBase == null)
                        _scrollBase = new Point(); //FIXME: optimise me
                    _eventId = eventId;
                    _scrollBase.x = (int)event.getX();
                    _scrollBase.y = (int)event.getY();
                    _useScrollX = _useScrollY = false;
                    return true;
                } else
                    return handled;
            }

            case MotionEvent.ACTION_MOVE: {
                if (_eventId == eventId && (_scrollX || _scrollY)) {
                    if (_scrollBase != null) {
                        int dx = (int) (event.getX() - _scrollBase.x);
                        int dy = (int) (event.getY() - _scrollBase.y);
                        if (_scrollOffset == null)
                            _scrollOffset = new Point();

                        if (!_useScrollX && !_useScrollY) {
                            if (_scrollX && _scrollY) {
                                if (Math.abs(dx) > Math.abs(dy))
                                    _useScrollX = true;
                                else
                                    _useScrollY = true;
                            } else if (_scrollX) {
                                _useScrollX = true;
                            } else if (_scrollY) {
                                _useScrollY = true;
                            }
                            else
                                throw new Exception("invalid scrollX/scrollY combination");
                        }

                        if (_useScrollX) {
                            int clientWidth = _combinedRect.width();
                            if (_scrollX && rect.width() < clientWidth) {
                                if (rect.width() - dx > clientWidth)
                                    dx = clientWidth + dx;
                                if (dx > 0)
                                    dx = 0;
                                Log.i(TAG, "adjusting scrollX to " + dx);
                                _scrollOffset.x = dx;
                            }
                        }

                        if (_useScrollY) {
                            int clientHeight = _combinedRect.height();
                            if (_scrollY && rect.height() < clientHeight) {
                                if (rect.width() - dy > clientHeight)
                                    dy = clientHeight + dy;
                                if (dy > 0)
                                    dy = 0;
                                Log.i(TAG, "adjusting scrollY to " + dy);
                                _scrollOffset.y = dy;
                            }
                        }
                    }
                    return true;
                } else
                    return handled;
            }
            case MotionEvent.ACTION_UP: {
                if (handled)
                    return true;

                if (_eventId == eventId) {
                    if (rect.contains(x, y) && hasCallbackFor(click)) {
                        V8Object mouseEvent = new V8Object(_env.getRuntime());
                        mouseEvent.add("offsetX", x - rect.left);
                        mouseEvent.add("offsetY", y - rect.top);
                        emit(null, click, mouseEvent);
                        mouseEvent.close();
                        return true;
                    }
                    emit(null, "scroll");
                }
                return false;
            }
            default:
                return false;
        }
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
