package com.pureqml.android.runtime;

import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.Log;

import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.pureqml.android.IExecutionEnvironment;
import com.pureqml.android.TypeConverter;

public final class Text extends Element {

    enum Wrap {
        NoWrap,
        Wrap
    };

    enum HorizontalAlignment {
        AlignLeft, AlignRight, AlignHCenter, AlignJustify
    };

    enum VerticalAlignment {
        AlignTop, AlignBottom, AlignVCenter
    };

    private final static String TAG = "rt.Text";
    Paint               _paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    String              _text;
    TextLayout          _layout;
    Wrap                _wrap = Wrap.NoWrap;
    HorizontalAlignment _halign = HorizontalAlignment.AlignLeft;
    VerticalAlignment   _valign = VerticalAlignment.AlignTop;

    public Text(IExecutionEnvironment env) {
        super(env);
    }

    @Override
    protected void setStyle(String name, Object value) {
        switch(name) {
            case "color":
                _paint.setColor(TypeConverter.toColor((String)value));
                break;
            case "font-size":
                try {
                    _paint.setTextSize(TypeConverter.toFontSize((String)value, _env.getDisplayMetrics()));
                } catch (Exception e) {
                    Log.w(TAG, "set font-size failed", e);
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

            case "text-align":
                switch((String)value) {
                    case "left":
                        _halign = HorizontalAlignment.AlignLeft;
                        break;
                    case "center":
                        _halign = HorizontalAlignment.AlignHCenter;
                        break;
                    case "right":
                        _halign = HorizontalAlignment.AlignRight;
                        break;
                    case "justify":
                        _halign = HorizontalAlignment.AlignJustify;
                        break;
                    default:
                        Log.w(TAG, "ignoring text-align value " + value);
                }
                break;

            case "-pure-text-vertical-align":
                switch((String)value) {
                    case "top":
                        _valign = VerticalAlignment.AlignTop;
                        break;
                    case "middle":
                        _valign = VerticalAlignment.AlignVCenter;
                        break;
                    case "bottom":
                        _valign = VerticalAlignment.AlignBottom;
                        break;
                    default:
                        Log.w(TAG, "ignoring vertical-text-align value " + value);
                }
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
            Rect rect = getRect();
            _lastRect.left = state.baseX + rect.left;
            _lastRect.top = state.baseY + rect.top; //fixme: get actual bounding box

            float textSize = _paint.getTextSize();
            float lineHeight = textSize * 1.2f; //fixme: support proper line height/baseline
            float x = _lastRect.left;
            float y = _lastRect.top - _paint.ascent();
            if (_layout == null) {
                state.canvas.drawText(_text, x, y, _paint);
                y += lineHeight;
            } else {
                switch (_halign) {
                    case AlignHCenter:
                        x += (rect.width() - _layout.width) / 2;
                        break;
                    case AlignRight:
                        x += rect.width() - _layout.width;
                        break;
                }
                switch (_valign) {
                    case AlignVCenter:
                        y += (rect.height() - _layout.height) / 2;
                        break;
                    case AlignBottom:
                        y += rect.height() - _layout.height;
                        break;
                }
                Log.v(TAG, "paint: " + _layout + ", halign: " + _halign + ", valign: "  + _valign + ", rect: " + rect);
                for (TextLayout.Stripe stripe : _layout.stripes) {
                    state.canvas.drawText(_layout.text, stripe.start, stripe.end, x, y, _paint);
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

    public void layoutText(V8Function callback) {
        Log.v(TAG, "layout text: " + _text);
        int offset = 0, length = _text.length();
        int lines = 0;
        _layout = new TextLayout(_text);

        if (_wrap == Wrap.Wrap) {
            while(offset < length) {
                float measuredWidth[] = new float[1];
                int n = _paint.breakText(_text, offset, length, true, getRect().width(), measuredWidth);
                _layout.add(offset, offset + n, (int)measuredWidth[0]);
                offset += n;
                ++lines;
            }
        } else {
            int width = (int)_paint.measureText(_text);
            _layout.add(0, _text.length(), width);
            lines = 1;
        }
        _layout.height = (int) (lines * _paint.getTextSize());

        V8Object metrics = new V8Object(_env.getRuntime());
        metrics.add("width", _layout.width);
        metrics.add("height", _layout.height);

        V8Array args = new V8Array(_env.getRuntime());
        args.push(metrics);
        callback.call(null, args);
        args.close();

        metrics.close();
        callback.close();
        update();
    }
}
