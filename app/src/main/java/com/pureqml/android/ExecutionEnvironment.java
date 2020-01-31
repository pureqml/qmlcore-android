package com.pureqml.android;

import android.app.Service;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;

import com.eclipsesource.v8.JavaCallback;
import com.eclipsesource.v8.JavaVoidCallback;
import com.eclipsesource.v8.Releasable;
import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.pureqml.android.runtime.BaseObject;
import com.pureqml.android.runtime.Console;
import com.pureqml.android.runtime.Element;
import com.pureqml.android.runtime.HttpRequest;
import com.pureqml.android.runtime.Image;
import com.pureqml.android.runtime.Input;
import com.pureqml.android.runtime.LocalStorage;
import com.pureqml.android.runtime.PaintState;
import com.pureqml.android.runtime.Rectangle;
import com.pureqml.android.runtime.Text;
import com.pureqml.android.runtime.Timers;
import com.pureqml.android.runtime.VideoPlayer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class ExecutionEnvironment extends Service
        implements IExecutionEnvironment, IResource {
    public static final String TAG = "ExecutionEnvironment";

    public class LocalBinder extends Binder {
        ExecutionEnvironment getService() {
            return ExecutionEnvironment.this;
        }
    }
    private final IBinder _binder = new LocalBinder();
    private V8 _v8;

    class WeakRefList<E> extends ArrayList<WeakReference<E>> {};
    //Element collection
    private Map<Long, BaseObject>       _objects = new HashMap<Long, BaseObject>(10000);
    private HashMap<URL, List<ImageLoadedCallback>>
                                        _imageWaiters = new HashMap<>();
    private WeakRefList<IResource>      _resources = new WeakRefList<IResource>();
    private Set<Element>                _updatedElements = new HashSet<Element>();
    private Rect                        _surfaceGeometry;
    private V8Object                    _rootObject;
    private Element                     _rootElement;
    private V8Object                    _exports;
    private ExecutorService             _executor;
    private ExecutorService             _rendererThread;
    private Timers                      _timers;
    private ExecutorService             _threadPool;
    private ImageLoader                 _imageLoader;
    private IRenderer                   _renderer;
    private DisplayMetrics              _metrics;
    private ViewGroup                   _rootView;
    private int                         _eventId;
    private boolean                     _blockInput;
    private View                        _focusedView;
    private SurfaceHolder               _surfaceHolder;

    public ExecutionEnvironment() {
        Log.i(TAG, "starting execution environment thread...");
        _rendererThread = Executors.newSingleThreadExecutor();
        _executor = Executors.newSingleThreadExecutor();
        _executor.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                Looper.prepare();
                ExecutionEnvironment.this.start();
                return null;
            }
        });
    }

    @Override
    public ViewGroup getRootView() {
        return _rootView;
    }

    void setRootView(ViewGroup rootView) {
        _rootView = rootView;
    }

    public Element getRootElement() {
        return _rootElement;
    }

    @Override
    public Context getContext() {
        return super.getBaseContext();
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
        v8Console.close();

        V8Object v8FD = new V8Object(_v8);
        _v8.add("fd", v8FD);

        v8FD.registerJavaMethod(new JavaCallback() {
            @Override
            public Object invoke(V8Object v8Object, V8Array v8Array) {
                final int DeviceDesktop  = 0;
                final int DeviceTV       = 1;
                final int DeviceMobile   = 2;

                V8Object info = new V8Object(_v8);
                info.add("deviceId", Settings.Secure.getString(getContext().getContentResolver(), Settings.Secure.ANDROID_ID));
                info.add("language", Locale.getDefault().toString());
                info.add("modelName", Build.MODEL);
                info.add("firmware", Build.VERSION.RELEASE);
                try {
                    UiModeManager uiModeManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
                    switch (uiModeManager.getCurrentModeType()) {
                        case Configuration.UI_MODE_TYPE_TELEVISION:
                            Log.i(TAG, "Running on TV device");
                            info.add("device", DeviceTV);
                            break;
                        default:
                            Log.i(TAG, "Running on mobile device");
                            info.add("device", DeviceMobile);
                            break;
                    }
                } catch(Exception ex) {
                    Log.e(TAG, "getCurrentModeType", ex);
                }
                return info;
            }
        }, "getDeviceInfo");
        v8FD.registerJavaMethod(new JavaVoidCallback() {
            @Override
            public void invoke(V8Object v8Object, V8Array v8Array) {
                HttpRequest.request(ExecutionEnvironment.this, v8Array);
            }
        }, "httpRequest");

        v8FD.registerJavaMethod(new JavaCallback() {
            @Override
            public Object invoke(V8Object v8Object, V8Array v8Array) {
                ExecutionEnvironment.this.paint();
                return null;
            }
        }, "paint");

        V8Object objectProto    = Wrapper.generateClass(this, _v8, v8FD, "Object", BaseObject.class, new Class<?>[] { IExecutionEnvironment.class });
        V8Object elementProto   = Wrapper.generateClass(this, _v8, v8FD, "Element", Element.class, new Class<?>[] { IExecutionEnvironment.class });
        elementProto.setPrototype(objectProto);
        V8Object rectangleProto = Wrapper.generateClass(this, _v8, v8FD, "Rectangle", Rectangle.class, new Class<?>[] { IExecutionEnvironment.class });
        rectangleProto.setPrototype(elementProto);
        V8Object imageProto     = Wrapper.generateClass(this, _v8, v8FD, "Image", Image.class, new Class<?>[] { IExecutionEnvironment.class });
        imageProto.setPrototype(elementProto);
        V8Object textProto      = Wrapper.generateClass(this, _v8, v8FD, "Text", Text.class, new Class<?>[] { IExecutionEnvironment.class });
        textProto.setPrototype(elementProto);
        V8Object inputProto     = Wrapper.generateClass(this, _v8, v8FD, "Input", Input.class, new Class<?>[] { IExecutionEnvironment.class });
        inputProto.setPrototype(elementProto);
        V8Object localStorageProto = Wrapper.generateClass(this, _v8, v8FD, "LocalStorage", LocalStorage.class, new Class<?>[] { IExecutionEnvironment.class });
        localStorageProto.setPrototype(objectProto);
        V8Object videoPlayerProto = Wrapper.generateClass(this, _v8, v8FD, "VideoPlayer", VideoPlayer.class, new Class<?>[] { IExecutionEnvironment.class });
        videoPlayerProto.setPrototype(objectProto);

        v8FD.close();

        V8Object v8Module = new V8Object(_v8);
        _v8.add("module", v8Module);
        v8Module.close();
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
        _rootElement = (Element)getObjectById(_rootObject.hashCode());

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
            _executor.execute(new Runnable() {
                @Override
                public void run() {
                    _rootElement.emit(_rootObject, "resize", _surfaceGeometry.width(), _surfaceGeometry.height());
                }
            });
        }
        if (_exports != null) {
            Log.v(TAG, "executing script...");

            Log.i(TAG, "calling run()...");
            _exports.executeJSFunction("run", _rootObject);

            Log.i(TAG, "run() finished");
            _exports.close();
            _exports = null;
        }
    }

    @Override
    public void onDestroy() {
        Future<Void> future = _executor.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
            _timers.discard();

            try { _rootObject.executeVoidFunction("discard", null); }
            catch(Exception e) { Log.e(TAG, "discard failed", e); }

            for(Map.Entry<Long, BaseObject> entry : _objects.entrySet()) {
                entry.getValue().discard();
            }
            _objects.clear();

            if (_rootElement != null) {
                _rootElement.discard();
                _rootElement = null;
            }

            if (_rootObject != null) {
                _rootObject.close();
                _rootObject = null;
            }
            if (_exports != null) {
                _exports.close();
                _exports = null;
            }

            _v8.close();
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
        _objects = null;
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
    public BaseObject getObjectById(long id) {
        BaseObject object = _objects.get(Long.valueOf(id));
        if (object == null)
            throw new NullPointerException("object " + id + " was never registered or garbage collected");
        return object;
    }

    @Override
    public void putObject(long id, BaseObject object) {
        if (object == null)
            throw new NullPointerException("putting null is not allowed");
        _objects.put(Long.valueOf(id), object);
    }

    @Override
    public void removeObject(long id) {
        _objects.remove(id);
    }

    @Override
    public Object invokeCallback(V8Function callback, V8Object receiver, V8Array arguments) {
        try {
            return callback.call(null, arguments);
        } catch (Exception e) {
            Log.e(TAG, "callback invocation failed", e);
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

    @Override
    public void update(Element el) {
        _updatedElements.add(el);
    }

    public void paint(final SurfaceHolder holder) {
        if (_rootElement == null || holder == null)
            return;

        Canvas canvas = null;
        Rect rect = _rootElement.getCombinedRect();
        Log.v(TAG, "paint: " + rect);
        try {
            canvas = holder.lockCanvas(rect);
            if (canvas != null) {
                PaintState paint = new PaintState(canvas);

                Paint bgPaint = new Paint();
                bgPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                canvas.drawRect(rect, bgPaint);

                _rootElement.paint(paint);
            }
        } catch (Exception e) {
            Log.e(TAG, "repaint failed", e);
        } finally {
            if (canvas != null)
                holder.unlockCanvasAndPost(canvas);
        }
    }

    public void paint() {
        _rendererThread.submit(new Runnable() {
            @Override
            public void run() {
                ExecutionEnvironment.this.paint(_surfaceHolder);
            }
        });
    }

    private Rect popDirtyRect() {
        Element root = _rootElement;
        if (root == null || _updatedElements.isEmpty() || _renderer == null)
            return null;

        //Log.v(TAG, "popDirtyRect: " + _updatedElements.size() + " elements");
        final Rect clipRect = _surfaceGeometry;
        Rect combinedRect = new Rect();
        for(Element el : _updatedElements) {
            if (el.visible()) {
                Rect rect = new Rect(el.getScreenRect());
                if (rect.intersect(clipRect))
                    combinedRect.union(rect);

                rect = new Rect(el.getCombinedRect());
                if (rect.intersect(clipRect))
                    combinedRect.union(rect);

            }
            Rect last = new Rect(el.getLastRenderedRect());
            if (last.intersect(clipRect))
                combinedRect.union(last);
        }
        _updatedElements.clear();
        return !combinedRect.isEmpty()? combinedRect: null;
    }

    @Override
    public void schedulePaint() {
        Rect combinedRect = popDirtyRect();
        if (combinedRect != null) {
            Log.v(TAG, "schedulePaint: combined rect: " + combinedRect.toString());
            _renderer.invalidateRect(combinedRect);
        }
    }

    public Future<Boolean> sendEvent(final String keyName, final KeyEvent event) {
        return _executor.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                try {
                    boolean r = _rootElement != null ? _rootElement.sendEvent(keyName, event) : false;
                    Log.v(TAG, "key processed = " + r);
                    return r;
                } catch(Exception e) {
                    Log.e(TAG, "key handler failed", e);
                    return false;
                }
            }
        });
    }

    public Future<Boolean> sendEvent(final MotionEvent event) {
        final int eventId;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            synchronized (this) {
                eventId = ++_eventId;
            }
        } else {
            synchronized (this) {
                eventId = _eventId;
            }
        }
        return _executor.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                try {
                    Log.v(TAG,"touch coordinates " + event.getX() + ", " + event.getY() + ", id: " + eventId);
                    boolean r = _rootElement != null ? _rootElement.sendEvent(eventId, (int) event.getX(), (int) event.getY(), event) : false;
                    //Log.v(TAG, "click processed = " + r);
                    return r;
                } catch(Exception e) {
                    Log.e(TAG, "click handler failed", e);
                    return false;
                }
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public void acquireResource() {
        Log.i(TAG, "acquireResources");
        WeakRefList<IResource> resources;
        synchronized (this) { resources = (WeakRefList<IResource>)_resources.clone(); }

        int i = 0, n = resources.size();
        while(i < n) {
            IResource res = resources.get(i).get();
            if (res != null) {
                try {
                    res.acquireResource();
                } catch (Exception e) {
                    Log.e(TAG, "acquireResource", e);
                } finally {
                    ++i;
                }
            } else {
                resources.remove(i);
                n = resources.size();
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void releaseResource() {
        Log.i(TAG, "releaseResources");
        WeakRefList<IResource> resources;
        synchronized (this) { resources = (WeakRefList<IResource>)_resources.clone(); }

        int i = 0, n = _resources.size();
        while(i < n) {
            IResource res = _resources.get(i).get();
            if (res != null) {
                try {
                    res.releaseResource();
                } catch (Exception e) {
                    Log.e(TAG, "releaseResource", e);
                } finally {
                    ++i;
                }
            } else {
                _resources.remove(i);
                n = _resources.size();
            }
        }
    }

    @Override
    public void register(IResource res) {
        synchronized (this) { _resources.add(new WeakReference<IResource>(res)); }
    }

    @Override
    public void focusView(View view, boolean set) {
        Log.v(TAG, "focusView: " + view + ", set: " + set);
        //fixme: better logic here.
        if (set) {
            _focusedView = view;
        } else {
            _blockInput = false;
            _focusedView = null;
        }
    }

    public View getFocusedView() {
        return _focusedView;
    }

    public void blockUiInput(boolean block) {
        _blockInput = block;
    }

    public boolean isUiInputBlocked() {
        return _blockInput;
    }

    public void setSurfaceHolder(SurfaceHolder holder) {
        _surfaceHolder = holder;
    }

    SurfaceHolder getSurfaceHolder() {
        return _surfaceHolder;
    }
}
