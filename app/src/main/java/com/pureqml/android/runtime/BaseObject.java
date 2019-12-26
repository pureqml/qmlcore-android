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

    protected IExecutionEnvironment         _env;
    private Map<String, List<V8Function>>   _callbacks;

    public BaseObject(IExecutionEnvironment env) {
        _env = env;
    }

    public void discard() {
        if (_callbacks != null) {
            for(String name : _callbacks.keySet()) {
                List<V8Function> callbacks = _callbacks.get(name);
                for(V8Function callback : callbacks)
                    callback.close();
            }
            _callbacks = null;
        }
        //_env.removeElement(this.hashCode()); //fixme: find out why it's not working
    }

    public void on(String name, V8Function callback) {
        Log.i(TAG, "on " + name);
        if (_callbacks == null)
            _callbacks = new HashMap<>();
        List<V8Function> callbacks = _callbacks.get(name);
        if (callbacks == null) {
            callbacks = new LinkedList<V8Function>();
            _callbacks.put(name, callbacks);
        }
        callbacks.add(callback);
    }

    protected boolean hasCallbackFor(String name) {
        return _callbacks != null && _callbacks.get(name) != null;
    }

    public void emit(V8Object target, String name, Object ... args) {
        Log.i(TAG, "emitting " + name);
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
        _env.schedulePaint();
        v8args.close();
    }
}
