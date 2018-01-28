package com.pureqml.android.runtime;

import android.util.Log;

import com.eclipsesource.v8.JavaCallback;
import com.eclipsesource.v8.JavaVoidCallback;
import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.pureqml.android.ExecutionEnvironment;

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class Timers {
    public static final String TAG = "Timers";

    ExecutionEnvironment    _env;
    Timer                   _timer = new Timer();
    HashMap<Integer, TimerTask> _tasks = new HashMap<Integer, TimerTask>();
    int                     _nextId = 1;

    class Task extends TimerTask {
        int         _id;
        V8Object    _callback;
        boolean     _singleShot;

        Task(int id, V8Object callback, boolean singleShot) { _id = id; _callback = callback; _singleShot = singleShot; }

        @Override
        public void run() {
            _env.getExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    //Log.v(TAG, "timer task " + _id + " fired");
                    V8Function func = (V8Function)_callback;
                    _env.invokeCallback(func, null, null);
                    if (_singleShot)
                        cancel();
                }
            });
        }

        @Override
        public boolean cancel() {
            _callback.release();
            _tasks.remove(_id);
            return super.cancel();
        }
    }

    public Timers(ExecutionEnvironment env) {
        _env = env;
        V8 v8 = env.getRuntime();
        v8.registerJavaMethod(new JavaCallback() {
            @Override
            public Object invoke(V8Object v8Object, V8Array arguments) {
                int timeout = arguments.getInteger(1);
                int id = _nextId++;
                TimerTask task = new Task(id, arguments.getObject(0), true);
                _timer.schedule(task, timeout);
                arguments.release();
                return id;
            }
        }, "setTimeout");
        v8.registerJavaMethod(new JavaCallback() {
            @Override
            public Object invoke(V8Object v8Object, V8Array arguments) {
                int period = arguments.getInteger(1);
                int id = _nextId++;
                TimerTask task = new Task(id, arguments.getObject(0), false);
                _timer.schedule(task, period, period);
                arguments.release();
                return id;
            }
        }, "setInterval");
        JavaVoidCallback cancel = new JavaVoidCallback() {
            @Override
            public void invoke(V8Object v8Object, V8Array arguments) {
                int id = arguments.getInteger(0);
                //callback will leak here, fixme
                TimerTask task = _tasks.get(id);
                if (task != null) {
                    task.cancel();
                } else {
                    //Log.v(TAG, "invalid/completed task " + id  + " cancelled");
                }
            }
        };
        v8.registerJavaMethod(cancel, "clearTimeout");
        v8.registerJavaMethod(cancel, "clearInterval");
    }

    public void cancel() { _timer.cancel(); }
}
