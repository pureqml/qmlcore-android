package com.pureqml.android;

import android.graphics.Bitmap;

import java.net.URI;

public interface ImageLoadedCallback {
    void onImageLoaded(URI url, Bitmap bitmap);
}
