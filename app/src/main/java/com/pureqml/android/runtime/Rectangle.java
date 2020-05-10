package com.pureqml.android.runtime;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;

import com.pureqml.android.IExecutionEnvironment;

import static com.pureqml.android.TypeConverter.toColor;
import static com.pureqml.android.TypeConverter.toInteger;

public final class Rectangle extends Element {
    private final static String TAG = "rt.Rectangle";
    private Paint   _background;
    private Paint   _border;
    int             _radius = 0;
    int             _color = 0;

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

    @Override
    protected void setStyle(String name, Object value) {
        switch(name) {
            case "background-color":    _color = toColor(value); _background.setAlpha(Color.alpha(_color)); _background.setColor(_color); break;
            case "border-color":        getBorder().setColor(toColor(value)); break;
            case "border-width":        getBorder().setStrokeWidth(toInteger(value)); break;
            case "border-radius":
                try
                { _radius = toInteger(value); }
                catch(Exception ex)
                { Log.w(TAG, "unsupported radius spec", ex); }
                break;

            default:
                super.setStyle(name, value); return;
        }
        update();
    }

    @Override
    public void paint(PaintState state) {
        beginPaint();

        Canvas canvas = state.canvas;
        float opacity = state.opacity;
        Rect rect = getDstRect(state);

        if (_background.getColor() != 0) {
            Paint paint = patchAlpha(_background, Color.alpha(_color), opacity);
            if (paint != null) {
                if (_radius > 0) {
                    canvas.drawRoundRect(new RectF(rect), _radius, _radius, paint);
                } else {
                    canvas.drawRect(rect, paint);
                }
            }
        }

        if (_border != null) {
            Paint paint = patchAlpha(_border, Color.alpha(_color), opacity);
            if (paint != null) {
                if (_radius > 0) {
                    canvas.drawRoundRect(new RectF(rect), _radius, _radius, paint);
                } else {
                    canvas.drawRect(rect, paint);
                }
            }
        }

        _lastRect.union(rect);
        paintChildren(state);

        endPaint();
    }
}
