package com.pureqml.android.runtime;

import android.graphics.Rect;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.EditText;

import com.pureqml.android.IExecutionEnvironment;

public class Input extends Element {
    public static final String TAG = "Input";

    String value = new String();
    String placeholder = new String();
    EditText view;

    public Input(IExecutionEnvironment env) {
        super(env);

        view = new EditText(env.getContext());
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
    }

    @Override
    public void setAttribute(String name, String value) {
        if (value == null)
            throw new NullPointerException("setAttribute " + name);

        if (name.equals("placeholder")) {
            placeholder = value;
            view.setHint(placeholder);
        } else if (name.equals("value"))
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

    @Override
    void update() {
        super.update();
        if (!_rect.isEmpty()) {
            Rect rect = getScreenRect();
            Log.i(TAG, "input layout " + rect.toString());
            view.setLayoutParams(new ViewGroup.LayoutParams(_rect.width(), _rect.height()));
            Log.i(TAG, "laying out " + view.toString());
            view.layout(rect.left, rect.top, rect.right, rect.bottom);
            view.debug(0);
        }
    }

    @Override
    public void paint(PaintState state) {
        beginPaint();
        if (_visible && !_rect.isEmpty()) {
            Log.i(TAG, "drawing " + view.toString());
            view.draw(state.canvas);
        }
        endPaint();
    }
    @Override
    public void blur() {
        Log.i(TAG, "removing focus...");
        super.blur();
        view.clearFocus();
    }

    @Override
    public void focus() {
        Log.i(TAG, "focusing input...");
        super.focus();
        try {
            if (!view.requestFocus())
                Log.w(TAG, "requestFocus failed");
        } catch (Exception ex) {
            Log.e(TAG, "requestFocus failed", ex);
        }
    }
}
