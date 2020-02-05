package com.pureqml.android.runtime;

import java.util.LinkedList;
import java.util.List;

public final class TextLayout {
    public String   text;

    public int      width;
    public int      height;

    public final class Stripe {
        public int start;
        public int end;
        public int width;

        public Stripe(int start, int end, int width) {
            this.start = start;
            this.end = end;
            this.width = width;
        }

        public String toString() {
            return "" + start + "-" + end + " (" + width + "px)";
        }
    }

    public List<Stripe> stripes;

    public TextLayout(String text) {
        this.text = text;
        this.stripes = new LinkedList<Stripe>();
    }

    public void add(int start, int end, int width) {
        stripes.add(new Stripe(start, end, width));
        if (width > this.width)
            this.width = width;
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
