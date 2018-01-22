package com.pureqml.android;

import com.eclipsesource.v8.V8;
import com.pureqml.android.runtime.Element;

import java.net.URL;
import java.util.concurrent.Executor;

public interface IExecutionEnvironment {
    V8 getRuntime();

    Element getElementById(long id);

    void putElement(long id, Element element);

    void removeElement(long id);

    Executor getExecutor();

    //image loader api
    ImageLoader.ImageResource loadImage(URL url, ImageListener listener);
    void imageLoaded(URL url);
}
