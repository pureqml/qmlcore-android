package com.pureqml.android.runtime;

import android.util.Log;

import com.eclipsesource.v8.Releasable;
import com.eclipsesource.v8.V8;
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

public final class HttpRequest {
    private static final String TAG = "HttpRequestDispatcher";

    static class Request implements Runnable {
        private static final String TAG = "HttpRequest";
        IExecutionEnvironment   _env;
        URL                     _url;
        HttpURLConnection       _connection;
        byte []                 _body;
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
                if (value instanceof V8Value && ((V8Value) value).isUndefined()) {
                    return;
                }
                _body = value.toString().getBytes("UTF-8");
                if (_body.length > 0)
                    _connection.setDoOutput(true);
            } else if (key.equals("done")) {
                _callback = (V8Function)value;
            } else if (key.equals("error")) {
                _error = (V8Function)value;
            } else
                Log.w(TAG, "unhandled request field " + key);
        }

        @Override
        public void run() {
            int code = 0;
            String text;
            try {
                code = _connection.getResponseCode();
                Log.d(TAG, "response code: " + code);

                InputStream inputStream;
                if (code >= 200 && code < 400)
                    inputStream = _connection.getInputStream();
                else
                    inputStream = _connection.getErrorStream();

                ByteArrayOutputStream dataOutputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int length;
                while ((length = inputStream.read(buffer)) != -1) {
                    dataOutputStream.write(buffer, 0, length);
                }

                text = dataOutputStream.toString("UTF-8");
                Log.d(TAG, "response text: " + text);

                final int argCode = code;
                final String argText = text;
                _env.getExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        if (_callback != null)
                            _callback.call(null, createEventArguments(argCode, argText));
                    }
                });
            } catch (final Exception e) {
                e.printStackTrace();
                _env.getExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        emitError(e);
                    }
                });
            }
            finally {
                _connection.disconnect();
            }
        }

        V8Array createEventArguments(int code, String text) {
            V8 runtime = _env.getRuntime();
            V8Array arguments = new V8Array(runtime);
            V8Object result = new V8Object(runtime);
            {
                V8Object target = new V8Object(runtime);
                target.add("status", code);
                target.add("responseText", text != null? text: "");
                result.add("target", target);
                target.close();
            }
            arguments.push(result);
            result.close();
            return arguments;
        }


        private void emitError(final Exception e) {
            if (_error != null)
                _error.call(null, createEventArguments(400, e.toString()));
            else
                Log.w(TAG, "no error handler for request found", e);
        }

        public Request(IExecutionEnvironment env, V8Object request) {
            _env = env;
            try {
                _url = new URL(request.get("url").toString());
                Log.d(TAG, "url: " + _url);
                _connection = (HttpURLConnection) _url.openConnection();
                _connection.setDoInput(true);

                for (String key : request.getKeys()) {
                    Object value = request.get(key);
                    setProperty(key, value);
                }

                if (_body != null && _connection.getDoOutput()) {
                    OutputStream out = _connection.getOutputStream();
                    out.write(_body);
                    out.close();
                }
                Log.v(TAG, "starting request thread...");
                new Thread(this).start();
            } catch (Exception e) {
                Log.w(TAG, "connection failed", e);
                emitError(e);
            } finally {
                request.close();
                if (_connection != null)
                    _connection.disconnect();
            }
        }
    }

    public static void request(IExecutionEnvironment env, V8Array arguments) {
        if (arguments.length() < 1) {
            throw new IllegalArgumentException("not enough arguments for httpRequest");
        }
        V8Object request = (V8Object)arguments.get(0);
        arguments.close();

        new Request(env, request);
        request.close();
    }
}