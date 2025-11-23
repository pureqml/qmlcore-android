package com.pureqml.android;

import android.graphics.Rect;
import android.util.DisplayMetrics;

public interface IRenderer {
    DisplayMetrics getDisplayMetrics();
    void invalidateRect(Rect rect);
    void keepScreenOn(boolean enable);
    void setFullScreen(boolean enable);
    void lockOrientation(String orientation);
    void closeApp();
    String getIntentParam(String text);
}
