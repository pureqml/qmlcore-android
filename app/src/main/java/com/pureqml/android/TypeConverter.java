package com.pureqml.android;


import android.graphics.Color;
import android.util.DisplayMetrics;
import android.util.Log;

import com.eclipsesource.v8.V8Object;
import com.eclipsesource.v8.V8Value;

public final class TypeConverter {
    private static final String TAG = "TypeConverter";

    public static boolean toBoolean(Object value) {
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

    public static int toInteger(Object value) {
        if (value instanceof Integer)
            return (int)value;
        else if (value instanceof Float)
            return (int)(float)value;
        else if (value instanceof Double)
            return (int)(double)value;
        else
            throw new RuntimeException("value " + value + " could not be converted to int");
    }

    public static float toFloat(Object value) {
        if (value instanceof Float)
            return (float)value;
        else if (value instanceof Integer)
            return (float)(int)value;
        else if (value instanceof Double)
            return (float)(double)value;
        else
            throw new RuntimeException("value " + value + " could not be converted to int");
    }

    public static int toColor(Object valueObject) {
        if (valueObject instanceof String) {
            String value = (String)valueObject;
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
        } else if (valueObject instanceof V8Object) {
            V8Object colorObject = (V8Object)valueObject;
            int r = colorObject.getInteger("r");
            int g = colorObject.getInteger("g");
            int b = colorObject.getInteger("b");
            int a = colorObject.getInteger("a");
            return Color.argb(a, r, g, b);
        }

        Log.w(TAG, "invalid color specification: " + valueObject);
        return 0xffff00ff; //invalid color
    }

    public static int toFontSize(String value, DisplayMetrics metrics) {
        int intValue = Integer.parseInt(value.substring(0, value.length() - 2));
        if (value.endsWith("px")) {
            return intValue;
        } else if (value.endsWith("pt")) {
            return (int)(intValue / 72.0f * metrics.ydpi);
        } else
            throw new RuntimeException("invalid unit in " + value);
    }

    public static Object getValue(IExecutionEnvironment env, Class<?> type, Object object) {
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
                    Object element = env.getObjectById(Wrapper.getObjectId((V8Object)object));
                    if (element != null) {
                        value.close();
                        return element;
                    } else
                        return object;
                default:
                    value.close();
                    throw new Error("can't convert value of type " + value.getClass());
            }
        } else
            return object;
    }

}
