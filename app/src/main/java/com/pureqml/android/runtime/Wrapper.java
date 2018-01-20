package com.pureqml.android.runtime;

import android.util.Log;

import com.eclipsesource.v8.JavaCallback;
import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Object;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class Wrapper {
    public final static String TAG = "ClassWrapper";

    public static final class MethodWrapper implements JavaCallback {
        private final Method _method;
        public MethodWrapper(Method method) { _method = method; }

        @Override
        public Object invoke(V8Object self, V8Array arguments) {
            int n = arguments.length();
            Object [] targetArguments = new Object[n];
            for(int i = 0; i < n; ++i)
                targetArguments[i] = arguments.get(i);
            try {
                return _method.invoke(self, targetArguments);
            } catch (IllegalAccessException e) {
                Log.e(TAG, "invocation failed", e);
            } catch (InvocationTargetException e) {
                Log.e(TAG, "invocation failed", e);
            }
            return null;
        }
    }

    public static final class SimpleMethodWrapper implements JavaCallback {
        private final Method _method;

        public SimpleMethodWrapper(Method method) {
            _method = method;
        }

        @Override
        public Object invoke(V8Object self, V8Array arguments) {
            try {
                return _method.invoke(self, arguments);
            } catch (IllegalAccessException e) {
                Log.e(TAG, "invocation failed", e);
            } catch (InvocationTargetException e) {
                Log.e(TAG, "invocation failed", e);
            }
            return null;
        }
    }

    public static V8Object generatePrototype(V8 v8, V8Object prototype, Class<?> cls) {
        Log.i(TAG, "wrapping class " + cls.getName());

        for(Method method : cls.getMethods()) {
            String name = method.getName();
            if (method.getDeclaringClass().equals(cls) && Modifier.isPublic(method.getModifiers()) && !name.startsWith("access$")) {
                Log.i(TAG, "wrapping method " + name);
                Class<?>[] argTypes = method.getParameterTypes();
                if (argTypes.length == 1 && argTypes[0].equals(V8Array.class))
                    prototype.registerJavaMethod(new SimpleMethodWrapper(method), name);
                else
                    prototype.registerJavaMethod(new MethodWrapper(method), name);
            }
        }
        return null;
    }
}
