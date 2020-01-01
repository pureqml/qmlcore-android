package com.pureqml.android.runtime;

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
