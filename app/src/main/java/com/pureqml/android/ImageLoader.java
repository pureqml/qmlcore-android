package com.pureqml.android;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.PictureDrawable;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.LruCache;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.ExecutorService;

public final class ImageLoader {
    public static final String TAG = "ImageLoader";
    public static final int CacheSize = 64 * 1024 * 1024;

    public class ImageResource {
        private URL     _url;

        ImageResource(URL url) { _url = url; }

        @Nullable
        public void createLoader() {
            synchronized (_cache) {
                ImageHolder holder = _cache.get(_url);
                if (holder == null) {
                    String stringUrl = _url.toString();
                    String svgFileFormat = "svg";
                    if (stringUrl.contains(".") && svgFileFormat.equalsIgnoreCase(stringUrl.substring(stringUrl.lastIndexOf(".") + 1))) {
                        holder = new ImageVectorHolder(_url);
                    } else {
                        holder = new ImageStaticHolder(_url);
                    }
                    _cache.put(_url, holder);
                }
            }
        }

        @Nullable
        public Bitmap getBitmap(int w, int h) {
            synchronized (_cache) {
                ImageHolder holder = _cache.get(_url);
                return holder.getBitmap(w, h);
            }
        }
    }

    private class ImageLoaderTask implements Runnable {
        URL             _url;
        ImageHolder     _holder; //fixme: make me weak

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
                synchronized (_cache) {
                    _cache.remove(_url);
                    _env.onImageLoaded(_url);
                    _cache.put(_url, _holder);
                }
            }
        }
    }

    private interface ImageHolder {
        void load(InputStream stream);
        Bitmap getBitmap(int w, int h);
        boolean isLoaded();
        URL getUrl();
        int byteCount();
    }

    private class ImageStaticHolder implements ImageHolder {
        URL     _url;
        Bitmap  _image;
        boolean loaded = false;

        ImageStaticHolder(URL url) {
            _url = url;
            _threadPool.execute(new ImageLoaderTask(url, this));
        }

        synchronized public void load(InputStream stream) {
            _image = BitmapFactory.decodeStream(new BufferedInputStream(stream));
        }

        @Nullable
        synchronized public Bitmap getBitmap(int w, int h) {
            return _image;
        }

        synchronized public void setContent(Bitmap bitmap) {
            _image = bitmap;
            loaded = true;
        }

        synchronized public int byteCount() { return _url.toString().length() + (_image != null? _image.getByteCount(): 0); }

        synchronized public boolean isLoaded() { return loaded; }

        synchronized public URL getUrl() { return _url; }
    }

    private class ImageVectorHolder implements ImageHolder {
        URL     _url;
        Bitmap  _image;
        SVG _svg;
        boolean loaded = false;

        ImageVectorHolder(URL url) {
            _url = url;
            _threadPool.execute(new ImageLoaderTask(url, this));
        }

        public synchronized void load(InputStream stream) {
            try {
                _svg = SVG.getFromInputStream(stream);
            } catch (SVGParseException e) {
                Log.e(TAG, "loading vector image failed", e);
                _svg = null;
            }
        }

        @Nullable
        synchronized public Bitmap getBitmap(int w, int h) {
            if (w == 0 || h == 0 || _svg == null) {
                return null;
            }
            PictureDrawable pd = new PictureDrawable(_svg.renderToPicture(w, h));
            Bitmap bitmap = Bitmap.createBitmap(pd.getIntrinsicWidth(), pd.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawPicture(pd.getPicture());
            _image = bitmap;
            return _image;
        }

        synchronized public void setContent(SVG svg) {
            _svg = svg;
            loaded = true;
        }

        synchronized public int byteCount() { return _url.toString().length() + (_image != null? _image.getByteCount(): 0); }

        synchronized public boolean isLoaded() { return loaded; }

        synchronized public URL getUrl() { return _url; }
    }

    private IExecutionEnvironment   _env;
    private ExecutorService         _threadPool;

    private LruCache<URL, ImageHolder> _cache = new LruCache<URL, ImageHolder>(CacheSize) {
        @Override
        protected int sizeOf(URL key, ImageHolder value) {
            return value.byteCount();
        }
    };

    public ImageLoader(IExecutionEnvironment env) {
        _env = env;
        _threadPool = env.getThreadPool();
    }

    public ImageResource load(URL url, ImageLoadedCallback callback) {
        synchronized (_cache) {
            ImageHolder holder = _cache.get(url);
            if (holder != null && holder.isLoaded()) {
                callback.onImageLoaded(holder.getUrl());
            }
        }
        return new ImageResource(url);
    }

}
