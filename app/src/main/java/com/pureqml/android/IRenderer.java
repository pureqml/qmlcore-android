package com.pureqml.android;

import android.graphics.Rect;

public interface IRenderer {
    void invalidateRect(Rect rect);
    void keepScreenOn(boolean enable);
    void setFullScreen(boolean enable);
    void lockOrientation(String orientation);
}
