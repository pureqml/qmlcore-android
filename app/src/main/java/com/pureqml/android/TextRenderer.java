package com.pureqml.android;

import android.graphics.Rect;
import android.util.Log;

import com.pureqml.android.runtime.TextLayoutCallback;

import java.util.concurrent.ExecutorService;

public class TextRenderer {
    public static final String TAG = "TextRenderer";

    IExecutionEnvironment   _env;
    ExecutorService         _threadPool;

    TextRenderer(IExecutionEnvironment env) { _env = env; _threadPool = env.getThreadPool(); }

    public void layoutText(String text, Rect rect, TextLayoutCallback callback) {
        Log.i(TAG, "layout text " + text + " " + rect);
    }
}
