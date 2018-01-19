package com.pureqml.android;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.renderscript.ScriptGroup;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.eclipsesource.v8.V8;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "main";
    private boolean                 _executionEnvironmentBound = false;
    private ExecutionEnvironment    _executionEnvironment;

    private ServiceConnection _executionEnvironmentConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            _executionEnvironment = ((ExecutionEnvironment.LocalBinder) service).getService();

            Log.i(TAG, "connected to execution service...");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "execution environment service died...");
            _executionEnvironment = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindService(new Intent(this,
                ExecutionEnvironment.class), _executionEnvironmentConnection, Context.BIND_AUTO_CREATE);
        _executionEnvironmentBound = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (_executionEnvironmentBound)
            unbindService(_executionEnvironmentConnection);
    }
}
