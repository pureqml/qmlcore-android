package com.pureqml.android.runtime;

import android.util.Log;

import com.eclipsesource.v8.Releasable;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.pureqml.android.IExecutionEnvironment;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class BaseObject {
    private static final String TAG = "object";

    protected final IExecutionEnvironment         _env;
    private final int                             _objectId;
    private Map<String, List<V8Function>>   _callbacks;

    public BaseObject(IExecutionEnvironment env) {
        _env = env;
        _objectId = _env.nextObjectId();
    }

    public int getObjectId() { return _objectId; }

    public void discard() {
        if (_callbacks != null) {
            for(List<V8Function> callbacks: _callbacks.values()) {
                for(V8Function callback : callbacks)
                    callback.close();
            }
            _callbacks = null;
        }
        _env.removeObject(this.getObjectId());
    }

    public void on(String name, V8Function callback) {
        //Log.d(TAG, "on " + name);
        if (_callbacks == null)
            _callbacks = new HashMap<>();
        List<V8Function> callbacks = _callbacks.get(name);
        if (callbacks == null) {
            callbacks = new LinkedList<>();
            _callbacks.put(name, callbacks);
        }
        callbacks.add(callback);
    }

    protected boolean hasCallbackFor(String name) {
        return _callbacks != null && _callbacks.get(name) != null;
    }

    public void emit(V8Object target, String name, Object ... args) {
        Log.v(TAG, "emitting " + name);
        if (_callbacks == null)
            return;

        List<V8Function> callbacks = _callbacks.get(name);
        if (callbacks == null)
            return;

        V8Array v8args = new V8Array(_env.getRuntime());
        for (int i = 0; i < args.length; ++i) {
            v8args.push(args[i]);
        }
        for(V8Function callback : callbacks) {
            try {
                Object r = callback.call(target, v8args);
                if (r instanceof Releasable)
                    ((Releasable)r).release();
            } catch (Exception e) {
                Log.e(TAG, "callback for " + name + " failed", e);
            }
        }
        v8args.close();
    }

    public boolean emitUntilTrue(V8Object target, String name, Object ... args) {
        Log.i(TAG, "emitting " + name);
        if (_callbacks == null)
            return false;

        List<V8Function> callbacks = _callbacks.get(name);
        if (callbacks == null)
            return false;

        V8Array v8args = new V8Array(_env.getRuntime());
        for (int i = 0; i < args.length; ++i) {
            v8args.push(args[i]);
        }
        boolean result = false;
        for(V8Function callback : callbacks) {
            try {
                Boolean r = (Boolean)callback.call(target, v8args);
                if (r.booleanValue()) {
                    result = true;
                    break;
                }
            } catch (Exception e) {
                Log.e(TAG, "callback for " + name + " failed", e);
            }
        }
        v8args.close();
        return result;
    }
}
