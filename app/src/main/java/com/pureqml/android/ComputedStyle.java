package com.pureqml.android;

import android.graphics.Typeface;
import android.util.Log;

public final class ComputedStyle {
    private static final String TAG = "ComputedStyle";

    public final String fontFamily;
    public final int fontWeight;
    public final int fontSize;
    public ComputedStyle(String fontFamily, int fontWeight, int fontSize) {
        this.fontFamily = fontFamily;
        this.fontWeight = fontWeight;
        this.fontSize = fontSize;
    }
    public static ComputedStyle merge(ComputedStyle left, ComputedStyle right) {
        if (left == null)
            return right;
        if (right == null)
            return left;
        return new ComputedStyle(
                right.fontFamily != null? right.fontFamily: left.fontFamily,
                right.fontWeight > 0? right.fontWeight: left.fontWeight,
                right.fontSize >= 0? right.fontSize: left.fontSize
        );
    }

    public String toString() {
        return String.format("font-family: %s; font-weight: %d; font-size: %d", fontFamily, fontWeight, fontSize);
    }

    public static int parseFontWeight(Object value) {
        if (value instanceof Integer) {
            return (Integer)value;
        }
        String fontWeightStr = value.toString();
        int fontWeight = 0;
        if (fontWeightStr.isEmpty())
            return fontWeight;

        switch(fontWeightStr) {
            case "bold":
                fontWeight = 700;
                break;
            case "normal":
                fontWeight = 400;
                break;
            default: try {
                fontWeight = Integer.parseInt(fontWeightStr);
            } catch(Exception ex) {
                Log.w(TAG, "font-weight parsing failed, value: " + fontWeightStr, ex);
            }
        }
        return fontWeight;
    }
}
