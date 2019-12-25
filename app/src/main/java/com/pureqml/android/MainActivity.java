package com.pureqml.android;

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

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "main";
    private boolean                 _executionEnvironmentBound = false;
    private ExecutionEnvironment    _executionEnvironment;
    private MainView                _mainView;
    private Rect                    _surfaceFrame;
    private IRenderer               _uiRenderer;

    private ServiceConnection _executionEnvironmentConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            _executionEnvironment = ((ExecutionEnvironment.LocalBinder) service).getService();
            Log.i(TAG, "connected to execution service...");
            synchronized (MainActivity.this) {
                _mainView.setExecutionEnvironment(_executionEnvironment);
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
                        Log.v(TAG, "invalidateRect " + rect);
                        synchronized (holder) {
                            if (rect != null)
                                view.postInvalidate(rect.left, rect.top, rect.right, rect.bottom);
                            else
                                view.postInvalidate();
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
                if (_executionEnvironment != null)
                    _executionEnvironment.sendEvent(event);
                return false;
            }
        });

        bindService(new Intent(this,
                ExecutionEnvironment.class), _executionEnvironmentConnection, Context.BIND_AUTO_CREATE | Context.BIND_ADJUST_WITH_ACTIVITY);
        _executionEnvironmentBound = true;
    }

    @Override
    protected void onDestroy() {
        if (_executionEnvironmentBound)
            unbindService(_executionEnvironmentConnection);
        _mainView = null;
        super.onDestroy();
    }
}
