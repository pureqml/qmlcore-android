package com.pureqml.android;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.PictureDrawable;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.Nullable;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public final class ImageLoader {
    public static final String TAG = "ImageLoader";
    public static final int CacheSize = 64 * 1024 * 1024;

    private final IExecutionEnvironment   _env;
    private final ExecutorService         _threadPool;

    private final LruCache<URL, ImageHolder> _cache = new LruCache<URL, ImageHolder>(CacheSize) {
        @Override
        protected int sizeOf(URL key, ImageHolder value) {
            return value.byteCount();
        }
    };

    public ImageLoader(IExecutionEnvironment env) {
        _env = env;
        _threadPool = env.getThreadPool();
    }

    private ImageHolder getHolder(URL url, int hintWidth, int hintHeight) {
        synchronized (_cache) {
            ImageHolder holder = _cache.get(url);
            if (holder == null) {
                String stringUrl = url.toString();
                String svgFileFormat = "svg";
                if (stringUrl.contains(".") && svgFileFormat.equalsIgnoreCase(stringUrl.substring(stringUrl.lastIndexOf(".") + 1))) {
                    holder = new ImageVectorHolder(url, hintWidth, hintHeight);
                } else {
                    holder = new ImageStaticHolder(url);
                }
                _cache.put(url, holder);
                _threadPool.execute(new ImageLoaderTask(url, holder));
            }
            return holder;
        }
    }

    public void load(URL url, ImageLoadedCallback callback, int hintWidth, int hintHeight) {
        ImageHolder holder = getHolder(url, hintWidth, hintHeight);
        holder.notify(callback);
    }

    public Bitmap getBitmap(URL url, int w, int h) {
        ImageHolder holder = getHolder(url, w, h);
        return holder.getBitmap(w, h);
    }

    private class ImageLoaderTask implements Runnable {
        final URL             _url;
        final ImageHolder     _holder; //fixme: make me weak

        public ImageLoaderTask(URL url, ImageHolder holder) {
            _url = url;
            _holder = holder;
        }

        @Override
        public void run() {
            Log.i(TAG, "starting loading task on " + _url);
            try {
                InputStream rawStream = null;
                if (_url.getProtocol().equals("file")) {
                    String path = _url.getPath();
                    int pos = 0;
                    while(pos < path.length() && path.charAt(pos) == '/')
                        ++pos;
                    rawStream = _env.getAssets().open(path.substring(pos)); //strip leading slash
                } else
                    rawStream = _url.openStream();

                _holder.load(rawStream);
            } catch(Exception ex) {
                Log.e(TAG, "image loading failed", ex);
            } finally {
                _holder.finish();
                _cache.put(_url, _holder);
            }
            Log.i(TAG, "finished loading task on " + _url);
        }
    }

    private interface ImageHolder {
        URL getUrl();

        void load(InputStream stream);
        Bitmap getBitmap(int w, int h);

        int byteCount(); //LRUCache API

        void finish();
        void notify(ImageLoadedCallback callback);
    }

    private abstract static class BaseImageHolder implements ImageHolder
    {
        protected final URL                 _url;
        private boolean                     _finished;
        private List<ImageLoadedCallback>   _callbacks;

        BaseImageHolder(URL url) {
            _url = url;
            _callbacks = new LinkedList<>();
        }

        @Override
        public URL getUrl() { return _url; }

        @Override
        public void notify(ImageLoadedCallback callback) {
            boolean finished;
            synchronized (this) {
                finished = _finished;
                if (!finished) {
                    _callbacks.add(callback);
                }
            }
            if (finished)
                callback.onImageLoaded(_url, getNotifyBitmap());
        }

        @Nullable
        Bitmap getNotifyBitmap() {
            Bitmap bitmap = null;
            try {
                bitmap = getBitmap(0, 0);
            } catch(Exception ex) {
                Log.e(TAG, "getBitmap", ex);
            }
            return bitmap;
        }

        @Override
        public void finish() {
            synchronized (this) {
                if (_finished) {
                    Log.e(TAG, "double finish");
                    return;
                }
                _finished = true;
            }

            for(ImageLoadedCallback callback : _callbacks) {
                try {
                    callback.onImageLoaded(_url, getNotifyBitmap());
                } catch(Exception ex) {
                    Log.e(TAG, "onImageLoaded failed: ", ex);
                }
            }
            _callbacks = null;
        }
    }

    private static class ImageStaticHolder extends BaseImageHolder {
        Bitmap  _image = null;

        ImageStaticHolder(URL url) {
            super(url);
        }

        @Override
        public void load(InputStream stream) {
            _image = BitmapFactory.decodeStream(new BufferedInputStream(stream));
        }

        @Nullable
        @Override
        public Bitmap getBitmap(int w, int h) {
            return _image;
        }

        @Override
        public synchronized int byteCount() {
            return _url.toString().length() + (_image != null? _image.getByteCount(): 0);
        }
    }

    private static class ImageVectorHolder extends BaseImageHolder {
        Bitmap  _image;
        SVG     _svg;
        int     _lastWidth;
        int     _lastHeight;
        static final int DEFAULT_WIDTH  = 128;
        static final int DEFAULT_HEIGHT = 128;

        ImageVectorHolder(URL url, int hintWidth, int hintHeight) {
            super(url);
            _lastWidth = hintWidth > 0? hintWidth : DEFAULT_WIDTH;
            _lastHeight = hintHeight > 0? hintHeight: DEFAULT_HEIGHT;
        }

        @Override
        public void load(InputStream stream) {
            try {
                _svg = SVG.getFromInputStream(stream);
            } catch (SVGParseException e) {
                Log.e(TAG, "loading vector image failed", e);
            }
        }

        @Nullable
        @Override
        public Bitmap getBitmap(int w, int h) {
            synchronized (this) {
                if (_svg == null) {
                    return null;
                }

                if (w == 0 && h == 0) { //"notify" bitmap
                    w = _lastWidth;
                    h = _lastHeight;
                }

                if (_image != null && w == _lastWidth && h == _lastHeight) {
                    return _image;
                }
            }

            PictureDrawable pd = new PictureDrawable(_svg.renderToPicture(w, h));
            Bitmap bitmap = Bitmap.createBitmap(pd.getIntrinsicWidth(), pd.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawPicture(pd.getPicture());
            synchronized (this) {
                _image = bitmap;
                _lastWidth = w;
                _lastHeight = h;
                return _image;
            }
        }

        @Override
        public synchronized int byteCount() {
            return _url.toString().length() + (_image != null? _image.getByteCount(): 0);
        }
    }
}
