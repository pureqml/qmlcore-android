package com.pureqml.android.runtime;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.Log;

import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.pureqml.android.IExecutionEnvironment;

public final class Text extends Element {

    enum Wrap {
        NoWrap,
        Wrap
    };

    private final static String TAG = "rt.Text";
    Paint       _paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    String      _text;
    TextLayout  _layout;
    Wrap        _wrap = Wrap.NoWrap;

    public Text(IExecutionEnvironment env) {
        super(env);
    }

    @Override
    protected void setStyle(String name, Object value) {
        switch(name) {
            case "color":           _paint.setColor(TypeConverter.toColor((String)value)); break;
            case "font-size":
                try {
                    _paint.setTextSize(TypeConverter.toFontSize((String)value, _env.getDisplayMetrics()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case "font-weight":
                if (value.equals("bold"))
                    _paint.setTypeface(Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD));
                else
                    Log.v(TAG, "ignoring font-weight " + value);
                break;

            case "white-space":
                if (value.equals("pre") || value.equals("nowrap"))
                    _wrap = Wrap.NoWrap;
                else if (value.equals("pre-wrap") || value.equals("normal"))
                    _wrap = Wrap.Wrap;
                else
                    Log.w(TAG, "unsupported white-space rule");
                break;

            default:
                super.setStyle(name, value);
                return;
        }
        update();
    }

    @Override
    public void paint(PaintState state) {
        beginPaint();
        if (_text != null) {
            _lastRect.left = state.baseX + _rect.left;
            _lastRect.top = state.baseY + _rect.top; //fixme: get actual bounding box

            float textSize = _paint.getTextSize();
            float lineHeight = textSize * 1.2f; //fixme: support proper line height/baseline
            float x = _lastRect.left;
            float y = _lastRect.top + lineHeight;
            int r;
            if (_layout == null) {
                r = _lastRect.left + (int) Math.round(_paint.measureText(_text, 0, _text.length()));
                if (r > _lastRect.right)
                    _lastRect.right = r;
                state.canvas.drawText(_text, x, y, _paint);
                y += lineHeight;
            } else {
                //fixme: stub
                for (TextLayout.Stripe stripe : _layout.stripes) {
                    state.canvas.drawText(_layout.text, stripe.start, stripe.end, x, y, _paint);
                    r = _lastRect.left + (int) Math.round(_paint.measureText(_text, stripe.start, stripe.end) + _lastRect.left);
                    if (r > _lastRect.right)
                        _lastRect.right = r;
                    y += lineHeight;
                }
            }
            _lastRect.bottom = (int) (y - lineHeight);
        }
        paintChildren(state);
        endPaint();
    }

    public void setText(String text) {
        //Log.i(TAG, "setText " + text);
        _text = text;
    }

    public void layoutText(V8Object options, V8Function callback) {
        boolean wrap = true;
        _env.layoutText(_text, _rect, wrap, new TextLayoutCallback() {
            @Override
            public void onTextLayedOut(TextLayout layout) {
                _layout = layout;

                update();
            }
        });
    }
}
