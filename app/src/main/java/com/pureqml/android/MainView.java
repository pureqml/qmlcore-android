package com.pureqml.android;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import androidx.annotation.RequiresApi;
import android.util.AttributeSet;
import android.view.SurfaceView;

public class MainView extends SurfaceView {
    private static final String TAG = "MainView";

    private IExecutionEnvironment _env;

    public MainView(Context context) {
        super(context);
    }

    public MainView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MainView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public MainView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setExecutionEnvironment(IExecutionEnvironment env) {
        _env = env;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        IExecutionEnvironment env = _env;
        if (env != null)
            env.repaint(getHolder());
    }
}
