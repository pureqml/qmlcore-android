package com.pureqml.android.runtime;

import android.util.Log;

import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.pureqml.android.IExecutionEnvironment;
import com.pureqml.android.ImageListener;
import com.pureqml.android.ImageLoader;

import java.net.MalformedURLException;
import java.net.URL;

public class Image extends Element implements ImageListener {
    private final static String TAG = "rt.Image";
    URL                         _url;
    ImageLoader.ImageResource   _image;
    V8Function                  _callback;

    public Image(IExecutionEnvironment env) {
        super(env);
    }

    public void load(String name, V8Function callback) {
        if (name.indexOf("://") < 0)
            name = "file:///android_asset/" + name;
        _url = null;
        try {
            _url = new URL(name);
        } catch (MalformedURLException e) {
            Log.e(TAG, "invalid url", e);
            V8 v8 = _env.getRuntime();

            V8Array args = new V8Array(v8);
            args.push((Object)null);
            callback.call(null, args); //indicate error
            return;
        }
        //Log.v(TAG, "loading " + url);
        _image = _env.loadImage(_url, this);
        _image.getBitmap();
        _callback = callback;
    }

    protected void setStyle(String name, Object value) {
        super.setStyle(name, value);
    }

    @Override
    public void onImageLoaded(URL url) {
        if (url.equals(_url))
            update();
    }
}
