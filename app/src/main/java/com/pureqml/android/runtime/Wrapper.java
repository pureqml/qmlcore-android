package com.pureqml.android.runtime;

import android.util.Log;

import com.eclipsesource.v8.JavaCallback;
import com.eclipsesource.v8.JavaVoidCallback;
import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Object;
import com.eclipsesource.v8.V8Value;
import com.eclipsesource.v8.utils.V8ObjectUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class Wrapper {
    public final static String TAG = "ClassWrapper";

    private static Object getValue(IExecutionEnvironment env, Object object) {
        if (object instanceof V8Value) {
            V8Value value = (V8Value)object;
            if (value.getV8Type() == V8Value.V8_OBJECT) {
                return env.getElementById(value.hashCode());
            } else
                return value;
        } else
            return object;
    }

    public static final class MethodWrapper implements JavaCallback {
        IExecutionEnvironment _env;
        private final Method _method;
        public MethodWrapper(IExecutionEnvironment env, Method method) { _env = env; _method = method; }

        @Override
        public Object invoke(V8Object self, V8Array arguments) {
            Element element = _env.getElementById(self.hashCode());
            int n = arguments.length();
            Object [] targetArguments = new Object[n];
            for(int i = 0; i < n; ++i)
                targetArguments[i] = getValue(_env, arguments.get(i));
            try {
                return _method.invoke(element, targetArguments);
            } catch (IllegalAccessException e) {
                Log.e(TAG, "invocation failed", e);
            } catch (InvocationTargetException e) {
                Log.e(TAG, "invocation failed", e);
            }
            return null;
        }
    }

    public static final class SimpleMethodWrapper implements JavaCallback {
        IExecutionEnvironment _env;
        private final Method _method;

        public SimpleMethodWrapper(IExecutionEnvironment env, Method method) { _env = env; _method = method; }

        @Override
        public Object invoke(V8Object self, V8Array arguments) {
            Element element = _env.getElementById(self.hashCode());
            try {
                return _method.invoke(element, arguments);
            } catch (IllegalAccessException e) {
                Log.e(TAG, "invocation failed", e);
            } catch (InvocationTargetException e) {
                Log.e(TAG, "invocation failed", e);
            }
            return null;
        }
    }

    public static void generatePrototype(IExecutionEnvironment env, V8 v8, V8Object prototype, Class<?> cls) {
        Log.i(TAG, "wrapping class " + cls.getName());

        for(Method method : cls.getMethods()) {
            String name = method.getName();
            if (!method.getDeclaringClass().equals(cls) || !Modifier.isPublic(method.getModifiers()) || method.isSynthetic())
                continue;

            Log.i(TAG, "wrapping method " + name);
            Class<?>[] argTypes = method.getParameterTypes();
            if (argTypes.length == 1 && argTypes[0].equals(V8Array.class)) {
                prototype.registerJavaMethod(new SimpleMethodWrapper(env, method), name);
            } else
                prototype.registerJavaMethod(new MethodWrapper(env, method), name);
        }
    }

    private static final class ConstructorWrapper implements JavaVoidCallback {
        IExecutionEnvironment   _env;
        V8Object                _proto;
        Constructor<?>          _ctor;

        public ConstructorWrapper(IExecutionEnvironment env, V8Object proto, Constructor<?> ctor) {
            _env = env;
            _proto = proto;
            _ctor = ctor;
        }

        @Override
        public void invoke(V8Object self, V8Array arguments) {
            Class<?> ctorArgs[] = _ctor.getParameterTypes();
            int n = ctorArgs.length;
            Object args[] = new Object[n];
            for(int i = 0; i < n; ++i) {
                args[i] = getValue(_env, arguments.get(i));
            }
            try {
                Object obj = _ctor.newInstance(args);
                _env.putElement(self.hashCode(), (Element)obj);
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            self.setPrototype(_proto);
        }
    }

    public static JavaVoidCallback generateClass(IExecutionEnvironment env, V8 v8, Class<?> cls, Class<?> ctorArgs[]) {
        try {
            V8Object prototype = new V8Object(v8);

            generatePrototype(env, v8, prototype, cls);
            Constructor<?> ctor = cls.getConstructor(ctorArgs);
            Log.i(TAG, "wrapping ctor of " + cls.getName());
            return new ConstructorWrapper(env, prototype, ctor);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return null;
    }
}
