package com.pureqml.android.runtime;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.Log;

import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.pureqml.android.ComputedStyle;
import com.pureqml.android.Font;
import com.pureqml.android.IExecutionEnvironment;
import com.pureqml.android.IRenderer;
import com.pureqml.android.TypeConverter;

import java.util.Objects;
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
    Paint               _paint;
    String              _text;
    TextLayout          _layout;
    Wrap                _wrap = Wrap.NoWrap;
    boolean             _wrapAnywhere;
    HorizontalAlignment _halign = HorizontalAlignment.AlignLeft;
    VerticalAlignment   _valign = VerticalAlignment.AlignTop;
    int                 _cachedWidth = -1;

    String              _fontFamily = null;
    int                 _fontWeight = 0;
    boolean             _fontItalic = false;
    final float               _lineHeight = ComputedStyle.DefaultLineHeight;

    public Text(IExecutionEnvironment env) {
        super(env);
        updateTypeface();
    }

    static private final Pattern textShadowPattern = Pattern.compile("(\\d+)px\\s*(\\d+)px\\s*(\\d+)px\\s*rgba\\(\\s*(\\d+)\\s*,(\\d+)\\s*,(\\d+)\\s*,([\\d.]+)\\s*\\)");

    private void resetLayout() { _layout = null; }

    @Override
    protected void addClass(ComputedStyle style) {
        super.addClass(style);
        updateTypeface();
    }

    private void updateTypeface() {
        if (_paint == null)
            _paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        String fontFamily = _fontFamily != null? _fontFamily: _style != null? _style.fontFamily: null;
        int fontWeight = _fontWeight > 0? _fontWeight: _style != null? _style.fontWeight: ComputedStyle.NormalWeight;
        Typeface tf = _env.getTypeface(fontFamily, fontWeight, _fontItalic);
        _paint.setTypeface(tf);
        update();
    }

    @Override
    protected void setStyle(String name, Object value) {
        switch(name) {
            case "color":
                _paint.setColor(TypeConverter.toColor(value));
                break;
            case "font-family": {
                    Font family = Font.parse(value.toString());
                    _fontFamily = family.family;
                    if (family.weight > 0)
                        _fontWeight = family.weight;
                    if (_fontFamily.isEmpty())
                        _fontFamily = null;
                }
                updateTypeface();
                break;
            case "font-style":
                _fontItalic = value.toString().equals("italic");
                updateTypeface();
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
                _fontWeight = ComputedStyle.parseFontWeight(value);
                updateTypeface();
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
                    int dx = Integer.parseInt(Objects.requireNonNull(matcher.group(1)));
                    int dy = Integer.parseInt(Objects.requireNonNull(matcher.group(2)));
                    int r = Integer.parseInt(Objects.requireNonNull(matcher.group(4)));
                    int g = Integer.parseInt(Objects.requireNonNull(matcher.group(5)));
                    int b = Integer.parseInt(Objects.requireNonNull(matcher.group(6)));
                    float a = Float.parseFloat(Objects.requireNonNull(matcher.group(7)));
                    if ((dx | dy) != 0 && a > 0) {
                        _paint.setShadowLayer((float)Math.hypot(dx, dy), dx, dy, Color.argb((int)(255 * a), r, g, b));
                    } else
                        _paint.clearShadowLayer();
                } else
                    Log.w(TAG, "unsupported text shadow spec: " + value);
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

    private void layout() {
        if (_text == null || _layout != null)
            return;

        if (_wrap == Wrap.Wrap)
            layoutText();

        if (_layout == null && _cachedWidth < 0) {
            _cachedWidth = (int)_paint.measureText(_text);
        }
    }

    @Override
    protected Rect createRedrawRect() {
        if (!_globallyVisible)
            return super.createRedrawRect();

        layout();
        Rect rect = getScreenRect();
        if (_layout != null) {
            int right = rect.left + _layout.width;
            int bottom = rect.top + (int)Math.ceil(_paint.getTextSize() * _layout.stripes.size() + _paint.getFontMetrics().bottom);
            if (rect.right < right)
                rect.right = right;
            if (rect.bottom < bottom)
                rect.bottom = bottom;
        } else {
            int right = rect.left + _cachedWidth;
            int bottom = rect.top + (int)Math.ceil(_paint.getTextSize() + _paint.getFontMetrics().bottom);
            if (rect.right < right)
                rect.right = right;
            if (rect.bottom < bottom)
                rect.bottom = bottom;
        }

        return rect;
    }

    @Override
    public void paint(PaintState state) {
        layout();
        beginPaint(state);
        if (_text != null) {
            Rect rect = getRect();
            float textSize = _paint.getTextSize();
            float lineHeight = (_style != null? (_style.lineHeight != null? _style.lineHeight: _lineHeight): _lineHeight) * textSize;
            final int ascent = (int)Math.ceil(-_paint.ascent()); //it's negative, we want positive
            float x = state.baseX;
            float y = state.baseY + ascent;
            if (_layout == null) {
                //simple layoutless line

                switch (_halign) {
                    case AlignHCenter:
                        x += (rect.width() - _cachedWidth) / 2.0f;
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
                state.drawText(_text, x, y, _paint);
            } else {
                switch (_halign) {
                    case AlignHCenter:
                        x += (rect.width() - _layout.width) / 2.0f;
                        break;
                    case AlignRight:
                        x += rect.width() - _layout.width;
                        break;
                }
                switch (_valign) {
                    case AlignVCenter:
                        y += (rect.height() - _layout.height) / 2.0f;
                        break;
                    case AlignBottom:
                        y += rect.height() - _layout.height;
                        break;
                }
                //Log.v(TAG, "paint: " + _layout + ", halign: " + _halign + ", valign: "  + _valign + ", rect: " + rect);
                for (TextLayout.Stripe stripe : _layout.stripes) {
                    int dx = 0;
                    switch(_halign) {
                        case AlignHCenter:
                            dx += (_layout.width - stripe.width) / 2;
                            break;
                        case AlignRight:
                            dx += _layout.width - stripe.width;
                            break;
                    }
                    state.drawText(_layout.text, stripe.start, stripe.end, x + dx, y, _paint);
                    y += lineHeight;
                }
            }
        }
        paintChildren(state);
        endPaint(state);
    }

    public void setText(String text) {
        //Log.i(TAG, "setText " + text);
        if (text.equals(_text))
            return;

        _text = text;
        resetLayout();
        //enableCache(text != null && text.length() != 0);
        update();
    }

    static private final Pattern newLinePattern = Pattern.compile("<br.*?>");

    private String preprocess(String text) {
        return text.replaceAll("\\s+", " ");
    }

    private void layoutLine(int begin, int end, int maxWidth) {
        if (_wrap == Wrap.Wrap) {
            _layout.wrap(_paint, begin, end, maxWidth, _wrapAnywhere);
        } else {
            int width = (int)_paint.measureText(_layout.text);
            _layout.add(begin, end, width);
        }
    }

    private void setWrap(Wrap wrap) {
        _wrap = wrap;
        resetLayout();
    }

    private void layoutText() {
        if (_text == null)
            return;

        float fontHeight = _paint.getTextSize();
        _layout = new TextLayout(preprocess(_text));
        Rect rect = getRect();

        Matcher matcher = newLinePattern.matcher(_layout.text);
        int begin = 0;
        while(matcher.find()) {
            layoutLine(begin, matcher.start(), rect.width());
            begin = matcher.end();
        }
        layoutLine(begin, _layout.text.length(), rect.width());

        _layout.height = (int) (_layout.stripes.size() * fontHeight * _lineHeight + _paint.getFontMetrics().bottom);
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
