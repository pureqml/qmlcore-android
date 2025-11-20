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
import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;

public final class ImageLoader {
    public static final String TAG = "ImageLoader";
    public static final int CacheSize = 64 * 1024 * 1024;

    private final IExecutionEnvironment   _env;
    private final ExecutorService         _threadPool;

    private final HashMap<URI, CallbackHolder> _callbacks = new HashMap<>();
    private final LruCache<URI, ImageHolder> _cache = new LruCache<URI, ImageHolder>(CacheSize) {
        @Override
        protected int sizeOf(URI key, ImageHolder value) {
            return value.byteCount();
        }
    };

    public ImageLoader(IExecutionEnvironment env) {
        _env = env;
        _threadPool = env.getThreadPool();
    }

    private ImageHolder getHolder(URI url) {
        synchronized (_cache) {
            ImageHolder holder = _cache.get(url);
            if (holder == null) {
                String stringUrl = url.toString();
                String svgFileFormat = "svg";
                if (stringUrl.contains(".") && svgFileFormat.equalsIgnoreCase(stringUrl.substring(stringUrl.lastIndexOf(".") + 1))) {
                    holder = new ImageVectorHolder(url);
                } else {
                    holder = new ImageStaticHolder(url);
                }
                _cache.put(url, holder);
                Log.v(TAG, "cache size: " + _cache.size());
                _threadPool.execute(new ImageLoaderTask(url, holder));
            }
            return holder;
        }
    }

    private static class CallbackHolder {
        private final HashSet<WeakReference<ImageLoadedCallback>> _callbacks = new HashSet<>();
        public void subscribe(ImageLoadedCallback callback) {
            synchronized (_callbacks) {
                _callbacks.add(new WeakReference<>(callback));
            }
        }
        public void unsubscribe(ImageLoadedCallback callback) {
            synchronized (_callbacks) {
                Iterator<WeakReference<ImageLoadedCallback>> it = _callbacks.iterator();
                while (it.hasNext()) {
                    ImageLoadedCallback el = it.next().get();
                    if (el == null || el == callback)
                        it.remove();
                }
            }
        }
        void onImageLoaded(URI uri, Bitmap bitmap) {
            LinkedList<ImageLoadedCallback> callbacks = new LinkedList<>();
            synchronized (_callbacks) {
                Iterator<WeakReference<ImageLoadedCallback>> it = _callbacks.iterator();
                while(it.hasNext()) {
                    ImageLoadedCallback el = it.next().get();
                    if (el != null) {
                        callbacks.push(el);
                    } else
                        it.remove();
                }
            }
            for(ImageLoadedCallback callback : callbacks) {
                try {
                    callback.onImageLoaded(uri, bitmap);
                } catch (Exception ex) {
                    Log.w(TAG, "onImageLoaded " + uri + " failed", ex);
                }
            }
        }
    };

    private CallbackHolder getCallbackHolder(URI url) {
        synchronized (_callbacks) {
            return _callbacks.get(url);
        }
    }
    private CallbackHolder createCallbackHolder(URI url) {
        synchronized (_callbacks) {
            CallbackHolder holder = _callbacks.get(url);
            if (holder == null) {
                holder = new CallbackHolder();
                _callbacks.put(url, holder);
            }
            return holder;
        }
    }

    public void subscribe(URI url, ImageLoadedCallback callback) {
        createCallbackHolder(url).subscribe(callback);
    }
    public void unsubscribe(URI url, ImageLoadedCallback callback) {
        CallbackHolder holder = getCallbackHolder(url);
        if (holder != null)
            holder.unsubscribe(callback);
    }

    public Bitmap getBitmap(URI url) {
        ImageHolder holder = getHolder(url);
        return holder.getBitmap();
    }

    private class ImageLoaderTask extends SafeRunnable {
        final URI             _url;
        final ImageHolder     _holder; //fixme: make me weak

        public ImageLoaderTask(URI url, ImageHolder holder) {
            _url = url;
            _holder = holder;
        }

        @Nullable
        private Bitmap getNotifyBitmap() {
            Bitmap bitmap = null;
            try {
                bitmap = _holder.getBitmap();
            } catch(Exception ex) {
                Log.e(TAG, "getBitmap", ex);
            }
            return bitmap;
        }

        @Override
        public void doRun() {
            Log.i(TAG, "starting loading task on " + _url);
            try {
                InputStream rawStream;
                if (_url.getScheme().equals("file")) {
                    String path = _url.getPath();
                    int pos = 0;
                    while(pos < path.length() && path.charAt(pos) == '/')
                        ++pos;
                    rawStream = _env.getAssets().open(path.substring(pos)); //strip leading slash
                } else
                    rawStream = _url.toURL().openStream();
                try {
                    _holder.load(rawStream);
                } finally {
                    rawStream.close();
                }
            } catch(Exception ex) {
                Log.e(TAG, "image loading failed", ex);
            } finally {
                Bitmap bitmap;
                synchronized (_cache) {
                    _cache.remove(_url);
                    _holder.finish();
                    bitmap = getNotifyBitmap();
                    _cache.put(_url, _holder);
                }
                getCallbackHolder(_url).onImageLoaded(_url, bitmap);
                Log.v(TAG, "cache size: " + _cache.size());
            }
            Log.i(TAG, "finished loading task on " + _url);
        }
    }

    private interface ImageHolder {
        void load(InputStream stream);
        Bitmap getBitmap();

        int byteCount(); //LRUCache API

        void finish();
    }

    private static abstract class BaseImageHolder implements ImageHolder
    {
        protected final URI                 _url;
        private boolean                     _finished;
        protected Bitmap                    _image;

        BaseImageHolder(URI url) {
            _url = url;
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
        }

        @Override
        public synchronized int byteCount() {
            return _url.toString().length() * 4 + (_finished && _image != null? _image.getByteCount(): 0);
        }
    }

    private static class ImageStaticHolder extends BaseImageHolder {
        ImageStaticHolder(URI url) {
            super(url);
        }

        @Override
        public void load(InputStream stream) {
            _image = BitmapFactory.decodeStream(new BufferedInputStream(stream));
        }

        @Nullable
        @Override
        public Bitmap getBitmap() {
            return _image;
        }
    }

    private static class ImageVectorHolder extends BaseImageHolder {
        SVG     _svg;

        ImageVectorHolder(URI url) {
            super(url);
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
        public Bitmap getBitmap() {
            synchronized (this) {
                if (_svg == null)
                    return null;

                if (_image != null)
                    return _image;
            }

            int documentWidth = (int)Math.ceil(_svg.getDocumentWidth());
            int documentHeight = (int)Math.ceil(_svg.getDocumentHeight());
            PictureDrawable pd = new PictureDrawable(_svg.renderToPicture(documentWidth, documentHeight));
            Bitmap bitmap = Bitmap.createBitmap(documentWidth, documentHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            _svg.renderToCanvas(canvas);
            synchronized (this) {
                _image = bitmap;
                return _image;
            }
        }
    }
}
