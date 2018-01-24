package com.pureqml.android.runtime;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

import com.eclipsesource.v8.V8Function;
import com.pureqml.android.IExecutionEnvironment;
import com.pureqml.android.TextLayout;
import com.pureqml.android.TextLayoutCallback;

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
            default:
                super.setStyle(name, value);
                return;
        }
        update();
    }

    @Override
    protected Rect getEffectiveRect() {
        return _rect;
    }

    @Override
    public void paint(Canvas canvas, int baseX, int baseY, float opacity) {
        if (!_visible)
            return;

        float textSize = _paint.getTextSize();
        float lineHeight = textSize * 1.2f; //fixme: support proper line height/baseline
        float x = baseX + _rect.left;
        float y = baseY + _rect.top + lineHeight;
        if (_layout == null) {
            canvas.drawText(_text, x, y, _paint);
        } else {
            //fixme: stub
            for ( TextLayout.Stripe stripe : _layout.stripes) {
                canvas.drawText(_layout.text, stripe.start, stripe.end, x, y, _paint);
                y += lineHeight;
            }
        }
        super.paint(canvas, baseX, baseY, opacity);
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
