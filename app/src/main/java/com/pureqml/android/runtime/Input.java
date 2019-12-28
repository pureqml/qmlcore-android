package com.pureqml.android.runtime;

import com.pureqml.android.IExecutionEnvironment;

public class Input extends Element {
    String value = new String();
    String placeholder = new String();

    public Input(IExecutionEnvironment env) {
        super(env);
    }

    @Override
    public void setAttribute(String name, String value) {
        if (value == null)
            throw new NullPointerException("setAttribute " + name);

        if (name.equals("placeholder"))
            placeholder = value;
        else if (name.equals("value"))
            this.value = value;
        else
            super.setAttribute(name, value);
    }

    @Override
    public String getAttribute(String name) {
        if (name.equals("placeholder"))
            return placeholder;
        else if (name.equals("value"))
            return value;
        else
            return super.getAttribute(name);
    }
}
