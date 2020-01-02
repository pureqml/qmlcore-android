package com.pureqml.android.runtime;

import android.util.Log;

import com.eclipsesource.v8.V8Array;

public class HttpRequest {
    private static final String TAG = "HttpRequest";
    public static void request(V8Array arguments) {
        Log.i(TAG, "arguments: " + arguments.toString());
    }
}
