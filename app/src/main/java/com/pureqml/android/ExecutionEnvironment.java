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
import com.pureqml.android.runtime.Wrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ExecutionEnvironment extends Service {
    public static final String TAG = "ExecutionEnvironment";
    public class LocalBinder extends Binder {
        ExecutionEnvironment getService() {
            return ExecutionEnvironment.this;
        }
    }
    private final IBinder _binder = new LocalBinder();
    private V8 _v8;
    private Element _rootElement;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return _binder;
    }

    private final static int BufferSize = 128 * 1024;

    static private void registerRuntime(V8 v8) {
        V8Object v8Console = new V8Object(v8);
        v8.add("console", v8Console);
        v8Console.registerJavaMethod(new Console.LogMethod(), "log");
        v8Console.release();

        V8Object v8FD = new V8Object(v8);

        V8Object elementPrototype = new V8Object(v8);
        v8FD.registerJavaMethod(new Element.Constructor(v8, elementPrototype), "Element");
        Wrapper.generatePrototype(v8, elementPrototype, Element.class);
        v8.add("fd", v8FD);
        v8FD.release();

        V8Object v8Module = new V8Object(v8);
        v8.add("module", v8Module);
        v8Module.release();
    }

    private static final String readScript(InputStream input) throws IOException {
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
        registerRuntime(_v8);
        Log.v(TAG, "executing script...");

        String script;
        try {
            InputStream input = getAssets().open("main.js");
            script = readScript(input);
        } catch (IOException e) {
            Log.e(TAG, "failed opening main.js", e);
            return;
        }
        _v8.executeVoidScript(script);
        V8Object exports = _v8.getObject("module").getObject("exports");

        //create root element to pass it to context
        _rootElement = new Element(_v8);

        Object result = exports.executeJSFunction("run", _rootElement);
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
}
