package com.pureqml.android.runtime;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.Log;

import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.pureqml.android.IExecutionEnvironment;
import com.pureqml.android.IRenderer;
import com.pureqml.android.TypeConverter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Text extends Element {

    enum Wrap {
        NoWrap,
        Wrap
    }

    enum HorizontalAlignment {
        AlignLeft, AlignRight, AlignHCenter, AlignJustify
    }

    enum VerticalAlignment {
        AlignTop, AlignBottom, AlignVCenter
    }

    private final static String TAG = "rt.Text";
    final Paint         _paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    String              _text;
    TextLayout          _layout;
    Wrap                _wrap = Wrap.NoWrap;
    boolean             _wrapAnywhere;
    HorizontalAlignment _halign = HorizontalAlignment.AlignLeft;
    VerticalAlignment   _valign = VerticalAlignment.AlignTop;
    int                 _cachedWidth = -1;

    public Text(IExecutionEnvironment env) {
        super(env);
    }

    static private final Pattern textShadowPattern = Pattern.compile("(\\d+)px\\s*(\\d+)px\\s*(\\d+)px\\s*rgba\\(\\s*(\\d+)\\s*,(\\d+)\\s*,(\\d+)\\s*,([\\d.]+)\\s*\\)");

    private void resetLayout() { _layout = null; }

    @Override
    protected void setStyle(String name, Object value) {
        switch(name) {
            case "color":
                _paint.setColor(TypeConverter.toColor(value));
                break;
            case "font-size":
                try {
                    IRenderer renderer = _env.getRenderer();
                    if (renderer != null)
                        _paint.setTextSize(TypeConverter.toFontSize((String)value, renderer.getDisplayMetrics()));
                    else
                        Log.w(TAG, "no renderer, font-size ignored");
                } catch (Exception e) {
                    Log.w(TAG, "set font-size failed", e);
                }
                break;
            case "font-weight":
                if (value.equals("bold") || ((value instanceof Integer) && (int)value >= 600))
                    _paint.setTypeface(Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD));
                else
                    Log.v(TAG, "ignoring font-weight " + value);
                break;

            case "white-space":
                if (value.equals("pre") || value.equals("nowrap"))
                    setWrap(Wrap.NoWrap);
                else if (value.equals("pre-wrap") || value.equals("normal"))
                    setWrap(Wrap.Wrap);
                else
                    Log.w(TAG, "unsupported white-space rule");
                break;

            case "word-break":
                _wrapAnywhere = value.equals("break-all");
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
            case "text-shadow":
                Matcher matcher = textShadowPattern.matcher(value.toString());
                if (matcher.matches()) {
                    int dx = Integer.valueOf(matcher.group(1));
                    int dy = Integer.valueOf(matcher.group(2));
                    int r = Integer.valueOf(matcher.group(4));
                    int g = Integer.valueOf(matcher.group(5));
                    int b = Integer.valueOf(matcher.group(6));
                    float a = Float.valueOf(matcher.group(7));
                    if ((dx | dy) != 0 && a > 0) {
                        _paint.setShadowLayer((float)Math.hypot(dx, dy), dx, dy, Color.argb((int)(255 * a), r, g, b));
                    } else
                        _paint.clearShadowLayer();
                } else
                    Log.w(TAG, "unsupported text shadow spec: " + value.toString());
                break;

            default:
                super.setStyle(name, value);
                return;
        }
        update();
    }

    void update() {
        _cachedWidth = -1;
        super.update();
    }

    @Override
    public void paint(PaintState state) {
        beginPaint(state);
        if (_text != null) {
            Rect rect = getRect();
            float textSize = _paint.getTextSize();
            float lineHeight = textSize; //fixme: support proper line height/baseline
            final int ascent = (int)Math.ceil(-_paint.ascent()); //it's negative, we want positive
            float x = state.baseX;
            float y = state.baseY + ascent;
            if (_layout == null) {
                //simple layoutless line
                if (_cachedWidth < 0) {
                    _cachedWidth = (int)_paint.measureText(_text);
                }

                switch (_halign) {
                    case AlignHCenter:
                        x += (rect.width() - _cachedWidth) / 2;
                        break;
                    case AlignRight:
                        x += rect.width() - _cachedWidth;
                        break;
                }
                switch (_valign) {
                    case AlignVCenter:
                        y += (rect.height() - lineHeight) / 2;
                        break;
                    case AlignBottom:
                        y += rect.height() - lineHeight;
                        break;
                }
                _lastRect.left = (int)x;
                _lastRect.right = (int)x + _cachedWidth;
                _lastRect.top = (int)y - ascent;

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
                //Log.v(TAG, "paint: " + _layout + ", halign: " + _halign + ", valign: "  + _valign + ", rect: " + rect);
                for (TextLayout.Stripe stripe : _layout.stripes) {
                    state.canvas.drawText(_layout.text, stripe.start, stripe.end, x, y, _paint);
                    y += lineHeight;
                }
            }
            _lastRect.bottom = (int) (y - lineHeight + _paint.descent());
        }
        paintChildren(state);
        endPaint();
    }

    public void setText(String text) {
        //Log.i(TAG, "setText " + text);
        if (text.equals(_text))
            return;

        _text = text;
        updateLayout();
        //enableCache(text != null && text.length() != 0);
        update();
    }

    static private final Pattern newLinePattern = Pattern.compile("<br.*?>");

    private String preprocess(String text) {
        return text.replaceAll("\\s+", " ");
    }

    private void layoutLine(int begin, int end, int maxWidth) {
        if (_wrap == Wrap.Wrap) {
            _layout.wrap(_paint, begin, end, maxWidth);
        } else {
            int width = (int)_paint.measureText(_layout.text);
            _layout.add(begin, end, width);
        }
    }

    private void setWrap(Wrap wrap) {
        _wrap = wrap;
        updateLayout();
    }

    private void updateLayout() {
        if (_wrap == Wrap.Wrap)
            layoutText();
        else
            resetLayout();
    }

    private void layoutText() {
        if (_text == null)
            return;

        _layout = new TextLayout(preprocess(_text));
        Rect rect = getRect();

        Matcher matcher = newLinePattern.matcher(_layout.text);
        int begin = 0;
        while(matcher.find()) {
            layoutLine(begin, matcher.start(), rect.width());
            begin = matcher.end();
        }
        layoutLine(begin, _layout.text.length(), rect.width());

        _layout.height = (int) (_layout.stripes.size() * _paint.getTextSize());
    }

    public void layoutText(V8Function callback) {
//        Log.v(TAG, "layout text: " + _text);
        layoutText();

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
