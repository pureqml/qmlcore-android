package com.pureqml.android.runtime;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.pureqml.android.IExecutionEnvironment;

public final class Input extends Element {
    public static final String TAG = "Input";

    String                          value = new String();
    String                          placeholder = new String();
    String                          inputmode = new String();
    String                          autocomplete = new String();
    String                          type = new String("text");
    EditText                        view;
    ViewHolder<EditText>            viewHolder;

    public Input(IExecutionEnvironment env) {
        super(env);

        Context context = env.getContext();
        view = new EditText(context);
        viewHolder = new ViewHolder<EditText>(context, view);

        view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, final boolean hasFocus) {
                _env.getExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        Input.this.emitFocusEvent(hasFocus);
                    }
                });
            }
        });
        view.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                Input.this.setValue(s.toString());
            }
        });
    }

    private void setValue(String value) {
        this.value = value;
        _env.getExecutor().execute(new Runnable() {
            @Override
            public void run() {
                Input.this.emit(null, "input");
            }
        });
    }

    public void discard() {
        super.discard();
        viewHolder.discard(_env.getRootView());
    }

    private void updateType() {
        viewHolder.getHandler().post(new Runnable() {
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
        if (!_rect.isEmpty() && _globallyVisible) {
            final Rect rect = getScreenRect();
            Log.i(TAG, "input layout " + rect.toString());
            viewHolder.setRect(_env.getRootView(), rect);
        }
    }

    @Override
    public boolean sendEvent(String name, int x, int y, final MotionEvent event) {
        if (!_rect.contains(x, y))
            return false;

        viewHolder.getHandler().post(new Runnable() {
            @Override
            public void run() {
                view.onTouchEvent(event);
            }
        });
        return true;
    }

    private void updateVisibility(boolean value) {
        viewHolder.update(_env.getRootView(), value);
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
        viewHolder.getHandler().post(new Runnable() {
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
        viewHolder.getHandler().post(new Runnable() {
            @Override
            public void run() {
                if (!view.requestFocus())
                    Log.w(TAG, "requestFocus failed");
            }
        });
    }

    private void emitFocusEvent(final boolean hasFocus) {
        _env.getExecutor().execute(new Runnable() {
            @Override
            public void run() {
                emit(null, hasFocus? "focus": "blur");
            }
        });
    }
}
