package com.pureqml.android;

import android.app.Service;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.pureqml.android.runtime.Console;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;

public class ExecutionEnvironment extends Service {
    public static final String TAG = "ExecutionEnvironment";
    public class LocalBinder extends Binder {
        ExecutionEnvironment getService() {
            return ExecutionEnvironment.this;
        }
    }
    private final IBinder _binder = new LocalBinder();
    private V8 _runtime;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return _binder;
    }

    private final static int BufferSize = 128 * 1024;

    static private void registerRuntime(V8 runtime) {
        V8Object v8Console = new V8Object(runtime);
        runtime.add("console", v8Console);
        v8Console.registerJavaMethod(new Console.LogMethod(), "log");
        v8Console.release();

        V8Object v8Module = new V8Object(runtime);
        runtime.add("module", v8Module);
        v8Module.release();
    }

    private void start() {
        Log.i(TAG, "starting execution environment...");
        String script;
        try {
            InputStream input = getAssets().open("main.js");
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
            script = scriptStream.toString();
        } catch (IOException e) {
            Log.e(TAG, "failed opening main.js", e);
            return;
        }

        Log.v(TAG, "creating v8 runtime...");
        _runtime = V8.createV8Runtime();
        registerRuntime(_runtime);
        Log.v(TAG, "executing script...");
        _runtime.executeVoidScript(script);
        V8Object exports = _runtime.getObject("module").getObject("exports");
        Object result = exports.executeFunction("run", null);
        Log.i(TAG, "script finished: " + result.toString());
        //_runtime.release();
    }

    ExecutionEnvironment() {
        Log.i(TAG, "starting execution environment thread...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                ExecutionEnvironment.this.start();
            }
        }).start();
    }
}
