package com.pureqml.android.runtime;

import android.graphics.Color;

import com.pureqml.android.IExecutionEnvironment;

public class Rectangle extends Element {
    private final static String TAG = "rt.Rectangle";
    protected int   _radius;
    protected Color _color;

    public Rectangle(IExecutionEnvironment env) {
        super(env);
    }

    protected void setStyle(String name, Object value) {
        switch(name) {
            case "background-color":    _color = TypeConverter.toColor((String)value); break;
            case "border-radius":       _radius = TypeConverter.toInteger(value); break;
            default:
                super.setStyle(name, value);
        }
    }
}
