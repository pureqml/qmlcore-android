package com.pureqml.android.runtime;

import android.util.Log;

import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.pureqml.android.IExecutionEnvironment;

public class LocalStorage extends BaseObject {
    public static final String TAG = "localstorage";

    public LocalStorage(IExecutionEnvironment env) {
        super(env);
        Log.i(TAG, "local storage created");
    }

    public void get(String name, V8Function callback, V8Function error, V8Object origin) {
        Log.i(TAG, "getting value " + name);
    }

    public void set(String name, V8Function callback, V8Function error, V8Object origin) {
        Log.i(TAG, "setting value " + name);
    }

}
