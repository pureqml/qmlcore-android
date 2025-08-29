package com.pureqml.android.runtime;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import com.pureqml.android.IExecutionEnvironment;
import com.pureqml.android.IRenderer;
import com.pureqml.android.SafeRunnable;
import com.pureqml.android.TypeConverter;

public final class Input extends Element {
    public static final String TAG = "Input";

    String                          value = "";
    String                          placeholder = "";
    String                          inputmode = "";
    String                          autocomplete = "";
    String                          type = "text";
    final EditText                  view;
    final ViewHolder<EditText>      viewHolder;

    public Input(IExecutionEnvironment env) {
        super(env);

        Context context = env.getContext();
        view = new EditText(context);
        viewHolder = new ViewHolder<>(view);

        view.setOnFocusChangeListener((v, hasFocus) -> _env.getExecutor().execute(new SafeRunnable() {
            @Override
            public void doRun() {
                if (v == view) {
                    Log.i(TAG, "on focus changed: " + hasFocus + " " + v.isFocused());
                    Input.this.emitFocusEvent(hasFocus);
                }
            }
        }));

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
        view.setOnEditorActionListener((v, actionId, event) -> {
            Log.d(TAG, "onEditorAction: " + actionId);
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                Log.i(TAG, "IME_ACTION_DONE");
                _env.blockUiInput(false);
                _env.focusView(v, false);
                Input.this.emitChangeEvent();
            }
            return false;
        });
    }

    String getTag() { return "input"; }

    private void setValue(String value) {
        this.value = value;
        _env.getExecutor().execute(new SafeRunnable() {
            @Override
            public void doRun() {
                Input.this.emit(null, "input");
            }
        });
    }

    @Override
    public void discard() {
        super.discard();
        viewHolder.discard(_env.getRootView());
    }

    private void updateNativeInputType() {
        _env.getRootView().post(new SafeRunnable() {
            @Override
            public void doRun() {
                if (Build.VERSION.SDK_INT >= 26) {
                    if (autocomplete.equals("username"))
                        view.setAutofillHints(View.AUTOFILL_HINT_USERNAME);
                    else if (autocomplete.equals("current-password"))
                        view.setAutofillHints(View.AUTOFILL_HINT_PASSWORD);
                    else if (!autocomplete.isEmpty())
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

        switch (name) {
            case "placeholder":
                placeholder = value;
                _env.getRootView().post(new SafeRunnable() {
                    @Override
                    public void doRun() {
                        view.setHint(placeholder);
                    }
                });
                break;
            case "value":
                this.value = value;
                _env.getRootView().post(new SafeRunnable() {
                    @Override
                    public void doRun() {
                        view.setText(value);
                    }
                });
                break;
            case "inputmode":
                inputmode = value;
                updateNativeInputType();
                break;
            case "type":
                type = value;
                updateNativeInputType();
                break;
            case "autocomplete":
                autocomplete = value;
                updateNativeInputType();
                break;
            default:
                super.setAttribute(name, value);
                break;
        }
    }

    @Override
    public String getAttribute(String name) {
        switch (name) {
            case "placeholder":
                return placeholder;
            case "value":
                return value;
            case "type":
                return type;
            case "inputmode":
                return inputmode;
            case "autocomplete":
                return autocomplete;
            default:
                return super.getAttribute(name);
        }
    }

    @Override
    public boolean sendEvent(int id, int x, int y, final MotionEvent event) {
        if (!getRect().contains(x, y))
            return false;

        _env.getRootView().post(new SafeRunnable() {
            @Override
            public void doRun() {
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
        _env.getRootView().post(new SafeRunnable() {
            @Override
            public void doRun() {
//                view.clearFocus(); //this will trigger refocus and infinite focus loop
                view.setCursorVisible(false);
            }
        });
        _env.focusView(view, false);
    }

    @Override
    public void focus() {
        Log.i(TAG, "focusing input...");
        super.focus();
        _env.getRootView().post(new SafeRunnable() {
            @Override
            public void doRun() {
                view.setCursorVisible(true);
                if (!view.requestFocus())
                    Log.w(TAG, "requestFocus failed");
            }
        });
        _env.focusView(view, true);
    }

    private void emitFocusEvent(final boolean hasFocus) {
        _env.getExecutor().execute(new SafeRunnable() {
            @Override
            public void doRun() {
                emit(null, hasFocus? "focus": "blur");
            }
        });
    }

    private void emitChangeEvent() {
        _env.getExecutor().execute(new SafeRunnable() {
            @Override
            public void doRun() {
                emit(null, "change");
            }
        });
    }

    @Override
    public void paint(PaintState state) {
        beginPaint(state);
        paintChildren(state);

        Rect rect = getRect();

        if (!rect.isEmpty()) {
            rect.offsetTo(state.baseX, state.baseY);
            Log.i(TAG, "input layout " + rect);
            viewHolder.setRect(_env.getRootView(), rect);
        }

        endPaint(state);
    }

    @Override
    protected void setStyle(String name, Object value) {
        switch(name)
        {
            case "color": {
                final int color = TypeConverter.toColor(value);
                _env.getRootView().post(new SafeRunnable() {
                    @Override
                    public void doRun() {
                        view.setTextColor(color);
                    }
                });
                break;
            }
            case "font-size": {
                try {
                    IRenderer renderer = _env.getRenderer();
                    if (renderer != null) {
                        final int fontSize = TypeConverter.toFontSize(value.toString(), renderer.getDisplayMetrics());
                        _env.getRootView().post(new SafeRunnable() {
                            @Override
                            public void doRun() {
                                view.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize);
                            }
                        });
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "invalid font-size: ", ex);
                }
                break;
            }

            case "background":
            case "background-color": {
                final int color = TypeConverter.toColor(value);
                _env.getRootView().post(new SafeRunnable() {
                    @Override
                    public void doRun() {
                        view.setBackgroundColor(color);
                    }
                });
                break;
            }
            case "-pure-placeholder-color":
                final int color = TypeConverter.toColor(value);
                _env.getRootView().post(new SafeRunnable() {
                    @Override
                    public void doRun() {
                        view.setHintTextColor(color);
                    }
                });
                break;

            default:
                super.setStyle(name, value);
                return;
        }
        update();
    }
}
