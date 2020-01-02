package com.pureqml.android.runtime;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.text.InputType;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.pureqml.android.IExecutionEnvironment;

public class Input extends Element {
    public static final String TAG = "Input";

    String                          value = new String();
    String                          placeholder = new String();
    String                          inputmode = new String();
    String                          autocomplete = new String();
    String                          type = new String("text");
    EditText                        view;
    Handler                         handler;
    RelativeLayout.LayoutParams     layoutParams;

    public Input(IExecutionEnvironment env) {
        super(env);

        Context context = env.getContext();
        view = new EditText(this, context);
        handler = new Handler(context.getMainLooper());
    }

    public void discard() {
        super.discard();
        final ViewGroup rootView = _env.getRootView();
        if (rootView != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    view.setVisibility(View.GONE);
                    rootView.removeView(view);
                }
            });
        } else
            Log.w(TAG, "no root view...");
    }
    private void updateType() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= 26) {
                    if (autocomplete.equals("username"))
                        view.setAutofillHints(View.AUTOFILL_HINT_USERNAME);
                    else if (autocomplete.equals("current-password"))
                        view.setAutofillHints(View.AUTOFILL_HINT_PASSWORD);
                    else
                        Log.w(TAG, "ignoring autocomplete hint " + autocomplete);
                }
                if (type.equals("password")) {
                    view.setInputType(inputmode.equals("numeric") ?
                            InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD :
                            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                } else {
                    view.setInputType(inputmode.equals("numeric") ?
                            InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_NORMAL :
                            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
                }
            }
        });
    }

    @Override
    public void setAttribute(String name, String value) {
        if (value == null)
            throw new NullPointerException("setAttribute " + name);

        if (name.equals("placeholder")) {
            placeholder = value;
            view.setHint(placeholder);
        } else if (name.equals("value")) {
            this.value = value;
        } else if (name.equals("inputmode")) {
            inputmode = value;
            updateType();
        } else if (name.equals("type")) {
            type = value;
        } else if (name.equals("autocomplete")) {
            autocomplete = value;
        } else
            super.setAttribute(name, value);
    }

    @Override
    public String getAttribute(String name) {
        if (name.equals("placeholder"))
            return placeholder;
        else if (name.equals("value"))
            return value;
        else if (name.equals("type"))
            return type;
        else if (name.equals("inputmode"))
            return inputmode;
        else if (name.equals("autocomplete"))
            return autocomplete;
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
                RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(_rect.width(), _rect.height());
                lp.leftMargin = rect.left;
                lp.topMargin = rect.top;
                Log.d(TAG, "layout params = " + lp.debug("RelativeLayout.LayoutParams"));

                synchronized (this) {
                    if (!lp.equals(layoutParams)) {
                        Log.i(TAG, "installing new layout params");
                        layoutParams = lp;
                    } else
                        lp = null;
                }
                if (lp != null)
                    updateViewState(lp);
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

    private void updateViewState(final ViewGroup.LayoutParams lp) {
        Log.d(TAG, "adding view to layout: " + lp.toString());
        final ViewGroup rootView = _env.getRootView();
        boolean visible;
        synchronized (this) {
            visible = _globallyVisible;
        }
        if (visible) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (view.getParent() == null) {
                        view.setVisibility(View.VISIBLE);
                        if (lp != null)
                            rootView.addView(view, lp);
                        else
                            rootView.addView(view);
                    } else {
                        if (lp != null)
                            rootView.updateViewLayout(view, lp);
                    }
                }});
        } else {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "removing view to layout...");
                    view.setVisibility(View.GONE);
                    rootView.removeView(view);
                }
            });
        }
    }

    private void updateVisibility(boolean value) {
        final ViewGroup.LayoutParams lp;
        synchronized (this) {
            lp = layoutParams;
        }
        updateViewState(lp);
    }

    @Override
    protected void onGloballyVisibleChanged(boolean value) {
        Log.d(TAG, "onGloballyVisibleChanged " + value);
        super.onGloballyVisibleChanged(value);
        updateVisibility(value);
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
