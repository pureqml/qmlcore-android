package com.pureqml.android.runtime;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import com.pureqml.android.IExecutionEnvironment;

import static com.pureqml.android.runtime.TypeConverter.*;

public class Rectangle extends Element {
    private final static String TAG = "rt.Rectangle";
    private Paint   _background = new Paint();
    private Paint   _border;
    int             _radius;

    public Rectangle(IExecutionEnvironment env) {
        super(env);
        _background.setStyle(Paint.Style.FILL);
    }
    Paint getBorder() {
        if (_border == null) {
            _border = new Paint(Paint.ANTI_ALIAS_FLAG);
            _border.setStyle(Paint.Style.STROKE);
        }
        return _border;
    }

    protected void setStyle(String name, Object value) throws Exception {
        switch(name) {
            case "background-color":    _background.setColor(toColor((String)value)); break;
            case "border-color":        getBorder().setColor(toColor((String)value)); break;
            case "border-width":        getBorder().setStrokeWidth(toInteger(value)); break;
            case "border-radius":       _radius = toInteger(value); break;
            default:
                super.setStyle(name, value); return;
        }
        update();
    }

    @Override
    public void paint(Canvas canvas, int baseX, int baseY, float opacity) {
        beginPaint();
        if (_visible) {
            Rect rect = translateRect(_rect, baseX, baseY);
            if (_radius > 0) {
                canvas.drawRoundRect(new RectF(rect), _radius, _radius, patchAlpha(_background, opacity));
            } else {
                canvas.drawRect(rect, patchAlpha(_background, opacity));
            }

            if (_border != null) {
                if (_radius > 0) {
                    canvas.drawRoundRect(new RectF(rect), _radius, _radius, patchAlpha(_border, opacity));
                } else {
                    canvas.drawRect(rect, patchAlpha(_border, opacity));
                }
            }
            _lastRect.union(rect);
            paintChildren(canvas, baseX, baseY, opacity);
        }
        endPaint();
    }
}
