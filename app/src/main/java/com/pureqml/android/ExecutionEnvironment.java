package com.pureqml.android;

import android.app.Service;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Object;
import com.pureqml.android.runtime.Console;
import com.pureqml.android.runtime.Element;
import com.pureqml.android.runtime.IExecutionEnvironment;
import com.pureqml.android.runtime.Image;
import com.pureqml.android.runtime.Rectangle;
import com.pureqml.android.runtime.Text;
import com.pureqml.android.runtime.Wrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private Map<Long, Element>  _elements;
    Rect                        _surfaceGeometry;
    V8Object                    _rootElement;
    V8Object                    _exports;
    ExecutorService             _executor;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return _binder;
    }

    void registerRuntime() {
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
        _elements = new HashMap<Long, Element>(10000);

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
        _rootElement = _v8.executeObjectScript("new fd.Element()");

        if (_surfaceGeometry != null) { //already signalled
            setup();
        }
    }

    private void setup() {
        if (_rootElement != null && _surfaceGeometry != null) { //signal geometry
            Log.v(TAG, "updating window geometry");
            _rootElement.add("left", 0);
            _rootElement.add("top", 0);
            _rootElement.add("width", _surfaceGeometry.width());
            _rootElement.add("height", _surfaceGeometry.height());
        }
        if (_exports != null) {
            Log.v(TAG, "executing script...");

            Log.i(TAG, "calling run()...");
            _exports.executeJSFunction("run", _rootElement);

            Log.i(TAG, "run() finished");
            _exports.release();
            _exports = null;
        }
    }

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

    @Override
    public void onDestroy() {
        _elements = null;
        if (_rootElement != null) {
            _rootElement.release();
            _rootElement = null;
        }
        if (_exports != null) {
            _exports.release();
        }
        _v8.release();
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

    public void setSurfaceFrame(Rect rect) {
        _surfaceGeometry = rect;
        Log.i(TAG, "new surface frame: " + _surfaceGeometry);
        setup();
    }

    public void repaint(SurfaceHolder surface) {
        Log.i(TAG, "repaint");
    }

}
