package com.pureqml.android.runtime;

import android.content.Context;
import android.util.Log;

import com.eclipsesource.v8.Releasable;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.pureqml.android.IExecutionEnvironment;

import java.io.FileInputStream;
import java.io.FileOutputStream;

public class LocalStorage extends BaseObject {
    public static final String TAG = "localstorage";

    public LocalStorage(IExecutionEnvironment env) {
        super(env);
        Log.i(TAG, "local storage created");
    }

    public void get(String name, V8Function callback, V8Function error, V8Object origin) {
        Log.i(TAG, "getting value " + name);
        V8Array args = new V8Array(_env.getRuntime());
        Object ret = null;
        try {
            final int MaxStorageSize = 128 * 1024;
            FileInputStream file = _env.getContext().openFileInput(name + ".storage");
            Log.i(TAG, "opened file for reading...");
            byte[] data = new byte[MaxStorageSize];
            int r = file.read(data);
            String stringData = new String(data, 0, r, "UTF-8");
            args.push(stringData);
            ret = callback.call(origin, args);
        } catch (Exception ex) {
            Log.w(TAG, "can't open file for reading");
            args.push(ex.toString());
            ret = error.call(origin, args); //indicate error
        } finally {
            if (ret != null && ret instanceof Releasable)
                ((Releasable)ret).release();
            args.close();
        }
    }

    public void set(String name, String value, V8Function callback, V8Function error, V8Object origin) {
        Log.i(TAG, "setting value " + name);
        V8Array args = new V8Array(_env.getRuntime());
        Object ret = null;
        try {
            FileOutputStream file = _env.getContext().openFileOutput(name + ".storage", Context.MODE_PRIVATE);
            Log.i(TAG, "opened file for writing...");
            file.write(value.getBytes("UTF-8"));
            ret = callback.call(origin, args);
        } catch (Exception ex) {
            Log.e(TAG, "can't open file for writing");
            args.push(ex.toString());
            ret = error.call(origin, args); //indicate error
        } finally {
            if (ret != null && ret instanceof Releasable)
                ((Releasable)ret).release();
            args.close();
        }
    }

}
