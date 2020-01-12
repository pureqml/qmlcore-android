package com.pureqml.android;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.annotation.Nullable;
import android.util.Log;
import android.util.LruCache;

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
        public Bitmap getBitmap() {
            synchronized (_cache) {
                ImageHolder holder = _cache.get(_url);
                if (holder != null) {
                    return holder.getBitmap();
                } else {
                    holder = new ImageHolder(_url);
                    _cache.put(_url, holder);
                }
                return holder.getBitmap();
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
            Bitmap bitmap = null;
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
                BufferedInputStream stream = new BufferedInputStream(rawStream);
                bitmap = BitmapFactory.decodeStream(stream);
            } catch(Exception ex) {
                Log.e(TAG, "image loading failed", ex);
            } finally {
                synchronized (_cache) {
                    _cache.remove(_url);
                    _holder.setBitmap(bitmap);
                    _env.onImageLoaded(_url);
                    _cache.put(_url, _holder);
                }
            }
        }
    }

    private class ImageHolder {
        URL     _url;
        Bitmap  _image;

        ImageHolder(URL url) {
            _url = url;
            _threadPool.execute(new ImageLoaderTask(url, this));
        }

        @Nullable
        synchronized public Bitmap getBitmap() {
            return _image;
        }

        synchronized public void setBitmap(Bitmap bitmap) {
            _image = bitmap;
        }

        synchronized int byteCount() { return _url.toString().length() + (_image != null? _image.getByteCount(): 0); }
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
            if (holder != null && holder.getBitmap() != null) {
                callback.onImageLoaded(holder._url);
            }
        }
        return new ImageResource(url);
    }

}
