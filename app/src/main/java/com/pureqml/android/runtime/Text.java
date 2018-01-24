package com.pureqml.android.runtime;

import android.graphics.Canvas;
import android.graphics.Rect;

import com.pureqml.android.IExecutionEnvironment;

public class Text extends Element {
    private final static String TAG = "rt.Text";
    protected int _color;

    public Text(IExecutionEnvironment env) {
        super(env);
    }

    protected void setStyle(String name, Object value) {
        switch(name) {
            case "color": _color = TypeConverter.toColor((String)value); break;
            default:
                super.setStyle(name, value);
                return;
        }
        update();
    }

    @Override
    protected Rect getEffectiveRect() {
        return _rect;
    }

    @Override
    public void paint(Canvas canvas, int baseX, int baseY, float opacity) {
        if (!_visible)
            return;

        super.paint(canvas, baseX, baseY, opacity);
    }
}
