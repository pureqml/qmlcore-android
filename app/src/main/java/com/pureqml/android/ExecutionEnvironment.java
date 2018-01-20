package com.pureqml.android;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Object;
import com.pureqml.android.runtime.Console;
import com.pureqml.android.runtime.Element;
import com.pureqml.android.runtime.IExecutionEnvironment;
import com.pureqml.android.runtime.Wrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.WeakHashMap;

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
    private Map<Long, Element> _elements;

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

        v8FD.registerJavaMethod(Wrapper.generateClass(this, _v8, Element.class, new Class<?>[] { }), "Element");

        v8FD.release();

        V8Object v8Module = new V8Object(_v8);
        _v8.add("module", v8Module);
        v8Module.release();

        //create root element to pass it to context
        //return new Element(v8, elementPrototype);
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
        _elements = new WeakHashMap<Long, Element>(10000);

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

        Log.v(TAG, "creating root element...");
        V8Object rootElement = _v8.executeObjectScript("new fd.Element()");
        Log.v(TAG, "executing script...");
        Object result = exports.executeJSFunction("run", rootElement);

        Log.i(TAG, "script finished: " + result.toString());
        exports.release();
        //_v8.release();
    }

    public ExecutionEnvironment() {
        Log.i(TAG, "starting execution environment thread...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                ExecutionEnvironment.this.start();
            }
        }).start();
    }

    @Override
    public Element getElementById(long id) {
        return _elements.get(Long.valueOf(id));
    }

    @Override
    public void putElement(long id, Element element) {
        _elements.put(Long.valueOf(id), element);
    }

}
