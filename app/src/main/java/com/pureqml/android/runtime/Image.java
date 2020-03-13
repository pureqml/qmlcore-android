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
import com.pureqml.android.TypeConverter;

import java.net.MalformedURLException;
import java.net.URL;

public final class Image extends Element implements ImageLoadedCallback {
    private final static String TAG = "rt.Image";
    URL                         _url;
    ImageLoader.ImageResource   _image;
    V8Function                  _callback;
    Paint                       _paint;
    int                         _fixedWidth = -1;
    int                         _fixedHeight = -1;

    enum Position { Left, Center, Right };

    Position                    _positionX, _positionY;
    boolean                     _repeatX, _repeatY;

    public Image(IExecutionEnvironment env) {
        super(env);
        _paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
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

    private static final String regexWS = "\\s+";

    private final Position parseBackgroundPosition(String value) {
        switch(value) {
            case "left":
                return Position.Left;
            case "right":
                return Position.Right;
            case "center":
                return Position.Center;
            default:
                Log.w(TAG, "invalid position: " + value);
                return Position.Center;
        }
    }

    private final int parseBackgroundSize(String value) {
        switch(value) {
            case "100%":
                return -1;
            default:
                try {
                    return TypeConverter.toFontSize(value, _env.getDisplayMetrics());
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.w(TAG, "parsing background size failed: ", e);
                    return -1;
                }
        }
    }

    @Override
    protected void setStyle(String name, Object value) {
        switch(name) {
            case "image-rendering":
                _paint.setFilterBitmap(!value.equals("pixelated"));
                break;
            case "background-image":
                break;
            case "background-position-x":
                _positionX = parseBackgroundPosition(value.toString());
                break;
            case "background-position-y":
                _positionY = parseBackgroundPosition(value.toString());
                break;
            case "background-size": {
                String[] size = value.toString().split(regexWS);
                if (size.length == 1)
                    _fixedWidth = _fixedHeight = parseBackgroundSize(value.toString());
                else if (size.length >= 2) {
                    _fixedWidth = parseBackgroundSize(size[0]);
                    _fixedHeight = parseBackgroundSize(size[1]);
                    if (size.length > 2)
                        Log.w(TAG, "skipping background-size tail " + value);
                } else
                    Log.w(TAG, "malformed background-size: " + value);
                break;
            }
            case "background-repeat": {
                String repeat = value.toString();
                switch (repeat) {
                    case "no-repeat":
                        _repeatX = _repeatY = false;
                        break;
                    default:
                        String[] size = value.toString().split(regexWS);
                        for (int i = 0; i < size.length; ++i) {
                            switch(size[i]) {
                                case "repeat-x":
                                    _repeatX = true;
                                    break;
                                case "repeat-y":
                                    _repeatY = true;
                                    break;
                                default:
                                    Log.w(TAG, "Unhandled background-repeat value " + size[i]);
                            }
                        }
                }
                break;
            }
            default:
                super.setStyle(name, value);
                return;
        }
        update();
    }

    @Override
    public void onImageLoaded(URL url) {
        if (url.equals(_url)) {
            Bitmap bitmap = _image.getBitmap();
            V8Array args = new V8Array(_env.getRuntime());
            try {
                if (bitmap != null) {
                    V8Object metrics = new V8Object(_env.getRuntime());
                    metrics.add("width", bitmap.getWidth());
                    metrics.add("height", bitmap.getHeight());
                    args.push(metrics);

                    _env.invokeVoidCallback(_callback, null, args);
                    metrics.close();
                } else {
                    args.push((Object)null);
                    _env.invokeVoidCallback(_callback, null, args);
                }
            } finally {
                args.close();
                update();
            }

        }
    }

    @Override
    public void paint(PaintState state) {
        beginPaint();

        if (_image != null) {
            Bitmap bitmap = _image.getBitmap();
            if (bitmap != null) {
                Rect src = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
                Rect dst = getDstRect(state);
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
