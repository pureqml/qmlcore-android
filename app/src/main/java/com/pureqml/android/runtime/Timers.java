package com.pureqml.android.runtime;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.SparseArray;

import com.eclipsesource.v8.JavaCallback;
import com.eclipsesource.v8.JavaVoidCallback;
import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.pureqml.android.ExecutionEnvironment;
import com.pureqml.android.SafeRunnable;

import java.util.concurrent.ExecutorService;

public final class Timers {
    public static final String TAG = "Timers";

    class Task extends SafeRunnable {
        final int           _id;
        V8Object            _callback;
        final int           _timeout;
        final boolean       _singleShot;

        Task(int id, V8Object callback, int timeout, boolean singleShot) {
            _id = id;
            _callback = callback.twin();
            _timeout = timeout;
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
            if (executor != null && _callback != null) {
                executor.execute(new SafeRunnable() {
                    @Override
                    public void doRun() {
                        releaseCallback();
                    }
                });
            } else if (_callback != null)
                Log.w(TAG, "no executor, callback won't be recycled");
            super.finalize();
        }

        @Override
        public void doRun() {
            ExecutorService executor = _env.getExecutor();
            if (executor == null)
                return;

            executor.execute(new SafeRunnable() {
                @Override
                public void doRun() {
                    //Log.v(TAG, "Timer task " + _id + " fired");
                    try {
                        V8Function func = (V8Function) _callback;
                        if (func != null)
                            _env.invokeCallback(func, null, null);

                    } finally {
                        if (_singleShot)
                            cancel();
                    }
                }
            });

            Handler handler = _handler;
            if (!_singleShot) {
                if (handler != null) {
                    handler.postDelayed(this, _timeout);
                } else
                    Log.i(TAG, "stopping periodic task, null handler");
            }
        }

        public void cancel() {
            //Log.v(TAG, "Timer task " + _id + " has been cancelled: ");
            _tasks.remove(_id);
            releaseCallback();
        }
    }



    final ExecutionEnvironment      _env;
    HandlerThread                   _handlerThread;
    Handler                         _handler;
    final SparseArray<Task>         _tasks = new SparseArray<>();
    int                             _nextId = 1;

    public Timers(ExecutionEnvironment env) {
        _env = env;
        Log.i(TAG, "starting timer thread...");
        _handlerThread = new HandlerThread("TimerThread");
        _handlerThread.start();
        _handler = new Handler(_handlerThread.getLooper());

        Log.i(TAG, "registering API functions...");
        V8 v8 = env.getRuntime();
        v8.registerJavaMethod(new JavaCallback() {
            @Override
            public Object invoke(V8Object v8Object, V8Array arguments) {
                Handler handler = _handler;
                if (handler == null) {
                    Log.w(TAG, "skipping setTimeout, timer is dead");
                    return -1;
                }
                int timeout = arguments.getInteger(1);
                int id = _nextId++;
                Task task = new Task(id, arguments.getObject(0), timeout,true);
                _tasks.put(id, task);
                handler.postDelayed(task, timeout);
                arguments.close();
                return id;
            }
        }, "setTimeout");
        v8.registerJavaMethod(new JavaCallback() {
            @Override
            public Object invoke(V8Object v8Object, V8Array arguments) {
                Handler handler = _handler;
                if (handler == null) {
                    Log.w(TAG, "skipping setInterval, timer is dead");
                    return -1;
                }
                int period = arguments.getInteger(1);
                int id = _nextId++;
                Task task = new Task(id, arguments.getObject(0), period,false);
                _tasks.put(id, task);
                handler.postDelayed(task, period);
                arguments.close();
                return id;
            }
        }, "setInterval");
        JavaVoidCallback cancel = new JavaVoidCallback() {
            @Override
            public void invoke(V8Object v8Object, V8Array arguments) {
                int id = arguments.getInteger(0);
                //callback will leak here, fixme
                Task task = _tasks.get(id);
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
        for(int i = 0, n = _tasks.size(); i < n; ++i) {
            Task task = _tasks.get(i, null);
            if (task != null)
                task.cancel();
        }
        _tasks.clear();
        _handlerThread.quit();
        _handlerThread = null;
        _handler = null;
    }
}
