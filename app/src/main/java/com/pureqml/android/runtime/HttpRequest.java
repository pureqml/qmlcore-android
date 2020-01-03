package com.pureqml.android.runtime;

import android.util.Log;

import com.eclipsesource.v8.Releasable;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.eclipsesource.v8.V8Value;
import com.pureqml.android.IExecutionEnvironment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class HttpRequest {
    private static final String TAG = "HttpRequestDispatcher";

    static class Request {
        private static final String TAG = "HttpRequest";
        IExecutionEnvironment   _env;
        URL                     _url;
        HttpURLConnection       _connection;
        V8Function              _callback;
        V8Function              _error;

        private void setProperty(String key, Object value) throws IOException {
            if (key.equals("url")) {
                //ignore
            } else if (key.equals("headers")) {
                V8Object headers = (V8Object) value;
                for (String k : headers.getKeys()) {
                    String v = headers.get(k).toString();
                    Log.v(TAG, "request header " + k + ": " + v);
                    _connection.setRequestProperty(k, v);
                }
            } else if (key.equals("method")) {
                Log.v(TAG, "request method " + value);
                _connection.setRequestMethod(value.toString());
            } else if (key.equals("contentType")) {
                _connection.setRequestProperty("Content-Type", value.toString());
            } else if (key.equals("data")) {
                OutputStream out = _connection.getOutputStream();
                out.write(value.toString().getBytes("UTF-8"));
                out.close();
            } else if (key.equals("done")) {
                _callback = (V8Function)value;
            } else if (key.equals("error")) {
                _error = (V8Function)value;
            } else
                Log.w(TAG, "unhandled request field " + key);
        }

        public Request(IExecutionEnvironment env, V8Object request) throws MalformedURLException {
            _env = env;
            try {
                _url = new URL(request.get("url").toString());
                Log.d(TAG, "url: " + _url);
                _connection = (HttpURLConnection) _url.openConnection();

                for (String key : request.getKeys()) {
                    Object value = request.get(key);
                    setProperty(key, value);
                }
                int code = _connection.getResponseCode();
                Log.d(TAG, "response code: " + code);
                InputStream inputStream = _connection.getInputStream();
                ByteArrayOutputStream dataOutputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) != -1) {
                    dataOutputStream.write(buffer, 0, length);
                }

                String data = dataOutputStream.toString("UTF-8");
                V8Array arguments = new V8Array(_env.getRuntime());
                arguments.push(data);
                try {
                    _callback.call(null, arguments);
                } finally {
                    arguments.close();
                }
            } catch (Exception e) {
                Log.w(TAG, "connection failed", e);
                if (_error != null) {
                    V8Object error = new V8Object(_env.getRuntime());
                    V8Array args = new V8Array(_env.getRuntime());
                    args.push(error);
                    try {
                        _error.call(null, args);
                    } finally {
                        args.close();
                        error.close();
                    }
                }
            } finally {
                request.close();
            }
        }
    }

    public static void request(IExecutionEnvironment env, V8Array arguments) {
        if (arguments.length() < 1) {
            throw new IllegalArgumentException("not enough arguments for httpRequest");
        }
        V8Object request = (V8Object)arguments.get(0);
        arguments.close();

        try {
            Request r = new Request(env, request);
        } catch (Exception e) {
            Log.e(TAG, "request", e);
        }
        finally {
            request.close();
        }
    }
}
