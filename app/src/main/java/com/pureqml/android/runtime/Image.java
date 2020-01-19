package com.pureqml.android.runtime;

import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
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

public final class Image extends Element implements ImageLoadedCallback {
    private final static String TAG = "rt.Image";
    URL                         _url;
    ImageLoader.ImageResource   _image;
    V8Function                  _callback;
    Paint                       _paint;

    public Image(IExecutionEnvironment env) {
        super(env);
        _paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        _paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));
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

    @Override
    protected void setStyle(String name, Object value) {
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

        if (_image != null) {
            Bitmap bitmap = _image.getBitmap();
            if (bitmap != null) {
                Rect src = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
                Rect dst = translateRect(_rect, state.baseX, state.baseY);
                Paint paint = patchAlpha(_paint, 255, state.opacity);
                //Log.i(TAG, "drawing image "  + src + " " + dst + " " + dst.width() + "x" + dst.height());
                if (paint != null) {
                    state.canvas.drawBitmap(bitmap, src, dst, paint);
                    _lastRect.set(dst);
                }
            }
        }
        paintChildren(state);

        endPaint();
    }
}
