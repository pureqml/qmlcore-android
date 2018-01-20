package com.pureqml.android.runtime;

import com.eclipsesource.v8.JavaCallback;
import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Object;

public abstract class JavaCallbackWithRuntime implements JavaCallback {
    protected final V8 _v8;
    protected V8Object _prototype;

    public JavaCallbackWithRuntime(V8 v8, V8Object prototype) {
        _v8 = v8;
        _prototype = prototype;
    }
}

