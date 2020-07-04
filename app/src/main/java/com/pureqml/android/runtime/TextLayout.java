package com.pureqml.android.runtime;

import android.graphics.Paint;

import java.util.LinkedList;
import java.util.List;

public final class TextLayout {
    public final String   text;

    public int      width;
    public int      height;

    public static final class Stripe {
        public final int start;
        public final int end;
        public final int width;

        public Stripe(int start, int end, int width) {
            this.start = start;
            this.end = end;
            this.width = width;
        }

        public String toString() {
            return "" + start + "-" + end + " (" + width + "px)";
        }
    }

    public final List<Stripe> stripes;

    public TextLayout(String text) {
        this.text = text;
        this.stripes = new LinkedList<Stripe>();
    }

    public void add(int start, int end, int width) {
        stripes.add(new Stripe(start, end, width));
        if (width > this.width)
            this.width = width;
    }

    public void wrap(Paint paint, int begin, int end, int maxWidth) {
        while(begin < end) {
            float measuredWidth[] = new float[1];
            int n = paint.breakText(text, begin, end, true, maxWidth, measuredWidth);
            add(begin, begin + n, (int)measuredWidth[0]);
            begin += n;
        }
    }

    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("{ text: ");
        b.append(text);
        b.append(", ");
        b.append(width);
        b.append("x");
        b.append(height);
        b.append(" [");
        for(Stripe s: stripes) {
            b.append(s.toString());
            b.append(" ");
        }
        b.append("]");

        return b.toString();
    }
}
