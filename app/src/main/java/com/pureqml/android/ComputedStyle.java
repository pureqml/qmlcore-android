package com.pureqml.android;

import android.graphics.Typeface;

public final class ComputedStyle {
    public final Typeface typeface;
    public final int fontSize;
    public ComputedStyle(Typeface typeface, int fontSize) {
        this.typeface = typeface;
        this.fontSize = fontSize;
    }
    public static ComputedStyle merge(ComputedStyle left, ComputedStyle right) {
        if (left == null)
            return right;
        if (right == null)
            return left;
        return new ComputedStyle(
                right.typeface != null? right.typeface: left.typeface,
                right.fontSize >= 0? right.fontSize: left.fontSize
        );
    }
}
