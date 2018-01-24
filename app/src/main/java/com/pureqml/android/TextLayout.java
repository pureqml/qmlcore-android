package com.pureqml.android;

public class TextLayout {
    public CharSequence    text;

    public int width;
    public int height;

    public class Stripe {
        public int start;
        public int end;
        public int width;
    }
    public Stripe[] stripes;
}
