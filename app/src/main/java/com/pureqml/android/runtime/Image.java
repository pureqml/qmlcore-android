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
import com.pureqml.android.SafeRunnable;
import com.pureqml.android.TypeConverter;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.Executor;

public final class Image extends Element implements ImageLoadedCallback {
    private final static String TAG = "rt.Image";
    URL                         _url;
    V8Function                  _callback;
    final Paint                       _paint;

    private enum Position { LeftOrTop, Center, RightOrBottom }

    private enum Mode { Percentage, Absolute, Cover, Contain }

    static int getPosition(Position position, int imageSize, int rectSize) {
        switch(position) {
            case RightOrBottom:
                return rectSize - imageSize;
            case Center:
                return (rectSize - imageSize) / 2;
            default:
                return 0;
        }
    }

    private final class Background {
        Mode        mode        = Mode.Absolute;
        Position    position    = Position.LeftOrTop;
        int         percentage  = 100;
        int         size        = 0;

        public boolean repeat   = false;

        public void setPosition(String value) {
            switch(value) {
                case "left":
                    position = Position.LeftOrTop;
                    break;
                case "right":
                    position = Position.RightOrBottom;
                    break;
                case "center":
                    position = Position.Center;
                    break;
                default:
                    Log.w(TAG, "invalid position: " + value);
                    position = Position.LeftOrTop;
                    break;
            }
        }

        public void resetSize() {
            mode = Mode.Percentage;
            percentage = 100;
        }

        public void resetPosition() {
            position = Position.LeftOrTop;
        }

        public void setBackgroundSize(String value) {
            if (value.endsWith("%")) {
                mode = Mode.Percentage;
                percentage = Integer.valueOf(value.substring(0, value.length() - 1), 10);
                return;
            }

            switch(value) {
                case "auto":
                    resetSize();
                    break;
                case "cover":
                    mode = Mode.Cover;
                    break;
                case "contain":
                    mode = Mode.Contain;
                    break;
                default:
                    try {
                        size = TypeConverter.toFontSize(value, _env.getRenderer().getDisplayMetrics());
                        mode = Mode.Absolute;
                    } catch (Exception e) {
                        resetSize();
                        e.printStackTrace();
                        Log.w(TAG, "parsing background size failed: ", e);
                    }
            }
        }

        int getPosition(int imageSize, int rectSize) {
            return Image.getPosition(position, imageSize, rectSize);
        }

        public boolean needClip(Background y) {
            return repeat || y.repeat || mode == Mode.Cover;
        }

        public void merge(Background y, Rect dst, Rect src) {
            //Log.v(TAG, "merge in " + mode + " " + dst + " ← " + src);
            float aspect;
            float wx, hx;
            final int dstWidth = dst.width(), dstHeight = dst.height();
            final int srcWidth = src.width(), srcHeight = src.height();
            int dx = 0, dy = 0;

            switch(mode) {
                case Percentage:
                    dx = dstWidth - (dstWidth * percentage) / 100;
                    dy = dstHeight - (dstHeight * y.percentage) / 100;
                    dst.left += dx / 2;
                    dst.right -= (dx - dx / 2);
                    dst.top += dy / 2;
                    dst.bottom -= (dy - dy / 2);
                    break;
                case Absolute:
                    dst.right = dst.left + size;
                    dst.bottom = dst.top + y.size;
                    break;
                case Contain:
                case Cover:
                    wx = 1.0f * dstWidth / srcWidth;
                    hx = 1.0f * dstHeight / srcHeight;
                    float x = mode == Mode.Contain? Math.min(wx, hx): Math.max(wx, hx);
                    dx = dstWidth - (int)(srcWidth * x);
                    dy = dstHeight - (int)(srcHeight * x);
                    dst.left += dx / 2;
                    dst.right -= (dx - dx / 2);
                    dst.top += dy / 2;
                    dst.bottom -= (dy - dy / 2);
                    break;
                default:
                    break;
            }
            //Log.v(TAG, "merge out " + mode + " " + dst + " ← " + src);
        }
    }
    final Background                  _backgroundX = new Background();
    final Background                  _backgroundY = new Background();

