package com.pureqml.android;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.SurfaceHolder;
import android.view.ViewGroup;

import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.pureqml.android.runtime.BaseObject;
import com.pureqml.android.runtime.TextLayoutCallback;

import java.net.URL;
import java.util.concurrent.ExecutorService;

public interface IExecutionEnvironment extends ImageLoadedCallback {
    Context getContext();
    ExecutorService getExecutor();
    ExecutorService getThreadPool();
    ViewGroup getRootView();

    V8 getRuntime();
    DisplayMetrics getDisplayMetrics();

    BaseObject getObjectById(long id);
    void putObject(long id, BaseObject element);
    void removeObject(long id);

    //invoke function + schedulePaint
    Object invokeCallback(V8Function callback, V8Object receiver, V8Array arguments);
    void invokeVoidCallback(V8Function callback, V8Object receiver, V8Array arguments);
    void schedulePaint();
    void repaint(SurfaceHolder holder);

    //image loader api
    AssetManager getAssets();
    ImageLoader.ImageResource loadImage(URL url, ImageLoadedCallback listener);

    //text layout api
    void layoutText(String text, Rect rect, boolean wrap, TextLayoutCallback callback);
}
