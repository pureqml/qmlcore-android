package com.pureqml.android;

import android.app.Service;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import com.eclipsesource.v8.Releasable;
import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.pureqml.android.runtime.Console;
import com.pureqml.android.runtime.Element;
import com.pureqml.android.runtime.Image;
import com.pureqml.android.runtime.Rectangle;
import com.pureqml.android.runtime.Text;
import com.pureqml.android.runtime.Timers;
import com.pureqml.android.runtime.Wrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ExecutionEnvironment extends Service implements IExecutionEnvironment {
    public static final String TAG = "ExecutionEnvironment";

    public class LocalBinder extends Binder {
        ExecutionEnvironment getService() {
            return ExecutionEnvironment.this;
        }
    }
    private final IBinder _binder = new LocalBinder();
    private V8 _v8;

    //Element collection
    private Map<Long, Element>           _elements = new HashMap<Long, Element>(10000);
    private HashMap<URL, List<ImageLoadedCallback>>
                                        _imageWaiters = new HashMap<>();
    private Rect                        _surfaceGeometry;
    private V8Object                    _rootObject;
    private Element                     _rootElement;
    private V8Object                    _exports;
    private ExecutorService             _executor;
    private Timers                      _timers;
    private ExecutorService             _threadPool;
    private ImageLoader                 _imageLoader;
    private TextRenderer                _textRenderer;
    private IRenderer                   _renderer;
    private DisplayMetrics              _metrics;

    public ExecutionEnvironment() {
        Log.i(TAG, "starting execution environment thread...");
        _executor = Executors.newSingleThreadExecutor();
        _executor.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                ExecutionEnvironment.this.start();
                return null;
            }
        });
    }

    synchronized void setRenderer(IRenderer renderer) {
        Log.v(TAG, "setRenderer " + renderer);
        _renderer = renderer;
        if (renderer != null)
            renderer.invalidateRect(null); //fullscreen update
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return _binder;
    }

    void registerRuntime() {
        _timers = new Timers(this);
        V8Object v8Console = new V8Object(_v8);
        _v8.add("console", v8Console);
        v8Console.registerJavaMethod(new Console.LogMethod(), "log");
        v8Console.release();

        V8Object v8FD = new V8Object(_v8);
        _v8.add("fd", v8FD);

        V8Object elementProto   = Wrapper.generateClass(this, _v8, v8FD, "Element", Element.class, new Class<?>[] { IExecutionEnvironment.class });
        V8Object rectangleProto = Wrapper.generateClass(this, _v8, v8FD, "Rectangle", Rectangle.class, new Class<?>[] { IExecutionEnvironment.class });
        rectangleProto.setPrototype(elementProto);
        V8Object imageProto     = Wrapper.generateClass(this, _v8, v8FD, "Image", Image.class, new Class<?>[] { IExecutionEnvironment.class });
        imageProto.setPrototype(elementProto);
        V8Object textProto      = Wrapper.generateClass(this, _v8, v8FD, "Text", Text.class, new Class<?>[] { IExecutionEnvironment.class });
        textProto.setPrototype(elementProto);

        v8FD.release();

        V8Object v8Module = new V8Object(_v8);
        _v8.add("module", v8Module);
        v8Module.release();
    }

    private static final String readScript(InputStream input) throws IOException {
            final int BufferSize = 128 * 1024;

            final byte[] buffer = new byte[BufferSize];
            final ByteArrayOutputStream scriptStream = new ByteArrayOutputStream();
            int r;
            do {
                r = input.read(buffer);
                if (r <= 0)
                    break;
                scriptStream.write(buffer, 0, r);
            } while(r == BufferSize);
            Log.v(TAG, "read " + scriptStream.size() + " bytes...");
            return scriptStream.toString();
    }

    private void start() {
        Log.i(TAG, "starting execution environment...");
        _metrics = getBaseContext().getResources().getDisplayMetrics();

        _threadPool = Executors.newCachedThreadPool();

        _imageLoader = new ImageLoader(this);
        _textRenderer = new TextRenderer(this);

        Log.v(TAG, "creating v8 runtime...");
        _v8 = V8.createV8Runtime();
        registerRuntime();

        String script;
        final String assetName = "main.js";
        try {
            InputStream input = getAssets().open(assetName);
            script = readScript(input);
        } catch (IOException e) {
            Log.e(TAG, "failed opening main.js", e);
            return;
        }
        _v8.executeVoidScript(script, assetName, 0);
        V8Object exports = _v8.getObject("module").getObject("exports");
        _exports = exports;

        Log.v(TAG, "creating root element...");
        _rootObject = _v8.executeObjectScript("new fd.Element()");
        _rootElement = getElementById(_rootObject.hashCode());

        if (_surfaceGeometry != null) { //already signalled
            setup();
        }
    }

    private void setup() {
        if (_rootObject != null && _surfaceGeometry != null) { //signal geometry
            Log.v(TAG, "updating window geometry");
            _rootObject.add("left", 0);
            _rootObject.add("top", 0);
            _rootObject.add("width", _surfaceGeometry.width());
            _rootObject.add("height", _surfaceGeometry.height());
        }
        if (_exports != null) {
            Log.v(TAG, "executing script...");

            Log.i(TAG, "calling run()...");
            _exports.executeJSFunction("run", _rootObject);

            Log.i(TAG, "run() finished");
            _exports.release();
            _exports = null;
        }
        schedulePaint();
    }

    @Override
    public void onDestroy() {
        Future<Void> future = _executor.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                _timers.cancel();
                try { _rootObject.executeVoidFunction("discard", null); }
                catch(Exception e) { Log.e(TAG, "discard failed", e); }

                if (_rootObject != null) {
                    _rootObject.release();
                    _rootObject = null;
                }
                if (_exports != null) {
                    _exports.release();
                }
                _v8.release();
                return null;
            }
        });
        try {
            future.get();
        } catch (InterruptedException e) {
            Log.e(TAG, "stopping environment failed", e);
        } catch (ExecutionException e) {
            Log.e(TAG, "stopping environment failed", e);
        }
        _executor.shutdownNow();

        _executor = null;
        _elements = null;
    }

    @Override
    public V8 getRuntime() {
        return _v8;
    }

    @Override
    public DisplayMetrics getDisplayMetrics() {
        return _metrics;
    }

    @Override
    public Element getElementById(long id) {
        Element element = _elements.get(Long.valueOf(id));
        if (element == null)
            throw new NullPointerException("object " + id + " was never registered or garbage collected");
        return element;
    }

    @Override
    public void putElement(long id, Element element) {
        if (element == null)
            throw new NullPointerException("putting null is not allowed");
        _elements.put(Long.valueOf(id), element);
    }

    @Override
    public void removeElement(long id) {
        _elements.remove(id);
    }

    @Override
    public Object invokeCallback(V8Function callback, V8Object receiver, V8Array arguments) {
        try {
            return callback.call(null, arguments);
        } catch (Exception e) {
            Log.e(TAG, "callback invocation failed", e);
        } finally {
            schedulePaint();
        }
        return null;
    }

    @Override
    public void invokeVoidCallback(V8Function callback, V8Object receiver, V8Array arguments) {
        Object r = invokeCallback(callback, receiver, arguments);
        if (r instanceof Releasable)
            ((Releasable) r).release();
    }

    @Override
    public ExecutorService getExecutor() {
        return _executor;
    }

    @Override
    public ExecutorService getThreadPool() {
        return _threadPool;
    }

    @Override
    public ImageLoader.ImageResource loadImage(URL url, ImageLoadedCallback listener) {
        List<ImageLoadedCallback> list = _imageWaiters.get(url);
        if (list == null) {
            list = new LinkedList<ImageLoadedCallback>();
            _imageWaiters.put(url, list);
        }
        list.add(listener);
        return _imageLoader.load(url, this);
    }

    @Override
    public void onImageLoaded(final URL url) {
        _executor.execute(new Runnable() {
            @Override
            public void run() {
                Log.v(TAG, "loaded image " + url);
                List<ImageLoadedCallback> list = _imageWaiters.get(url);
                if (list != null) {
                    for (ImageLoadedCallback l : list) {
                        try {
                            l.onImageLoaded(url);
                        } catch (Exception e) {
                            Log.e(TAG, "image listener failed", e);
                        }
                    }
                }
                _imageWaiters.remove(url);
                schedulePaint();
            }
        });
    }

    @Override
    public void layoutText(String text, Rect rect, final TextLayoutCallback callback) {
        _textRenderer.layoutText(text, rect, new TextLayoutCallback() {
            @Override
            public void onTextLayedOut(final TextLayout layout) {
                _executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        callback.onTextLayedOut(layout);
                    }
                });
            }
        });
    }

    protected void setSurfaceFrame(final Rect rect) {
        _executor.execute(new Runnable() {
            @Override
            public void run() {
                _surfaceGeometry = rect;
                Log.i(TAG, "new surface frame: " + _surfaceGeometry);
                if (_rootElement != null) {
                    _rootElement.emit(_rootObject, "resize", _surfaceGeometry.width(), _surfaceGeometry.height());
                }
                setup();
            }
        });
    }

    public void repaint(final SurfaceHolder holder) {
        final Future<Void> f = _executor.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                Canvas canvas = null;
                Rect rect = _rootElement.getCombinedDirtyRect();
                try {
                    canvas = holder.lockCanvas(rect);
                    _rootElement.paint(canvas, 0, 0, 1);
                } catch (Exception e) {
                    Log.e(TAG, "schedulePaint failed", e);
                } finally {
                    if (canvas != null)
                        holder.unlockCanvasAndPost(canvas);
                }
                return null;
            }
        });
        try {
            f.get();
        } catch (InterruptedException e) {
            Log.e(TAG, "repaint interrupted", e);
        } catch (ExecutionException e) {
            Log.e(TAG, "repaint failed", e);
        }
    }

    public void schedulePaint() {
        Element root = _rootElement;
        if (root == null)
            return;

        root.updateCurrentGeometry(0, 0);
        Rect rect = root.getCombinedDirtyRect();
        if (_renderer != null) {
            Log.i(TAG,"schedulePaint rect " + rect);
            _renderer.invalidateRect(rect);
        }
    }

    public Future<Boolean> sendEvent(final MotionEvent event) {
        return _executor.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                try {
                    Log.v(TAG,"click coordinates " + event.getX() + ", " + event.getY());
                    boolean r = _rootElement != null ? _rootElement.sendEvent("click", (int) event.getX(), (int) event.getY()) : false;
                    //Log.v(TAG, "click processed = " + r);
                    return r;
                } catch(Exception e) {
                    Log.e(TAG, "click handler failed", e);
                    return false;
                } finally {
                    schedulePaint();
                }
            }
        });
    }

}
