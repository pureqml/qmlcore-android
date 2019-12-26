package com.pureqml.android.runtime;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

import com.eclipsesource.v8.Releasable;
import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.pureqml.android.IExecutionEnvironment;
import com.pureqml.android.ImageLoadedCallback;
import com.pureqml.android.ImageLoader;

import java.net.MalformedURLException;
import java.net.URL;

public class Image extends Element implements ImageLoadedCallback {
    private final static String TAG = "rt.Image";
    URL                         _url;
    ImageLoader.ImageResource   _image;
    V8Function                  _callback;
    Paint                       _paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public Image(IExecutionEnvironment env) {
        super(env);
    }

    public void load(String name, V8Function callback) {
        if (name.indexOf("://") < 0)
            name = "file:///" + name;
        _url = null;
        try {
            _url = new URL(name);
        } catch (MalformedURLException e) {
            Log.e(TAG, "invalid url", e);
            V8 v8 = _env.getRuntime();

            V8Array args = new V8Array(v8);
            args.push((Object)null);
            Object r = callback.call(null, args); //indicate error
            if (r instanceof Releasable)
                ((Releasable)r).release();
            args.close();
            return;
        }
        //Log.v(TAG, "loading " + url);
        _image = _env.loadImage(_url, this);
        _image.getBitmap();
        _callback = callback;
    }

    protected void setStyle(String name, Object value) throws Exception {
        super.setStyle(name, value);
    }

    @Override
    public void onImageLoaded(URL url) {
        if (url.equals(_url)) {
            Bitmap bitmap = _image.getBitmap();
            if (bitmap != null) {
                V8Array args = new V8Array(_env.getRuntime());
                V8Object metrics = new V8Object(_env.getRuntime());
                metrics.add("width", bitmap.getWidth());
                metrics.add("height", bitmap.getHeight());
                args.push(metrics);

                _env.invokeVoidCallback(_callback, null, args);
                args.close();
                metrics.close();
            }

            update();
        }
    }

    @Override
    public void paint(PaintState state) {
        beginPaint();
        if (_visible) {
            if (_image != null) {
                Bitmap bitmap = _image.getBitmap();
                if (bitmap != null && state.visible()) {
                    Rect src = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
                    Rect dst = translateRect(_rect, state.baseX, state.baseY);
                    //Log.i(TAG, "drawing image "  + src + " " + dst + " " + dst.width() + "x" + dst.height());
                    state.canvas.drawBitmap(bitmap, src, dst, patchAlpha(_paint, state.opacity));
                    _lastRect.set(dst);
                }
            }
            paintChildren(state);
        }
        endPaint();
    }
}
