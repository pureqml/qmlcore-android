package com.pureqml.android;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "main";
    private boolean                 _executionEnvironmentBound = false;
    private ExecutionEnvironment    _executionEnvironment;
    private Rect                    _surfaceFrame;
    private SurfaceView             _surfaceView;

    private ServiceConnection _executionEnvironmentConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            _executionEnvironment = ((ExecutionEnvironment.LocalBinder) service).getService();

            if (_surfaceFrame != null) {
                _executionEnvironment.setSurfaceFrame(_surfaceFrame);
            }

            Log.i(TAG, "connected to execution service...");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "execution environment service died...");
            _executionEnvironment = null;
        }
    };

    private class SurfaceHolderCallback implements SurfaceHolder.Callback2 {

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.i(TAG, "surface created " + holder.getSurfaceFrame());
            if (_executionEnvironment != null) {
                _executionEnvironment.setRenderer(new IRenderer() {
                    @Override
                    public void invalidateRect(final Rect rect) {
                        if (rect != null)
                            MainActivity.this._surfaceView.postInvalidate(rect.left, rect.top, rect.right, rect.bottom);
                        else
                            MainActivity.this._surfaceView.postInvalidate();
                    }
                });
                _executionEnvironment.setSurfaceFrame(holder.getSurfaceFrame());
            } else
                _surfaceFrame = holder.getSurfaceFrame();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.i(TAG, "surface changed " + holder.getSurfaceFrame());
            if (_executionEnvironment != null)
                _executionEnvironment.setSurfaceFrame(holder.getSurfaceFrame());
            else
                _surfaceFrame = holder.getSurfaceFrame();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.i(TAG, "surface destroyed");
            if (_executionEnvironment != null)
                _executionEnvironment.setRenderer(null);
            _surfaceFrame = null;
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
        bindService(new Intent(this,
                ExecutionEnvironment.class), _executionEnvironmentConnection, Context.BIND_AUTO_CREATE | Context.BIND_ADJUST_WITH_ACTIVITY);
        _executionEnvironmentBound = true;

        _surfaceView = (SurfaceView)findViewById(R.id.contextView);
        _surfaceView.getHolder().addCallback(new SurfaceHolderCallback());
    }

    @Override
    protected void onDestroy() {
        if (_executionEnvironmentBound)
            unbindService(_executionEnvironmentConnection);
        _surfaceView = null;
        super.onDestroy();
    }
}
