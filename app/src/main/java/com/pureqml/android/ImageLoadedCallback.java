package com.pureqml.android;

import android.graphics.Bitmap;

import java.net.URL;

public interface ImageLoadedCallback {
    void onImageLoaded(URL url, Bitmap bitmap);
}
