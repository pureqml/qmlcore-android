package com.pureqml.android.runtime;

import android.util.Log;
import android.util.SparseArray;

import com.eclipsesource.v8.JavaCallback;
import com.eclipsesource.v8.JavaVoidCallback;
import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.pureqml.android.ExecutionEnvironment;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;

public final class Timers {
    public static final String TAG = "Timers";

    ExecutionEnvironment    _env;
    Timer                   _timer = new Timer();
    SparseArray<TimerTask>  _tasks = new SparseArray<TimerTask>();
    int                     _nextId = 1;

    public Timer getTimer() { return _timer; }

    class Task extends TimerTask {
        int         _id;
        V8Object    _callback;
        boolean     _singleShot;

        Task(int id, V8Object callback, boolean singleShot) {
            _id = id;
            _callback = callback.twin();
            _singleShot = singleShot;
        }

        private void releaseCallback() {
            //Log.v(TAG, "Timer task " + _id + " released callback");

            if (_callback != null) {
                _callback.close();
                _callback = null;
            }
        }

        @Override
        protected void finalize() throws Throwable {
            //Log.v(TAG, "Timer task " + _id + " finalized");
            ExecutorService executor = _env.getExecutor();
            if (executor != null) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        releaseCallback();
                    }
                });
            } else
                Log.w(TAG, "no executor, callback won't be recycled");
            super.finalize();
        }

        @Override
        public void run() {
            _env.getExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    //Log.v(TAG, "Timer task " + _id + " fired");
                    V8Function func = (V8Function)_callback;
                    if (func != null)
                        _env.invokeCallback(func, null, null);
                    if (_singleShot)
                        cancel();
                }
            });
        }

        @Override
        public boolean cancel() {
            //Log.v(TAG, "Timer task " + _id + " has been cancelled: ");
            _tasks.remove(_id);
            releaseCallback();
            return super.cancel();
        }
    }

    public Timers(ExecutionEnvironment env) {
        _env = env;
        V8 v8 = env.getRuntime();
        v8.registerJavaMethod(new JavaCallback() {
            @Override
            public Object invoke(V8Object v8Object, V8Array arguments) {
                if (_timer == null) {
                    Log.w(TAG, "skipping setTimeout, timer is dead");
                    return -1;
                }
                int timeout = arguments.getInteger(1);
                int id = _nextId++;
                TimerTask task = new Task(id, arguments.getObject(0), true);
                _tasks.put(id, task);
                _timer.schedule(task, timeout);
                arguments.close();
                return id;
            }
        }, "setTimeout");
        v8.registerJavaMethod(new JavaCallback() {
            @Override
            public Object invoke(V8Object v8Object, V8Array arguments) {
                if (_timer == null) {
                    Log.w(TAG, "skipping setInterval, timer is dead");
                    return -1;
                }
                int period = arguments.getInteger(1);
                int id = _nextId++;
                TimerTask task = new Task(id, arguments.getObject(0), false);
                _tasks.put(id, task);
                _timer.schedule(task, period, period);
                arguments.close();
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
                    _tasks.remove(id);
                } else {
                    //Log.v(TAG, "invalid/completed task " + id  + " cancelled");
                }
            }
        };
        v8.registerJavaMethod(cancel, "clearTimeout");
        v8.registerJavaMethod(cancel, "clearInterval");
    }

    public void discard() {
        Timer timer = _timer;
        _timer = null; //block all new tasks
        timer.cancel();

        for(int i = 0, n = _tasks.size(); i < n; ++i) {
            TimerTask task = _tasks.valueAt(i);
            if (task != null)
                task.cancel();
        }
        _tasks.clear();
        timer.purge();
    }
}
