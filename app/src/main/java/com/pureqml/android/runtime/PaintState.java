package com.pureqml.android.runtime;

import android.graphics.Canvas;
import android.graphics.Picture;

public final class PaintState {
    public static final float opacityThreshold = 1.0f / 255;

    public final Picture picture;
    public final Canvas canvas;
    public final int baseX;
    public final int baseY;
    public final float opacity;

    public PaintState(Canvas canvas) {
        this.picture = null;
        this.canvas = canvas;
        this.baseX = this.baseY = 0;
        this.opacity = 1.0f;
    }

    public PaintState(PaintState parent, int x, int y, float opacity) {
        this.picture = null;
        this.canvas = parent.canvas;
        this.baseX = parent.baseX + x;
        this.baseY = parent.baseY + y;
        this.opacity = opacity;
    }

    public PaintState(Picture picture, int w, int h, float opacity) {
        this.picture = picture;
        this.canvas = picture.beginRecording(w, h);
        this.baseX = 0;
        this.baseY = 0;
        this.opacity = opacity;
    }

    public void end() {
        if (picture != null) {
            picture.endRecording();
        }
    }

    public final static boolean visible(float opacity) {
        return opacity >= opacityThreshold;
    }
}
