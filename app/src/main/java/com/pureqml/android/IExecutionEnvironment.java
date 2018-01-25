package com.pureqml.android;

import android.content.res.AssetManager;
import android.graphics.Rect;
import android.util.DisplayMetrics;

import com.eclipsesource.v8.V8;
import com.pureqml.android.runtime.Element;

import java.net.URL;
import java.util.concurrent.ExecutorService;

public interface IExecutionEnvironment extends ImageLoadedCallback {
    ExecutorService getExecutor();
    ExecutorService getThreadPool();

    V8 getRuntime();
    DisplayMetrics getDisplayMetrics();

    Element getElementById(long id);
    void putElement(long id, Element element);
    void removeElement(long id);

    //image loader api
    AssetManager getAssets();
    ImageLoader.ImageResource loadImage(URL url, ImageLoadedCallback listener);

    //text layout api
    void layoutText(String text, Rect rect, TextLayoutCallback callback);
}
