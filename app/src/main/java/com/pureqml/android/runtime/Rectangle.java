package com.pureqml.android.runtime;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;

import com.pureqml.android.IExecutionEnvironment;

import static com.pureqml.android.runtime.TypeConverter.*;

public class Rectangle extends Element {
    private final static String TAG = "rt.Rectangle";
    private Paint   _background = new Paint();
    int             _radius;

    public Rectangle(IExecutionEnvironment env) {
        super(env);
        _background.setStyle(Paint.Style.FILL_AND_STROKE);
    }

    protected void setStyle(String name, Object value) {
        switch(name) {
            case "background-color":    _background.setColor(toColor((String)value)); break;
            case "border-width":        _background.setStrokeWidth(toInteger(value)); break;
            case "border-radius":       _radius = toInteger(value); break;
            default:
                super.setStyle(name, value);
        }
    }

    @Override
    protected Rect getEffectiveRect() {
        return _rect;
    }

    @Override
    public void paint(Canvas canvas, int baseX, int baseY) {
        Rect rect = translateRect(_rect, baseX, baseY);
        if (_radius > 0)
            canvas.drawRoundRect(new RectF(rect), _radius, _radius, _background);
        else
            canvas.drawRect(rect, _background);
        super.paint(canvas, baseX, baseY);
    }
}
