package com.pureqml.android.runtime;


import android.graphics.Color;

public class TypeConverter {
    public static final int toInteger(Object value) {
        if (value instanceof Integer)
            return (int)value;
        else if (value instanceof Float)
            return (int)(float)value;
        else if (value instanceof Double)
            return (int)(double)value;
        throw new RuntimeException("value " + value + " could not be converted to int");
    }

    public static final float toFloat(Object value) {
        if (value instanceof Float)
            return (float)value;
        else if (value instanceof Integer)
            return (float)(int)value;
        else if (value instanceof Double)
            return (float)(double)value;
        throw new RuntimeException("value " + value + " could not be converted to int");
    }

    public static final int toColor(String value) {
        return 0xff00ff;
    }
}
