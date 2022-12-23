package com.pureqml.android;

import android.annotation.SuppressLint;
import android.app.Service;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

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
import com.pureqml.android.runtime.Spinner;
import com.pureqml.android.runtime.Text;
import com.pureqml.android.runtime.Timers;
import com.pureqml.android.runtime.VideoPlayer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class ExecutionEnvironment extends Service
        implements IExecutionEnvironment, IResource {
    public static final String TAG = "ExecutionEnvironment";

    public class LocalBinder extends Binder {
        ExecutionEnvironment getService() {
            return ExecutionEnvironment.this;
        }
    }

    private static class ElementUpdater {
        private final Element element;
        private final long duration;
        private final long started;

        ElementUpdater(Element el, float seconds) {
            element = el;
            duration = (long)(1000 * seconds);
            started = SystemClock.currentThreadTimeMillis();
        }

        public boolean tick() {
            long dt = SystemClock.currentThreadTimeMillis() - started;
            Log.v(TAG, "tick " + dt + " of " + duration);
            boolean running = dt < duration;
            if (running)
                element.animate();
            return running;
        }
    }

    private final IBinder _binder = new LocalBinder();
    private V8 _v8;

    static class WeakRefList<E> extends ArrayList<WeakReference<E>> {}

    //Element collection
    private final HashMap<Integer, BaseObject>_objects = new HashMap<>(10000);
    private int                         _nextObjectId = 1;
    private final WeakRefList<IResource>      _resources = new WeakRefList<>();
    private final Set<Element>                _updatedElements = new HashSet<>();
    private final Map<Element, ElementUpdater>_elementUpdaters = new HashMap<>();
    private final Set<Element>                _elementUpdatersStop = new HashSet<>();
    private Rect                        _surfaceGeometry;
    private V8Object                    _rootObject;
    private Element                     _rootElement;
    private V8Object                    _exports;
    private ExecutorService             _executor;
    private Timers                      _timers;
    private final ExecutorService       _threadPool;
    private final ImageLoader           _imageLoader;
    private IRenderer                   _renderer;
    private ViewGroup                   _rootView;
    private boolean                     _paintScheduled;
    private int                         _eventId;
    private boolean                     _blockInput;
    private View                        _focusedView;
    private SurfaceHolder               _surfaceHolder;
    private String                      _orientation;
    private boolean                     _keepScreenOn;
    private boolean                     _fullScreen;
    private int                         _debugColorIndex;

    public ExecutionEnvironment() {
        super();

        Log.i(TAG, "starting execution environment thread...");
        _executor = Executors.newSingleThreadExecutor();

        Log.i(TAG, "creating thread pool...");
        _threadPool = Executors.newCachedThreadPool();

        Log.i(TAG, "started cached thread pool, creating image loader...");
        _imageLoader = new ImageLoader(this);

        _executor.execute(new SafeRunnable() {
            @Override
            public void doRun() {
                Looper.prepare();
                ExecutionEnvironment.this.start();
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

    @Override
    public void setRenderer(IRenderer renderer) {
        Log.v(TAG, "setRenderer " + renderer);
        String orientation;
        boolean keepScreenOn;
        synchronized (this) {
            _renderer = renderer;
            orientation = _orientation;
            keepScreenOn = _keepScreenOn;
        }
        if (renderer != null) {
            renderer.invalidateRect(null); //fullscreen update
            if (orientation != null)
                renderer.lockOrientation(orientation);
            renderer.keepScreenOn(keepScreenOn);
        }
    }

    @Override
    public IRenderer getRenderer() {
        synchronized (this) {
            return _renderer;
        }
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
            @SuppressLint("HardwareIds")
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
                info.add("runtime", "native");

                try {
                    UiModeManager uiModeManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
                    switch (uiModeManager.getCurrentModeType()) {
                        case Configuration.UI_MODE_TYPE_TELEVISION:
                            Log.i(TAG, "Running on TV device");
                            info.add("device", DeviceTV);
                            break;
                        default:
                            PackageManager pm = getContext().getPackageManager();
                            FeatureInfo features[] = pm.getSystemAvailableFeatures();
                            for(int i = 0; i < features.length; ++i) {
                                Log.v(TAG, "Available system feature: " + features[i].name);
                            }
                            if (pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
                                Log.i(TAG, "Running on mobile device");
                                info.add("device", DeviceMobile);
                            } else {
                                Log.i(TAG, "Running on TV device");
                                info.add("device", DeviceTV);
                            }
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
                Log.i(TAG, "setDeviceFeature: " + v8Array);
                if (v8Array.length() < 2)
                    throw new RuntimeException("setDevice feature requires two arguments");
                String name = v8Array.get(0).toString();
                switch(name) {
                    case "orientation":
                        _orientation = v8Array.get(1).toString();
                        if (_renderer != null)
                            _renderer.lockOrientation(_orientation);
                        break;
                    case "fullscreen":
                        _fullScreen = TypeConverter.toBoolean(v8Array.get(1));
                        if (_renderer != null)
                            _renderer.setFullScreen(_fullScreen);
                        break;
                    case "keep-screen-on":
                        _keepScreenOn = TypeConverter.toBoolean(v8Array.get(1));
                        if (_renderer != null)
                            _renderer.keepScreenOn(_keepScreenOn);
                        break;
                    default:
                        Log.w(TAG, "skipping device feature " + v8Array);
                }
            }
        }, "setDeviceFeature");

        v8FD.registerJavaMethod(new JavaVoidCallback() {
            @Override
            public void invoke(V8Object v8Object, V8Array v8Array) {
                HttpRequest.request(ExecutionEnvironment.this, v8Array);
            }
        }, "httpRequest");

        v8FD.registerJavaMethod(new JavaVoidCallback() {
            @Override
            public void invoke(V8Object v8Object, V8Array v8Array) {
                Log.i(TAG, "closing App: " + v8Array);
                if (_renderer != null)
                    _renderer.closeApp();
            }
        }, "closeApp");

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
        V8Object spinnerProto     = Wrapper.generateClass(this, _v8, v8FD, "Spinner", Spinner.class, new Class<?>[] { IExecutionEnvironment.class });
        spinnerProto.setPrototype(elementProto);
        V8Object localStorageProto = Wrapper.generateClass(this, _v8, v8FD, "LocalStorage", LocalStorage.class, new Class<?>[] { IExecutionEnvironment.class });
        localStorageProto.setPrototype(objectProto);
        V8Object videoPlayerProto = Wrapper.generateClass(this, _v8, v8FD, "VideoPlayer", VideoPlayer.class, new Class<?>[] { IExecutionEnvironment.class });
        videoPlayerProto.setPrototype(objectProto);

        v8FD.close();

        V8Object v8Module = new V8Object(_v8);
        _v8.add("module", v8Module);
        v8Module.close();
    }

    private static String readScript(InputStream input) throws IOException {
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

        Log.v(TAG, "creating v8 runtime...");
        _v8 = V8.createV8Runtime();
        Log.v(TAG, "registering runtime...");
        registerRuntime();

        Log.v(TAG, "executing main script...");
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
        _exports = _v8.getObject("module").getObject("exports");

        Log.v(TAG, "creating root element...");
        _rootObject = _v8.executeObjectScript("new fd.Element()");
        _rootElement = (Element)getObjectById(Wrapper.getObjectId(_rootObject));

        if (_surfaceGeometry != null) { //already signalled
            setup();
        }
        Log.v(TAG, "finishing start, painting...");
        paint();
    }

    private void setup() {
        if (_rootObject != null && _surfaceGeometry != null) { //signal geometry
            Log.v(TAG, "updating window geometry");
            _rootObject.add("left", 0);
            _rootObject.add("top", 0);
            _rootObject.add("width", _surfaceGeometry.width());
            _rootObject.add("height", _surfaceGeometry.height());
            _executor.execute(new SafeRunnable() {
                @Override
                public void doRun() {
                    if (_rootElement != null)
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
            public Void call() {
            _timers.discard();

            try { _rootObject.executeVoidFunction("discard", null); }
            catch(Exception e) { Log.e(TAG, "discard failed", e); }

            Vector<BaseObject> objects = new Vector<>(_objects.values());
            for(BaseObject o : objects) {
                if (o != null)
                    o.discard();
            }

            if (_rootElement != null) {
                _rootElement.discard();
                _rootElement = null;
            }

            _executor.execute(new SafeRunnable() {
                @Override
                public void doRun() {
                    if (_rootObject != null) {
                        _rootObject.close();
                        _rootObject = null;
                    }
                    if (_exports != null) {
                        _exports.close();
                        _exports = null;
                    }

                    _objects.clear();
                    try { _v8.close(); } catch (Exception ex) { Log.w(TAG, "v8 shutdown", ex); }
                }
            });
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
        Log.i(TAG, "shutting down main executor...");
        _executor.shutdown();
        try
        { _executor.awaitTermination(3, TimeUnit.SECONDS); }
        catch(Exception ex)
        { _executor.shutdownNow(); }
        Log.i(TAG, "main executor shut down");

        _executor = null;
        _objects.clear();
    }

    @Override
    public V8 getRuntime() {
        return _v8;
    }

    @Override
    public int nextObjectId() {
        return _nextObjectId++;
    }

    @Override
    public BaseObject getObjectById(int id) {
        return _objects.get(id);
    }

    @Override
    public void putObject(int id, BaseObject object) {
        if (object == null)
            throw new NullPointerException("putting null is not allowed");
        if (id <= 0)
            throw new RuntimeException("putObject: invalid object id");
        _objects.put(id, object);
    }

    @Override
    public void removeObject(int id) {
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
    public ImageLoader getImageLoader() {
        return _imageLoader;
    }

    protected void setSurfaceFrame(final Rect rect) {
        _executor.execute(new SafeRunnable() {
            @Override
            public void doRun() {
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
        if (el == null)
            return;

        synchronized (_updatedElements) {
            _updatedElements.add(el);
        }
        paint();
    }

    @Override
    public void startAnimation(Element el, float seconds)
    { _elementUpdaters.put(el, new ElementUpdater(el, seconds)); }

    @Override
    public void stopAnimation(Element el)
    { _elementUpdatersStop.add(el); }

    private int getDebugColorIndex() {
        int index = _debugColorIndex++ % 3;
        int debugAlpha = 0x40;
        switch(index) {
            case 0:
                return Color.argb(debugAlpha, 0xff, 0, 0);
            case 1:
                return Color.argb(debugAlpha, 0, 0xff, 0);
            case 2:
                return Color.argb(debugAlpha, 0, 0, 0xff);
            default:
                return 0;
        }
    }


    public void paint(final SurfaceHolder holder) {
        if (_rootElement == null || holder == null || holder.getSurface() == null)
            return;

        Rect rect = popDirtyRect();
        if (rect != null)
            Log.v(TAG, "paint: rect: " + rect.toString());
        else {
            Log.v(TAG, "paint: dirty rect is empty, skipping");
            return;
        }

        Canvas canvas = null;
        Log.v(TAG, "paint: " + rect);
        try {
            canvas = holder.lockCanvas(rect);
            if (canvas != null) {
                PaintState paint = new PaintState(canvas);

                {
                    Paint bgPaint = new Paint();
                    bgPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                    canvas.drawRect(rect, bgPaint);
                }

                _rootElement.paint(paint);

//                {
//                    Paint updatePaint = new Paint();
//                    updatePaint.setColor(getDebugColorIndex());
//                    canvas.drawRect(rect, updatePaint);
//                }
            }
        } catch (Exception e) {
            Log.e(TAG, "repaint failed", e);
        } finally {
            if (canvas != null)
                holder.unlockCanvasAndPost(canvas);
        }
    }

    public void paint() {
        synchronized (this) {
            if (_paintScheduled || _executor == null || _executor.isShutdown() || _rootElement == null || _renderer == null)
                return;
            _paintScheduled = true;
            Log.v(TAG, "paint scheduled");
        }
        _executor.execute(new SafeRunnable() {
            @Override
            public void doRun() {
                synchronized (this) { _paintScheduled = false; }
                ExecutionEnvironment.this.paint(_surfaceHolder);

                if (_elementUpdaters.isEmpty()) {
                    return;
                }
                Iterator<Map.Entry<Element, ElementUpdater>> it =_elementUpdaters.entrySet().iterator();
                while(it.hasNext()) {
                    Map.Entry<Element, ElementUpdater> entry = it.next();
                    if (!entry.getValue().tick())
                        it.remove();
                }

                for(Element el : _elementUpdatersStop) //avoid concurrent modification (element can call stopAnimation at any time
                    _elementUpdaters.remove(el);
                _elementUpdatersStop.clear();

                if (!_elementUpdaters.isEmpty())
                    ExecutionEnvironment.this.paint(); //restart
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
            Rect elementRect = el.getRedrawRect(clipRect);
            combinedRect.union(elementRect);
        }
        _updatedElements.clear();
        return !combinedRect.isEmpty()? combinedRect: null;
    }

    public Future<Boolean> sendEvent(final String keyName, final KeyEvent event) {
        return _executor.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                try {
                    boolean r = _rootElement != null && _rootElement.sendEvent(keyName, event);
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
            public Boolean call() {
                try {
                    Log.v(TAG,"touch coordinates " + event.getX() + ", " + event.getY() + ", id: " + eventId);
                    boolean r = _rootElement != null && _rootElement.sendEvent(eventId, (int) event.getX(), (int) event.getY(), event);
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
    public void acquireResource() {
        Log.i(TAG, "acquireResources");

        synchronized (_resources) {
            int i = 0, n = _resources.size();
            while (i < n) {
                IResource res = _resources.get(i).get();
                if (res != null) {
                    try {
                        res.acquireResource();
                    } catch (Exception e) {
                        Log.e(TAG, "acquireResource", e);
                    } finally {
                        ++i;
                    }
                } else {
                    _resources.remove(i);
                    n = _resources.size();
                }
            }
        }
    }

    @Override
    public void releaseResource() {
        Log.i(TAG, "releaseResources");

        synchronized (_resources) {
            int i = 0, n = _resources.size();
            while (i < n) {
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
    }

    @Override
    public void register(IResource res) {
        synchronized (this) { _resources.add(new WeakReference<>(res)); }
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

    @Override
    public Rect getSurfaceGeometry() { return _surfaceGeometry; }
}
