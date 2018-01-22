package com.pureqml.android.runtime;

import android.graphics.Color;

import com.pureqml.android.IExecutionEnvironment;

public class Text extends Element {
    private final static String TAG = "rt.Text";
    protected Color _color;

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
}
