package com.pureqml.android.runtime;

import android.util.Log;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.pureqml.android.IExecutionEnvironment;

public class Input extends Element {
    public static final String TAG = "Input";

    String value = new String();
    String placeholder = new String();
    TextView view;

    public Input(IExecutionEnvironment env) {
        super(env);

        view = new TextView(env.getContext());
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
            Log.i(TAG, "input layout " + _rect.toString());
            view.setLayoutParams(new RelativeLayout.LayoutParams(_rect.width(), _rect.height()));
            Log.i(TAG, "laying out " + view.toString());
            view.layout(_rect.left, _rect.top, _rect.right, _rect.bottom);
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
        if (!view.requestFocus())
            Log.w(TAG, "requestFocus failed");
    }
}
