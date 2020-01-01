package com.pureqml.android.runtime;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.Log;

import com.eclipsesource.v8.V8Function;
import com.pureqml.android.IExecutionEnvironment;

public class Text extends Element {
    private final static String TAG = "rt.Text";
    Paint       _paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    String      _text;
    TextLayout  _layout;

    public Text(IExecutionEnvironment env) {
        super(env);
    }

    protected void setStyle(String name, Object value) throws Exception {
        switch(name) {
            case "color":           _paint.setColor(TypeConverter.toColor((String)value)); break;
            case "font-size":       _paint.setTextSize(TypeConverter.toFontSize((String)value, _env.getDisplayMetrics())); break;
            case "font-weight":
                if (value.equals("bold"))
                    _paint.setTypeface(Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD));
                else
                    Log.v(TAG, "ignoring font-weight " + value);
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
        if (_visible && _text != null) {
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
            paintChildren(state);
        }
        endPaint();
    }

    public void setText(String text) {
        //Log.i(TAG, "setText " + text);
        _text = text;
    }

    public void layoutText(V8Function callback) {
        _env.layoutText(_text, _rect, new TextLayoutCallback() {
            @Override
            public void onTextLayedOut(TextLayout layout) {
                _layout = layout;

                update();
            }
        });
    }
}
