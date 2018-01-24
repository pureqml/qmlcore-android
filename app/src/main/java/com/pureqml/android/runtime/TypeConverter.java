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
        if (value.startsWith("rgba(")) {
            String [] components = value.substring(5, value.length() - 1).split(",");
            short a = (short)(255 * Float.parseFloat(components[3]));
            return (
                (a << 24) |
                (Short.parseShort(components[0]) << 16) |
                (Short.parseShort(components[1]) << 8) |
                (Short.parseShort(components[2]))
            );
        } else
            return 0xffff00ff; //invalid color
    }

    public static final int toFontSize(String value) {
        return 12;
    }
}
