package com.pureqml.android.runtime;

import android.util.Log;

import com.eclipsesource.v8.JavaCallback;
import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Object;

public class Element extends V8Object {
    public static final String TAG = "rt.Element";

    public Element(V8 v8) {
        super(v8);
    }

    public void append(Element el) {
        Log.i(TAG, "append element!");
    }

    public static final class Constructor extends JavaCallbackWithRuntime {
        public Constructor(V8 v8, V8Object prototype) {
            super(v8, prototype);
        }

        @Override
        public Object invoke(V8Object self, V8Array arguments) {
            Log.i(TAG, "CTOR INVOCATION " + arguments.length());
            V8Object el = new Element(_v8);
            el.setPrototype(_prototype);
            return el;
        }
    }
}
