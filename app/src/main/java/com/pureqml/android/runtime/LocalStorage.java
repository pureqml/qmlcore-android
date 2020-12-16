package com.pureqml.android.runtime;

import android.content.Context;
import android.util.Log;

import com.eclipsesource.v8.Releasable;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.pureqml.android.IExecutionEnvironment;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public final class LocalStorage extends BaseObject {
    private static final String TAG = "localstorage";
    private final int BufferSize = 128 * 1024;

    public LocalStorage(IExecutionEnvironment env) {
        super(env);
        Log.i(TAG, "local storage created");
    }

    public void get(String name, V8Function callback, V8Function error, V8Object origin) {
        Log.i(TAG, "getting value " + name);
        V8Array args = new V8Array(_env.getRuntime());
        Object ret = null;
        try {
            FileInputStream file = _env.getContext().openFileInput(name + ".storage");
            Log.d(TAG, "opened file " + name + " for reading...");
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            byte[] buffer = new byte[BufferSize];
            int r;
            while ((r = file.read(buffer)) != -1) {
                data.write(buffer, 0, r);
            }
            args.push(data.toString("UTF-8"));
            ret = callback.call(origin, args);
        } catch (Exception ex) {
            Log.w(TAG, "can't open file " + name + " for reading");
            args.push(ex.toString());
            ret = error.call(origin, args); //indicate error
        } finally {
            if (ret instanceof Releasable)
                ((Releasable)ret).release();
            args.close();
        }
    }

    public void set(String name, String value, V8Function error, V8Object origin) {
        Log.i(TAG, "setting value " + name);
        V8Array args = new V8Array(_env.getRuntime());
        Object ret = null;
        try {
            FileOutputStream file = _env.getContext().openFileOutput(name + ".storage", Context.MODE_PRIVATE);
            Log.i(TAG, "opened file for writing...");
            file.write(value.getBytes("UTF-8"));
        } catch (Exception ex) {
            Log.e(TAG, "can't open file for writing");
            args.push(ex.toString());
            ret = error.call(origin, args); //indicate error
        } finally {
            if (ret instanceof Releasable)
                ((Releasable)ret).release();
            args.close();
        }
    }

    public void erase(String name, V8Function error, V8Object origin) {
        Log.i(TAG, "erasing value " + name);
        V8Array args = new V8Array(_env.getRuntime());
        Object ret = null;
        try {
            _env.getContext().deleteFile(name + ".storage");
            Log.i(TAG, "file deleted...");
        } catch (Exception ex) {
            Log.e(TAG, "can't delete file");
            args.push(ex.toString());
            ret = error.call(origin, args); //indicate error
        } finally {
            if (ret instanceof Releasable)
                ((Releasable)ret).release();
            args.close();
        }
    }

}
