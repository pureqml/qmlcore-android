package com.pureqml.android.runtime;

import android.animation.TimeInterpolator;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Picture;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;

import com.eclipsesource.v8.Releasable;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Object;
import com.pureqml.android.IExecutionEnvironment;
import com.pureqml.android.TypeConverter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class Element extends BaseObject {
    public static final String TAG = "rt.Element";

    public static final class AlreadyHasAParentException extends Exception {
        AlreadyHasAParentException() { super("AlreadyHasAParentException"); }
    };

    private Rect                _rect               = new Rect();
    private Rect                _combinedRect       = new Rect();
    protected Rect              _lastRect           = new Rect();
    private Point               _translate;
    private PointF              _scale;
    private float               _rotate             = 0;

    private   float             _opacity            = 1;
    protected boolean           _visible            = true;
    protected boolean           _globallyVisible;
    private boolean             _clip;
    protected int               _radius = 0;
    protected int               _innerBorder = 0;
    protected Element           _parent;
    protected int               _z;
    private boolean             _cache = false;
    private boolean             _cacheValid = false;
    private Picture             _cachePicture = null;

    protected ArrayList<Element> _children;

    private static final float  DetectionDistance = 5;
    private static final float  DetectionDistance2 = DetectionDistance * DetectionDistance;
    private static final float  MinimumScrollVelocity = 500;
    private static final float  DecelerateInterpolatorOrder = 3;
    private static final float  ScrollDuration = 3.0f;

    private boolean             _enableScrollX;
    private boolean             _enableScrollY;
    private boolean             _useScrollX;
    private boolean             _useScrollY;
    private Point               _scrollOffset;
    private Point               _motionStartPos;
    private Point               _scrollPos;
    private Point               _publicScrollPos; //bloody html, scroll is reported on parent element
    private int                 _eventId;

    //inertial scrolling
    private PointF              _scrollVelocity;
    private TimeInterpolator    _scrollInterpolator;
    private long                _scrollTimeBase;
    private long                _scrollTimeLast;

    public Element(IExecutionEnvironment env) {
        super(env);
    }

    public void animate() {
        if (_scrollVelocity == null)
            return;

        Log.v(TAG, "animate, scroll velocity: " + _scrollVelocity);
        long now = SystemClock.elapsedRealtime();
        float t = (now - _scrollTimeBase) / 1000.0f / ScrollDuration;
        float dt = (now - _scrollTimeLast) / 1000.0f;
        _scrollTimeLast = now;

        Log.v(TAG, "t: " + t + ", dt: " + dt);
        if (t > 1.0f) {
            t = 1.0f;
        }

        float vtdt = (1.0f - _scrollInterpolator.getInterpolation(t)) * dt;
        _scrollPos.x += _scrollVelocity.x * vtdt;
        _scrollPos.y += _scrollVelocity.y * vtdt;

        Rect rect = getRect();
        Rect parentRect = _parent.getRect();

        int clientWidth = rect.width(), clientHeight = rect.height();
        int w = parentRect.width(), h = parentRect.height();

        boolean enableScrollX = scrollXEnabled() && clientWidth > w;
        boolean enableScrollY = scrollYEnabled() && clientHeight > h;

        if (enableScrollX && _scrollPos.x + w > clientWidth) {
            Log.v(TAG, "stopping scrolling, right edge reached");
            _scrollPos.x = clientWidth - w;
            t = 1.0f; //finish
        }
        if (enableScrollY && _scrollPos.y + h > clientHeight) {
            Log.v(TAG, "stopping scrolling, bottom edge reached");
            _scrollPos.y = clientHeight - h;
            t = 1.0f; //finish
        }
        if (enableScrollX && _scrollPos.x < 0) {
            Log.v(TAG, "stopping scrolling, left edge reached");
            _scrollPos.x = 0;
            t = 1.0f; //finish
        }
        if (enableScrollY && _scrollPos.y < 0) {
            Log.v(TAG, "stopping scrolling, top edge reached");
            _scrollPos.y = 0;
            t = 1.0f; //finish
        }

        if (t >= 1.0f) {
            Log.v(TAG, "scroll finished, stopping");
            _env.stopAnimation(this);
            _scrollVelocity = null;
        }
        emitScroll();
    }

    public Rect getRedrawRect(Rect clipRect) {
        //this function tries to calculate rectangle if this element says it's invalidated
        Rect elementRect = new Rect();
        if (_globallyVisible) {
            Rect rect = new Rect(getScreenRect());
            if (rect.intersect(clipRect)) {
                //Log.v(TAG, "screen rect " + rect);
                elementRect.union(rect);
            }

            rect = new Rect(_combinedRect);
            if (rect.intersect(clipRect)) {
                //Log.v(TAG, "combined rect " + rect);
                elementRect.union(rect);
            }

        }
        Rect last = new Rect(_lastRect);
        if (last.intersect(clipRect)) {
            //Log.v(TAG, "last rect " + last);
            elementRect.union(last);
        }
        Element parent = _parent;
        while(parent != null) {
            parent._cacheValid = false;
            if (parent._clip && !elementRect.isEmpty()) {
                Rect parentRect = parent.getScreenRect(); //fixme: this makes this loop O(N^2)
                elementRect.intersect(parentRect);
            }
            parent = parent._parent;
        }
        return elementRect;
    }

    public void enableCache(boolean enable)
    {
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return; //fixme: could not be replayed on hw-accelerated canvas, use software on pre-M ?
        }

        if (_cache != enable) {
            _cache = enable;
            if (!enable)
                _cachePicture = null;
        }
    }

    public final Rect getRect()
    {
        Rect rect = new Rect(_rect);
        if (_translate != null)
            rect.offset(_translate.x, _translate.y);
        return rect;
    }

    final boolean scrollXEnabled()  { return _parent != null? _parent._enableScrollX: false; }
    final boolean scrollYEnabled()  { return _parent != null? _parent._enableScrollY: false; }
    final boolean scrollUsed()      { return _parent != null? _parent._useScrollX || _parent._useScrollY: false; }

    final int getScrollXImpl() {
        int x = _scrollOffset != null? _scrollOffset.x: 0;
        x += _scrollPos != null? _scrollPos.x: 0;
        return x;
    }

    final int getScrollYImpl() {
        int y = _scrollOffset != null? _scrollOffset.y: 0;
        y += _scrollPos != null? _scrollPos.y: 0;
        return y;
    }

    public final int getScrollX() {
        return _publicScrollPos != null? _publicScrollPos.x: 0;
    }

    public final int getScrollY() {
        return _publicScrollPos != null? _publicScrollPos.y: 0;
    }

    private Element ensureParentAndChildren(BaseObject child) throws AlreadyHasAParentException {
        if (child == null)
            throw new NullPointerException("appending null element");

        if (!(child instanceof Element)) {
            Log.i(TAG, "skipping append(), not an Element instance");
            return null;
        }

        Element el = (Element)child;
        if (el._parent != null)
            throw new AlreadyHasAParentException();
        el._parent = this;
        if (_children == null)
            _children = new ArrayList<>();
        return el;
    }

    public void append(BaseObject child) throws AlreadyHasAParentException {
        Element el = ensureParentAndChildren(child);
        if (el == null)
            return;
        _children.add(el);
        el.update();
    }

    public void prepend(BaseObject child) throws AlreadyHasAParentException {
        Element el = ensureParentAndChildren(child);
        if (el == null)
            return;

        _children.add(0, el);
        el.update();
    }

    public void remove() {
        if (_parent != null)
            _parent.removeChild(this);
        _parent = null;
    }

    public void discard() {
        remove();
        _cacheValid = false;
        _cachePicture = null;
        super.discard();
    }

    void update() {
        _env.update(this);
        _cacheValid = false;
    }

    public void updateStyle() {}

    public void addClass(String classname)
    { Log.d(TAG, "ignoring addClass " + classname); }

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
            case "scroll":
                _clip = true;
                return true;
            case "visible":
                _clip = false;
                return false;
            case "hidden":
                _clip = true;
                return false;
            default:
                Log.v(TAG, "ignoring overflow: " + value);
                return false;
        }
    }

    private void setTransform(Object object) {
        if (!(object instanceof V8Object)) {
            Log.w(TAG, "setTransform expects object");
            return;
        }
        V8Object descriptor = ((V8Object) object).getObject("transforms");
        for(String name : descriptor.getKeys()) {
            V8Object value = descriptor.getObject(name);
            double n = value.getDouble("value");

            switch (name) {
                case "translateX":
                    if (_translate == null)
                        _translate = new Point();
                    _translate.x = (int)n;
                    break;
                case "translateY":
                    if (_translate == null)
                        _translate = new Point();
                    _translate.y = (int)n;
                    break;
                case "scaleX":
                    if (_scale == null)
                        _scale = new PointF();
                    _scale.x = (float)n;
                    break;
                case "scaleY":
                    if (_scale == null)
                        _scale = new PointF();
                    _scale.y = (float)n;
                    break;
                case "rotate":
                case "rotateZ":
                    _rotate = (float)n;
                    break;
                default:
                    Log.d(TAG, "skipping transform " + name);
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
            case "z-index":     _z = TypeConverter.toInteger(value); if (this._parent != null) this._parent.sortChildren(); break;
            case "visibility":  _visible = value.equals("inherit") || value.equals("visible"); break;
            case "transform": setTransform(value); break;
            case "-pure-recursive-visibility": {
                boolean globallyVisible = _globallyVisible;
                boolean visible = TypeConverter.toBoolean(value);
                if (globallyVisible != visible) {
                    _globallyVisible = visible;
                    onGloballyVisibleChanged(visible);
                }
                break;
            }

            case "overflow":    _enableScrollX = _enableScrollY = getOverflowValue(value); break;
            case "overflow-x":  _enableScrollX = getOverflowValue(value);  break;
            case "overflow-y":  _enableScrollY = getOverflowValue(value);  break;

            case "cursor":
            case "pointer-events":
            case "touch-action":
                return; //ignoring

            case "will-change":
                //Log.v(TAG, "will-change: " + value);
                if (value.toString().contains("scroll-position"))
                    enableCache(true);
                break;

            default:
                Log.d(TAG, "ignoring setStyle " + name + ": " + value);
                return;
        }
        update();
    }

    protected void onGloballyVisibleChanged(boolean value) { }
    private void setStyleSafe(String name, Object value) {
        try {
            this.setStyle(name, value);
        } catch (Exception ex) {
            Log.w(TAG, "setStyle failed", ex);
        }
    }

    public void style(V8Array arguments) throws Exception {
        Object arg0 = arguments.get(0);
        if (arg0 instanceof V8Object) {
            V8Object styles = (V8Object) arg0;
            for (String key : styles.getKeys())
                setStyleSafe(key, styles.get(key));
        } else if (arguments.length() == 2) {
            Object value = arguments.get(1);
            setStyleSafe(arguments.getString(0), TypeConverter.getValue(_env, null, value));
            if (value instanceof Releasable)
                ((Releasable)value).release();
        }
        else
            Log.w(TAG, "invalid setStyle invocation " + arguments);

        if (arg0 instanceof Releasable)
            ((Releasable)arg0).release();
    }

    private final boolean roundClippingNeeded() {
        return _radius > 0 && android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    protected final void beginPaint(PaintState state) {
        _lastRect.setEmpty();
        _combinedRect.setEmpty();
        if (roundClippingNeeded()) {
            state.roundClipWorkaround = true;
        }
    }

    protected final void endPaint() {
    }

    protected final int getBaseX() {
        return _rect.left + (_translate != null? _translate.x: 0);
    }

    protected final int getBaseY() {
        return _rect.top + (_translate != null? _translate.y: 0);
    }

    @SuppressWarnings("unchecked")
    public final void paintChildren(PaintState parent) {
        if (_children == null)
            return;

        ArrayList<Element> children = (ArrayList<Element>)_children.clone();
        int scrollX = -getScrollXImpl(), scrollY = -getScrollYImpl();

        for (Element child : children) {
            float opacity = child._opacity * parent.opacity;
            if (!child._visible || !PaintState.visible(opacity))
                continue;

            Rect childRect = child.getRect();
            int childX = scrollX + child.getBaseX() + _innerBorder, childY = scrollY + child.getBaseY() + _innerBorder;
            int childWidth = childRect.width(), childHeight = childRect.height();
            boolean cache = child._cache;

            if (!child._cacheValid) {
                PaintState state;
                if (cache) {
                    if (child._cachePicture == null)
                        child._cachePicture = new Picture();
                    state = new PaintState(child._cachePicture, childWidth, childHeight, opacity);
                } else {
                    state = new PaintState(parent, childX, childY, opacity);
                }

                final boolean clip = child._clip && !cache; //fixme: disable clipping when caching (should be implicit)
                boolean paint = true;
                boolean saveCanvasState = clip || _scale != null || _rotate != 0;
                int canvasRestorePoint;

                if (saveCanvasState)
                    canvasRestorePoint = state.canvas.save();
                else
                    canvasRestorePoint = -1;

                if (_scale != null) {
                    //Log.v(TAG, "adjusting scale to " + _scale);
                    state.canvas.scale(_scale.x, _scale.y, state.baseX + childWidth / 2.0f, state.baseY + childHeight / 2.0f);
                }

                if (_rotate != 0) {
                    state.canvas.rotate(_rotate, state.baseX + childWidth / 2.0f, state.baseY + childHeight / 2.0f);
                }

                if (clip) {
                    if (roundClippingNeeded()) {
                        Path path = new Path();
                        path.addRoundRect(state.baseX, state.baseY, state.baseX + childWidth, state.baseY + childHeight, _radius, _radius, Path.Direction.CW);
                        if (!state.canvas.clipPath(path))
                            paint = false;
                    } else {
                        if (!state.canvas.clipRect(new Rect(state.baseX, state.baseY, state.baseX + childWidth, state.baseY + childHeight)))
                            paint = false;
                    }
                }

                if (paint) {
                    child.paint(state);
                }

                if (saveCanvasState) {
                    state.canvas.restoreToCount(canvasRestorePoint);
                }
                if (cache) {
                    state.end();
                    child._cacheValid = true;
                }
            }

            if (child._cacheValid) {
                int saveCount = parent.canvas.save();
                parent.canvas.translate(parent.baseX + childX, parent.baseY + childY);
                parent.canvas.drawPicture(child._cachePicture);
                parent.canvas.restoreToCount(saveCount);
            }

            childRect.offset(parent.baseX, parent.baseY);
            _combinedRect.union(childRect);

            _combinedRect.union(child._combinedRect);
            Rect last = child._lastRect;
            if (cache)
                last.offset(childX, childY);
            _lastRect.union(last);
        }
    }

    public void paint(PaintState state) {
        beginPaint(state);
        paintChildren(state);
        endPaint();
    }

    public Rect getScreenRect() {
        Rect rect = getRect();
        Element el = _parent;
        while(el != null) {
            rect.offset(el.getBaseX() - el.getScrollXImpl(), el.getBaseY() - el.getScrollYImpl());
            el = el._parent;
        }
        return rect;
    }

    public boolean sendEvent(String keyName, KeyEvent event) {
        Log.v(TAG, "sending " + keyName + " key...");
        return emitUntilTrue(null, "keydown", keyName);
    }

    private final void emitScroll() {
        _parent._publicScrollPos.set(getScrollXImpl() - this.getBaseX() , getScrollYImpl() - this.getBaseY());
        _parent.emit(null, "scroll");
        update();
    }

    public boolean sendEvent(int eventId, int x, int y, MotionEvent event) throws Exception {
        if (!_globallyVisible)
            return false;

        boolean handled = false;
        Rect rect = getRect();
        int clientWidth = rect.width();
        int clientHeight = rect.height();

        x += getScrollXImpl();
        y += getScrollYImpl();

        if (_children != null) {
            for (int i = _children.size() - 1; i >= 0; --i) {
                Element child = _children.get(i);
                int offsetX = x - getBaseX();
                int offsetY = y - getBaseY();
                if (_clip && (offsetX < 0 || offsetY < 0 || offsetX > clientWidth || offsetY > clientHeight))
                    continue;

                if (child.sendEvent(eventId, offsetX, offsetY, event)) {
                    handled = true;
                    break;
                }
            }
        }

        if (_parent == null)
            return handled;

        final String click = "click";

        Rect parentRect = _parent.getRect();
        int w = parentRect.width(), h = parentRect.height();
        boolean enableScrollX = scrollXEnabled() && clientWidth > w;
        boolean enableScrollY = scrollYEnabled() && clientHeight > h;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                if (rect.contains(x, y) && (enableScrollX || enableScrollY || hasCallbackFor(click))) {
                    if (_motionStartPos == null)
                        _motionStartPos = new Point(); //FIXME: optimise me? (unwrap to 2 int members)
                    if (_scrollPos == null)
                        _scrollPos = new Point();
                    if (_scrollOffset == null)
                        _scrollOffset = new Point();
                    if (_parent._publicScrollPos == null)
                        _parent._publicScrollPos = new Point();
                    _scrollVelocity = null;
                    _eventId = eventId;
                    _motionStartPos.x = (int) event.getX();
                    _motionStartPos.y = (int) event.getY();
                    _useScrollX = _useScrollY = false;
                    return true;
                } else
                    return handled;
            }

            case MotionEvent.ACTION_MOVE: {
                boolean handleMove = false;
                if (!handled && _eventId == eventId && (enableScrollX || enableScrollY)) {
                    int dx = (int) (event.getX() - _motionStartPos.x);
                    int dy = (int) (event.getY() - _motionStartPos.y);
                    if (_scrollVelocity != null) {
                        //pause scrolling surface
                        if (_scrollVelocity.x != 0) {
                            _useScrollX = true;
                            handleMove = true;
                        } else if (_scrollVelocity.y != 0) {
                            _useScrollY = true;
                            handleMove = true;
                        }
                    }

                    if (!_useScrollX && !_useScrollY) {
                        float distance = (float) Math.hypot((double) dx, (double) dy);
                        if (distance >= DetectionDistance2) {
                            if (enableScrollX && enableScrollY) {
                                if (Math.abs(dx) > Math.abs(dy))
                                    _useScrollX = true;
                                else
                                    _useScrollY = true;
                                handleMove = true;
                            } else if (enableScrollX) {
                                if (Math.abs(dx) > Math.abs(dy)) {
                                    _useScrollX = true;
                                    handleMove = true;
                                }
                            } else if (enableScrollY) {
                                if (Math.abs(dy) > Math.abs(dx)) {
                                    _useScrollY = true;
                                    handleMove = true;
                                }
                             }
                        }
                    }

                    if (_useScrollX) {
                        _scrollOffset.x = -dx;

                        if (_scrollPos.x + _scrollOffset.x + w > clientWidth)
                            _scrollOffset.x = clientWidth - w - _scrollPos.x;

                        if (_scrollPos.x + _scrollOffset.x < 0)
                            _scrollOffset.x = -_scrollPos.x;

                        Log.v(TAG, "adjusting scrollX to " + (_scrollPos.x + _scrollOffset.x));
                        handleMove = true;
                    }

                    if (_useScrollY) {
                        _scrollOffset.y = -dy;

                        if (_scrollPos.y + _scrollOffset.y + h > clientHeight)
                            _scrollOffset.y = clientHeight - h - _scrollPos.y;

                        if (_scrollPos.y + _scrollOffset.y < 0)
                            _scrollOffset.y = -_scrollPos.y;

                        Log.v(TAG, "adjusting scrollY to " + (_scrollPos.y + _scrollOffset.y));
                        handleMove = true;
                    }
                    if (handleMove)
                        emitScroll();
                    return handleMove;
                } else
                    return handled;
            }
            case MotionEvent.ACTION_UP: {
                if (_eventId == eventId) {
                    boolean scrollUsed = scrollUsed();
                    Log.v(TAG, "handled by parent " + handled + ", parent scroll: " + scrollUsed);
                    if (_useScrollX || _useScrollY) {
                        _useScrollX = _useScrollY = false;
                        _scrollPos.x += _scrollOffset.x;
                        _scrollPos.y += _scrollOffset.y;
                        Log.d(TAG, "scrolling finished at " + _scrollOffset + ", final position: " + _scrollPos);
                        boolean noScroll = _scrollOffset.x == 0 && _scrollOffset.y == 0;

                        if (!noScroll) {
                            float delta = (event.getEventTime() - event.getDownTime()) / 1000.0f;
                            PointF scrollVelocity = new PointF(_scrollOffset.x / delta, _scrollOffset.y / delta);
                            if (scrollVelocity.length() >= MinimumScrollVelocity) {
                                Log.v(TAG, "scroll velocity: " + scrollVelocity);
                                if (_scrollInterpolator == null)
                                    _scrollInterpolator = new DecelerateInterpolator(DecelerateInterpolatorOrder);

                                _scrollTimeBase = _scrollTimeLast = SystemClock.elapsedRealtime();
                                _scrollVelocity = scrollVelocity;
                                _env.startAnimation(this, ScrollDuration);
                            } else
                                Log.v(TAG, "ignoring scroll, less than limit");
                        }

                        _scrollOffset = null;

                        if (noScroll)
                            return false;
                        emitScroll();
                        return true;
                    } else if (handled) {
                        Log.v(TAG, "handled by parent");
                        return true;
                    } else if (!scrollUsed && rect.contains(x, y) && hasCallbackFor(click)) {
                        Log.d(TAG, "emitting click: position " + x + ", " + y + " " + rect + ", in " + rect.contains(x, y) + ", scrollable: " + (_enableScrollX || _enableScrollY));
                        V8Object mouseEvent = new V8Object(_env.getRuntime());
                        mouseEvent.add("offsetX", x - rect.left);
                        mouseEvent.add("offsetY", y - rect.top);
                        emit(null, click, mouseEvent);
                        mouseEvent.close();
                        return true;
                    }
                }
                return false;
            }
            default:
                return false;
        }
    }

    public void setAttribute(String name, String value) {
        Log.d(TAG, "ignoring setAttribute " + name + ", " + value);
    }

    public String getAttribute(String name) {
        Log.d(TAG, "ignoring getAttribute " + name);
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

    public Rect getDstRect(PaintState state) {
        Rect rect = new Rect(0, 0, _rect.width(), _rect.height());
        rect.offset(state.baseX, state.baseY);
        return rect;
    }

    static final Paint patchAlpha(Paint paint, int alpha, float opacity) {
        alpha = (int)(alpha * opacity);
        if (alpha <= 0)
            return null;

        Paint alphaPaint = new Paint(paint);
        alphaPaint.setAlpha(alpha);
        return alphaPaint;
    }

    static private final class ZComparator implements Comparator<Element>
    {
        public int compare(Element e1, Element e2)
        {
            return e1._z - e2._z;
        }
    }

    protected final void sortChildren() {
        Collections.sort(_children, new ZComparator());
    }
}
