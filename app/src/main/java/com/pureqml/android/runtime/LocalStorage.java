package com.pureqml.android.runtime;

import android.content.Context;
import android.util.Log;

import com.eclipsesource.v8.Releasable;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.pureqml.android.IExecutionEnvironment;
import com.pureqml.android.SafeRunnable;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;

public final class LocalStorage extends BaseObject {
    private static final String TAG = "localstorage";
    private final ExecutorService _threadPool;
    private final ExecutorService _scriptThread;

    public LocalStorage(IExecutionEnvironment env) {
        super(env);
        Log.i(TAG, "local storage created");
        _threadPool = env.getThreadPool();
        _scriptThread = _env.getExecutor();
    }

    private void notify(V8Function callback, V8Object origin, String data) {
        _scriptThread.submit(new SafeRunnable() {
            @Override
            public void doRun() {
                V8Array args = new V8Array(_env.getRuntime());
                Object ret = null;
                try {
                    args.push(data);
                    ret = callback.call(origin, args);
                } catch (Exception ex) {
                    Log.w(TAG, "completion callback failed", ex);
                } finally {
                    if (ret instanceof Releasable)
                        ((Releasable)ret).release();
                    args.close();
                }
            }
        });
    }

    private void getAsync(String name, V8Function callback, V8Function error, V8Object origin) {
        Log.i(TAG, "getting value async " + name);
        try {
            FileInputStream file = _env.getContext().openFileInput(name + ".storage");
            Log.d(TAG, "opened file " + name + " for reading...");
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            int bufferSize = 128 * 1024;
            byte[] buffer = new byte[bufferSize];
            int r;
            while ((r = file.read(buffer)) != -1) {
                data.write(buffer, 0, r);
            }
            file.close();

            String value = data.toString("UTF-8");
            data.close();
            notify(callback, origin, value);
        } catch (FileNotFoundException ex) {
            Log.v(TAG, "no file found for " + name);
            notify(error, origin, ex.toString());
        } catch (Exception ex) {
            Log.v(TAG, "can't open file " + name + " for reading", ex);
            notify(error, origin, ex.toString());
        }
    }

    public void get(String name, V8Function callback, V8Function error, V8Object origin) {
        Log.i(TAG, "getting value " + name);
        _threadPool.submit(new SafeRunnable() {
            @Override
            public void doRun() {
                getAsync(name, callback, error, origin);
            }
        });
    }

    private void setAsync(String name, String value, V8Function error, V8Object origin) {
        Log.i(TAG, "setting value async " + name);
        try {
            FileOutputStream file = _env.getContext().openFileOutput(name + ".storage", Context.MODE_PRIVATE);
            Log.i(TAG, "opened file for writing...");
            file.write(value.getBytes(StandardCharsets.UTF_8));
            file.close();
        } catch (Exception ex) {
            Log.e(TAG, "can't open file " + name + " for writing", ex);
            notify(error, origin, ex.toString());
        }
    }

    public void set(String name, String value, V8Function error, V8Object origin) {
        Log.i(TAG, "setting value " + name);
        _threadPool.submit(new SafeRunnable() {
            @Override
            public void doRun() {
                setAsync(name, value, error, origin);
            }
        });
    }

    public void eraseAsync(String name, V8Function error, V8Object origin) {
        Log.i(TAG, "erasing value async " + name);
        try {
            _env.getContext().deleteFile(name + ".storage");
            Log.i(TAG, "file deleted...");
        } catch (Exception ex) {
            Log.e(TAG, "can't delete file", ex);
            notify(error, origin, ex.toString());
        }
    }

    public void erase(String name, V8Function error, V8Object origin) {
        Log.i(TAG, "erasing value " + name);
        _threadPool.submit(new SafeRunnable() {
            @Override
            public void doRun() {
                eraseAsync(name, error, origin);
            }
        });
    }

}
