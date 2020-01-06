package com.pureqml.android.runtime;


import android.graphics.Color;
import android.util.DisplayMetrics;

public class TypeConverter {
    public static final boolean toBoolean(Object value) {
        if (value instanceof Boolean)
            return (boolean)value;
        else if (value instanceof Integer)
            return (int)value != 0;
        else if (value instanceof Double)
            return (int)(double)value != 0;
        else if (value.equals("true"))
            return true;
        else if (value.equals("false"))
            return false;
        else
            throw new RuntimeException("value " + value + " could not be converted to boolean");
    }

    public static final int toInteger(Object value) {
        if (value instanceof Integer)
            return (int)value;
        else if (value instanceof Float)
            return (int)(float)value;
        else if (value instanceof Double)
            return (int)(double)value;
        else
            throw new RuntimeException("value " + value + " could not be converted to int");
    }

    public static final float toFloat(Object value) {
        if (value instanceof Float)
            return (float)value;
        else if (value instanceof Integer)
            return (float)(int)value;
        else if (value instanceof Double)
            return (float)(double)value;
        else
            throw new RuntimeException("value " + value + " could not be converted to int");
    }

    public static final int toColor(String value) {
        if (value.startsWith("rgba(")) {
            String [] components = value.substring(5, value.length() - 1).split(",");
            short a = (short)(255 * Float.parseFloat(components[3]));
            return Color.argb(
                a,
                Short.parseShort(components[0]),
                Short.parseShort(components[1]),
                Short.parseShort(components[2])
            );
        } else
            return 0xffff00ff; //invalid color
    }

    public static final int toFontSize(String value, DisplayMetrics metrics) throws Exception {
        if (value.endsWith("px")) {
            return Integer.valueOf(value.substring(0, value.length() - 2));
        } else if (value.endsWith("pt")) {
            int pt = Integer.valueOf(value.substring(0, value.length() - 2));
            return (int)(pt / 72.0f * metrics.ydpi);
        } else
            throw new Exception("invalid font size");
    }
}
