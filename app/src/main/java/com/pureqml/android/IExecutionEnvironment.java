package com.pureqml.android;

import android.content.res.AssetManager;

import com.eclipsesource.v8.V8;
import com.pureqml.android.runtime.Element;

import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

public interface IExecutionEnvironment {
    ExecutorService getExecutor();
    ExecutorService getThreadPool();

    V8 getRuntime();

    Element getElementById(long id);

    void putElement(long id, Element element);

    void removeElement(long id);

    //image loader api
    AssetManager getAssets();
    ImageLoader.ImageResource loadImage(URL url, ImageListener listener);
    void imageLoaded(URL url);
}
