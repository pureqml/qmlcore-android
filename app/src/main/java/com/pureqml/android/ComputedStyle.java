package com.pureqml.android;

import android.graphics.Typeface;
import android.util.Log;

public final class ComputedStyle {
    private static final String TAG = "ComputedStyle";
    public static final int NormalWeight = 400;
    public static final int BoldWeight = 700;
    public static final float DefaultLineHeight = 1.2f;

    public final String fontFamily;
    public final int fontWeight;
    public final int fontSize;
    public final Float lineHeight;

    public ComputedStyle(String fontFamily, int fontWeight, int fontSize, Float lineHeight) {
        this.fontFamily = fontFamily;
        this.fontWeight = fontWeight;
        this.fontSize = fontSize;
        this.lineHeight = lineHeight;
    }
    public static ComputedStyle merge(ComputedStyle left, ComputedStyle right) {
        if (left == null)
            return right;
        if (right == null)
            return left;
        return new ComputedStyle(
                right.fontFamily != null? right.fontFamily: left.fontFamily,
                right.fontWeight > 0? right.fontWeight: left.fontWeight,
                right.fontSize >= 0? right.fontSize: left.fontSize,
                right.lineHeight != null? right.lineHeight: left.lineHeight
        );
    }

    public String toString() {
        return String.format("font-family: %s; font-weight: %d; font-size: %d; line-height: %g", fontFamily, fontWeight, fontSize, lineHeight);
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
                fontWeight = BoldWeight;
                break;
            case "normal":
                fontWeight = NormalWeight;
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
