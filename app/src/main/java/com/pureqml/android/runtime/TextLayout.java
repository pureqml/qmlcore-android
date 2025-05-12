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
            return start + "-" + end + " (" + width + "px)";
        }
    }

    public final List<Stripe> stripes;

    public TextLayout(String text) {
        this.text = text;
        this.stripes = new LinkedList<>();
    }

    public void add(int start, int end, int width) {
        stripes.add(new Stripe(start, end, width));
        if (width > this.width)
            this.width = width;
    }

    private static boolean isWhitespace(Character ch) {
        switch (ch) {
            case ' ':
            case '\n':
            case '\r':
            case '\t':
            case '\f':
                return true;
            default:
                return false;
        }
    }

    public void wrap(Paint paint, int begin, int end, int maxWidth, boolean anywhere) {
        float[] measuredWidth = new float[1];
        while(begin < end) {
            int n = paint.breakText(text, begin, end, true, maxWidth, measuredWidth);
            if (!anywhere && n < text.length()) {
                int new_end = begin + n + 1;
                if (new_end >= end) {
                    //end of the string - nothing to wrap
                    new_end = begin;
                }
                else {
                    while (new_end > begin && !isWhitespace(text.charAt(new_end))) {
                        --new_end;
                    }
                    //new_end is pointing to the last whitespace char.
                }
                //fixme: skip whitespaces?
                if (new_end > begin) {
                    n = new_end - begin;
                    measuredWidth[0] = paint.measureText(text, begin, begin + n);
                }
            }
            add(begin, begin + n, (int)measuredWidth[0]);
            begin += n;
            while(begin < end && isWhitespace(text.charAt(begin)))
                ++begin;
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
