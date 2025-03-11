package com.pureqml.android.runtime;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.Log;

import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Object;
import com.pureqml.android.IExecutionEnvironment;
import com.pureqml.android.TypeConverter;

import static com.pureqml.android.TypeConverter.toColor;
import static com.pureqml.android.TypeConverter.toFloat;
import static com.pureqml.android.TypeConverter.toInteger;

public final class Rectangle extends Element {
    private final static String TAG = "rt.Rectangle";
    private final Paint   _background;
    private Paint   _border;
    boolean         _outerBorder = false;
    float           _borderWidth = 0;
    int             _color = 0;
    String          _gradientOrientation = null;
    int[] _gradientColors = null;
    float[] _gradientPositions = null;

    private void setupPaint(Paint paint) {
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));
    }

    public Rectangle(IExecutionEnvironment env) {
        super(env);
        _background = new Paint(Paint.ANTI_ALIAS_FLAG);
        _background.setStyle(Paint.Style.FILL);
        setupPaint(_background);
    }

    Paint getBorder() {
        if (_border == null) {
            _border = new Paint(Paint.ANTI_ALIAS_FLAG);
            _border.setStyle(Paint.Style.STROKE);
            setupPaint(_border);
        }
        return _border;
    }

    private void parseBackgroundDescription(Object value) {
        if (!(value instanceof V8Object)) {
            Log.d(TAG, "invalid background descriptor, non-object: " + value);
            return;
        }
        V8Object descriptor = (V8Object)value;
        _gradientOrientation = descriptor.getString("orientation");
        Log.v(TAG, "gradient orientation: " + _gradientOrientation);
        V8Array stops = descriptor.getArray("stops");

        _gradientColors = new int[stops.length()];
        _gradientPositions = new float[stops.length()];

        for(int i = 0; i < stops.length(); ++i) {
            V8Object stop = stops.getObject(i);
            _gradientColors[i] = TypeConverter.toColor(stop.get("color"));
            _gradientPositions[i] = TypeConverter.toFloat(stop.get("position"));
        }
    }

    Rect getGradientRect(Rect rect) {
        Rect r = new Rect(rect);
        switch(_gradientOrientation)
        {
            case "to bottom":
                r.right = r.left;
                break;
            case "to top right":
                r.bottom = r.top;
                break;
            case "to bottom right":
                break;
            case "to left":
                r.bottom = r.top;
                int l = r.left;
                r.left = r.right;
                r.right = l;
                break;
            default:
                Log.d(TAG, "unsupported gradient orientation");
        }
        return r;
    }

    @Override
    protected void setStyle(String name, Object value) {
        switch(name) {
            case "background":
                parseBackgroundDescription(value);
                break;

            case "border-color":
                getBorder().setColor(toColor(value));
                break;

            case "border-width":
                _borderWidth = toFloat(value);
                getBorder().setStrokeWidth(_borderWidth);
                break;

            case "box-sizing":
                if (value.equals("content-box")) {
                    _outerBorder = true;
                } else if (value.equals("border-box")) {
                    _outerBorder = false;
                }
                break;

            case "background-color":
                if (value.equals("")) //fixme: reset something?
                    break;
                _color = toColor(value);
                _background.setAlpha(Color.alpha(_color));
                _background.setColor(_color);
                _gradientColors = null;
                _gradientPositions = null;
                _gradientOrientation = null;
                break;
            case "border-radius":
                try
                { _radius = toInteger(value); }
                catch(Exception ex)
                { Log.w(TAG, "unsupported radius spec", ex); }
                break;

            default:
                super.setStyle(name, value);
                return;
        }
        update();
    }

    private void unionWithBorder(Rect rect) {
        if (_outerBorder && _borderWidth > 0) {
            int width = (int)Math.ceil(_borderWidth);
            int inset = -width;
            rect.inset(inset, inset);
        }
    }

    @Override
    protected Rect createRedrawRect() {
        if (!_globallyVisible)
            return super.createRedrawRect();

        Rect rect = getScreenRect();
        if (_outerBorder && _borderWidth > 0) {
            int width = (int)Math.ceil(_borderWidth);
            rect.offset(width, width);
        }

        unionWithBorder(rect);
        return rect;
    }

    @Override
    public void paint(PaintState state) {
        beginPaint(state);
        PaintState childrenState = null;

        float opacity = state.opacity;
        Rect rect = getDstRect(state);

        if (_outerBorder && _borderWidth > 0) {
            int bw = (int)_borderWidth;
            rect.offset(bw, bw);
            childrenState = new PaintState(state, bw, bw, 1.0f);
        }

        if (_background.getColor() != 0 || _gradientOrientation != null) {
            if (_gradientOrientation != null) {
                Rect gradientRect = getGradientRect(rect);
                LinearGradient lg = new LinearGradient(
                        gradientRect.left, gradientRect.top,
                        gradientRect.right, gradientRect.bottom,
                        _gradientColors, _gradientPositions, Shader.TileMode.CLAMP
                );
                _background.setShader(lg);
            }
            else
                _background.setShader(null);

            Paint paint = patchAlpha(_background, Color.alpha(_color), opacity);
            if (paint != null) {
                if (_radius > 0) {
                    state.drawRoundRect(rect, _radius, _radius, paint);
                } else {
                    state.drawRect(rect, paint);
                }
            }
        }

        if (_border != null && _borderWidth > 0) {
            Paint paint = patchAlpha(_border, Color.alpha(_color), opacity);
            RectF borderRect = new RectF(rect);
            float inset = _borderWidth / 2.0f;
            if (_outerBorder)
                inset -= _borderWidth;
            borderRect.inset(inset, inset);
            if (paint != null) {
                if (_radius > 0) {
                    state.drawRoundRect(borderRect, _radius, _radius, paint);
                } else {
                    state.drawRect(borderRect, paint);
                }
            }
        }

        _lastRect.union(state.getDirtyRect());

        paintChildren(childrenState != null? childrenState: state);

        endPaint();
    }
}
