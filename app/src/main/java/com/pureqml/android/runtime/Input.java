package com.pureqml.android.runtime;

import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.pureqml.android.IExecutionEnvironment;

public class Input extends Element {
    public static final String TAG = "Input";

    String      value = new String();
    String      placeholder = new String();
    EditText    view;
    Handler     handler;

    public Input(IExecutionEnvironment env) {
        super(env);

        Context context = env.getContext();
        view = new EditText(this, context);
        handler = new Handler(context.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "adding new Input to root view...");
                _env.getRootView().addView(view);
            }
        });
    }

    public void discard() {
        super.discard();
        final ViewGroup rootView = _env.getRootView();
        if (rootView != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    rootView.removeView(view);
                }
            });
        } else
            Log.w(TAG, "no root view...");
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
            final Rect rect = getScreenRect();
            Log.i(TAG, "input layout " + rect.toString());
            final ViewGroup rootView = _env.getRootView();
            if (rootView != null) {
                final RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(_rect.width(), _rect.height());
                lp.leftMargin = rect.left;
                lp.topMargin = rect.top;
                Log.d(TAG, "layout params = " + lp.debug("RelativeLayout.LayoutParams"));
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "updating view layout");
                        rootView.updateViewLayout(view, lp);
                    }
                });
            }
        }
    }

    @Override
    public boolean sendEvent(String name, int x, int y, final MotionEvent event) {
        if (!_rect.contains(x, y))
            return false;

        handler.post(new Runnable() {
            @Override
            public void run() {
                view.onTouchEvent(event);
            }
        });
        return true;
    }

    @Override
    public void paint(PaintState state) {
        beginPaint();
        if (_visible && !_rect.isEmpty()) {
//            final Rect rect = getScreenRect();
//            Log.i(TAG, "drawing " + view.toString() + " at " + _rect.toString());
//
//            state.canvas.save();
//            state.canvas.translate(rect.left, rect.top);
//            view.draw(state.canvas);
//            view.debug(0);
//            state.canvas.restore();
        }
        endPaint();
    }
    @Override
    public void blur() {
        Log.i(TAG, "removing focus...");
        super.blur();
        handler.post(new Runnable() {
            @Override
            public void run() {
                view.clearFocus();
            }
        });
    }

    @Override
    public void focus() {
        Log.i(TAG, "focusing input...");
        super.focus();
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!view.requestFocus())
                    Log.w(TAG, "requestFocus failed");
            }
        });
    }
}