    public Image(IExecutionEnvironment env) {
        super(env);
        _paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        _paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));
    }

    public void load(String name, V8Function callback) {
        if (!name.contains("://"))
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
        ImageLoader loader = _env.getImageLoader();
        loader.load(_url, this);
        _callback = callback;
    }

    private static final String regexWS = "\\s+";

    @Override
    protected void setStyle(String name, Object value) {
        switch(name) {
            case "image-rendering":
                _paint.setFilterBitmap(!value.equals("pixelated"));
                break;
            case "background-image":
                break;
            case "background-position-x":
                _backgroundX.setPosition(value.toString());
                break;
            case "background-position-y":
                _backgroundY.setPosition(value.toString());
                break;
            case "background-size": {
                String[] size = value.toString().split(regexWS);
                if (size.length == 1) {
                    _backgroundX.setBackgroundSize(value.toString());
                    _backgroundY.setBackgroundSize(value.toString());
                } else if (size.length >= 2) {
                    _backgroundX.setBackgroundSize(size[0]);
                    _backgroundY.setBackgroundSize(size[1]);
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
                        _backgroundX.repeat = _backgroundY.repeat = false;
                        break;
                    default:
                        String[] size = value.toString().split(regexWS);
                        for (String s : size) {
                            switch (s) {
                                case "repeat-x":
                                    _backgroundX.repeat = true;
                                    break;
                                case "repeat-y":
                                    _backgroundY.repeat = true;
                                    break;
                                default:
                                    Log.w(TAG, "Unhandled background-repeat value " + s);
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
    public void onImageLoaded(URL url, final Bitmap bitmap) {
        Executor executor = _env.getExecutor();
        if (executor == null) {
            Log.d(TAG, "skipping callback, executor is dead");
            return;
        }
        executor.execute(new SafeRunnable() {
            @Override
            public void doRun() {
                Log.v(TAG, "on image loaded " + url + ", current url: " + _url);
                if (!url.equals(_url))
                    return;

                Log.v(TAG, "image bitmap: " + _url + " -> " + bitmap);
                V8Array args = new V8Array(_env.getRuntime());
                try {
                    if (bitmap != null) {
                        V8Object metrics = new V8Object(_env.getRuntime());
                        metrics.add("width", bitmap.getWidth());
                        metrics.add("height", bitmap.getHeight());
                        args.push(metrics);

                        try { _env.invokeVoidCallback(_callback, null, args); }
                        catch (Exception ex) { Log.w(TAG, "callback failed: ", ex); }
                        metrics.close();
                    } else {
                        args.push((Object)null);
                        try { _env.invokeVoidCallback(_callback, null, args); }
                        catch (Exception ex) { Log.w(TAG, "callback failed: ", ex); }
                    }
                } finally {
                    args.close();
                    update();
                }
            }
        });
    }

    @Override
    public void paint(PaintState state) {
        beginPaint(state);

        if (_url != null) {
            Rect dst = getDstRect(state);
            Bitmap bitmap = null;

            try {
                bitmap = _env.getImageLoader().getBitmap(_url, dst.width(), dst.height());
            } catch(Exception ex) {
                Log.w(TAG, "image loading failed", ex);
            }

            if (bitmap != null) {
                _paint.setXfermode(new PorterDuffXfermode(state.roundClipWorkaround? PorterDuff.Mode.SRC_IN: PorterDuff.Mode.SRC_OVER));

                Paint paint = patchAlpha(_paint, 255, state.opacity);
                if (paint != null) {
                    boolean clip = _backgroundX.needClip(_backgroundY);
                    boolean doPaint = true;
                    if (clip) {
                        state.canvas.save();
                        if (!state.canvas.clipRect(dst))
                            doPaint = false;
                    }

                    if (doPaint) {
                        Rect src = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
                        _backgroundX.merge(_backgroundY, dst, src);
                        state.canvas.drawBitmap(bitmap, src, dst, paint);
                        _lastRect.set(dst);
                    }

                    if (clip)
                        state.canvas.restore();
                }
            } else
                Log.d(TAG, "null bitmap returned for " + _url);
        }
        paintChildren(state);

        endPaint();
    }
}
