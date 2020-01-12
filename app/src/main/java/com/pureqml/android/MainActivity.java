package com.pureqml.android;

import android.content.ComponentCallbacks2;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.IBinder;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import com.pureqml.android.runtime.Element;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public final class MainActivity
        extends AppCompatActivity
        implements ComponentCallbacks2
{
    private static final String TAG = "main";
    private boolean                 _executionEnvironmentBound = false;
    private ExecutionEnvironment    _executionEnvironment;
    private MainView                _mainView;
    private Rect                    _surfaceFrame;
    private IRenderer               _uiRenderer;

    private ServiceConnection _executionEnvironmentConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(TAG, "connected to execution service...");
            _executionEnvironment = ((ExecutionEnvironment.LocalBinder) service).getService();
            _executionEnvironment.acquireResource();
            synchronized (MainActivity.this) {
                _mainView.setExecutionEnvironment(_executionEnvironment);
                ViewGroup rootView = (ViewGroup)findViewById(android.R.id.content);
                rootView = (ViewGroup)rootView.getChildAt(0);
                _executionEnvironment.setRootView(rootView);

                if (_surfaceFrame != null)
                    _executionEnvironment.setSurfaceFrame(_surfaceFrame);
                if (_uiRenderer != null) {
                    _executionEnvironment.setRenderer(_uiRenderer);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "execution environment service died...");
            synchronized (MainActivity.this) {
                _executionEnvironment.setRootView(null);
                _mainView.setExecutionEnvironment(null);
                _executionEnvironment = null;
            }
        }
    };

    private class SurfaceHolderCallback implements SurfaceHolder.Callback2 {

        @Override
        public void surfaceCreated(final SurfaceHolder holder) {
            Log.i(TAG, "surface created " + holder.getSurfaceFrame());
            synchronized (MainActivity.this) {
                _surfaceFrame = holder.getSurfaceFrame();

                final SurfaceView view = _mainView;
                _uiRenderer = new IRenderer() {
                    @Override
                    public void invalidateRect(Rect rect) {
                        synchronized (holder) {
                            if (rect != null) {
                                if (!rect.isEmpty()) {
                                    //Log.v(TAG, "invalidateRect " + rect);
                                    view.postInvalidate(rect.left, rect.top, rect.right, rect.bottom);
                                }
                            } else {
                                Log.v(TAG, "invalidateAll");
                                view.postInvalidate();
                            }
                        }
                    }
                };
                if (_executionEnvironment != null)
                    _executionEnvironment.setRenderer(_uiRenderer);
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.i(TAG, "surface changed " + holder.getSurfaceFrame());
            synchronized (MainActivity.this) {
                _surfaceFrame = holder.getSurfaceFrame();
                if (_executionEnvironment != null)
                    _executionEnvironment.setSurfaceFrame(_surfaceFrame);
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.i(TAG, "surface destroyed");
            synchronized (MainActivity.this) {
                if (_executionEnvironment != null)
                    _executionEnvironment.setRenderer(null);
                _surfaceFrame = null;
            }
        }

        @Override
        public void surfaceRedrawNeeded(SurfaceHolder holder) {
            Log.i(TAG, "redraw needed");
            if (_executionEnvironment != null)
                _executionEnvironment.repaint(holder);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_main);

        _mainView = (MainView) findViewById(R.id.contextView);
        _mainView.getHolder().addCallback(new SurfaceHolderCallback());
        _mainView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Log.i(TAG, "motion " + event.toString());
                if (event.getAction() != MotionEvent.ACTION_UP)
                    return true;

                if (_executionEnvironment != null) {
                    try {
                        return _executionEnvironment.sendEvent(event).get();
                    } catch (Exception e) {
                        Log.e(TAG, "execution exception", e);
                        return false;
                    }
                }
                return false;
            }
        });

        bindService(new Intent(this,
                ExecutionEnvironment.class), _executionEnvironmentConnection, Context.BIND_AUTO_CREATE | Context.BIND_ADJUST_WITH_ACTIVITY);
        _executionEnvironmentBound = true;
    }

    @Override
    public void onBackPressed() {
        boolean result = false;
        try {
            result = _executionEnvironment.getExecutor().submit(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    Log.d(TAG, "back pressed, calling Context.processKey");
                    Element context = _executionEnvironment.getRootElement();
                    return context.emitUntilTrue(null, "keydown", "Back");
                }
            }).get();
            Log.d(TAG, "key handler finishes with " + result);
        } catch (ExecutionException e) {
            Log.e(TAG, "onBackPressed", e);
        } catch (InterruptedException e) {
            Log.e(TAG, "onBackPressed", e);
        }
        if (!result)
            super.onBackPressed();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "stopping main activity...");
        if (_executionEnvironment != null)
            _executionEnvironment.releaseResource();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "starting main activity...");
        if (_executionEnvironment != null)
            _executionEnvironment.acquireResource();
    }

    @Override
    protected void onDestroy() {
        if (_executionEnvironmentBound)
            unbindService(_executionEnvironmentConnection);
        _mainView = null;
        super.onDestroy();
    }

    @Override
    public void onTrimMemory(int level) {
        Log.i(TAG, "trim memory, level: " + level);
        super.onTrimMemory(level);
    }
}
