package com.pureqml.android;


import android.graphics.Color;
import android.util.DisplayMetrics;
import android.util.Log;

import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.eclipsesource.v8.V8Value;

public final class TypeConverter {
    private static final String TAG = "TypeConverter";

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
        if (value.startsWith("#")) {
            int r, g, b, a = 255;
            int length = value.length();
            switch(length)
            {
                case 4:
                case 5:
                    r = Short.parseShort(value.substring(1, 2), 16);
                    g = Short.parseShort(value.substring(2, 3), 16);
                    b = Short.parseShort(value.substring(3, 4), 16);
                    r |= (r << 4);
                    g |= (g << 4);
                    b |= (b << 4);
                    if (length == 5) {
                        a = Short.parseShort(value.substring(4, 5), 16);
                        a |= (a << 4);
                    }
                    return Color.argb(a, r, g, b);
                case 7:
                case 9:
                    r = Short.parseShort(value.substring(1, 3), 16);
                    g = Short.parseShort(value.substring(3, 5), 16);
                    b = Short.parseShort(value.substring(5, 7), 16);
                    if (length == 9)
                        a = Short.parseShort(value.substring(7, 9), 16);
                    return Color.argb(a, r, g, b);
            }
        } else if (value.startsWith("rgba(")) {
            String [] components = value.substring(5, value.length() - 1).split(",");
            short a = (short)(255 * Float.parseFloat(components[3]));
            return Color.argb(
                a,
                Short.parseShort(components[0]),
                Short.parseShort(components[1]),
                Short.parseShort(components[2])
            );
        }
        Log.w(TAG, "invalid color specification: " + value);
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

    public static final Object getValue(IExecutionEnvironment env, Class<?> type, Object object) {
        if (type != null && object.getClass() == type) {
            return object;
        } else if (object instanceof V8Value) {
            V8Value value = (V8Value)object;
            switch(value.getV8Type()) {
                case V8Value.UNDEFINED:
                case V8Value.NULL:
                    value.close();
                    return null;
                case V8Value.V8_OBJECT:
                    if (type != V8Object.class && type != V8Function.class) {
                        Object element = env.getObjectById(value.hashCode());
                        value.close();
                        return element;
                    } else {
                        value.close();
                        return object;
                    }
                default:
                    value.close();
                    throw new Error("can't convert value of type " + value.getClass());
            }
        } else
            return object;
    }

}
