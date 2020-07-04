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
    public boolean roundClipWorkaround; //image round clipping works only with SRC_IN porter-duff function

    public PaintState(Canvas canvas) {
        this.picture = null;
        this.canvas = canvas;
        this.baseX = this.baseY = 0;
        this.opacity = 1.0f;
        this.roundClipWorkaround = false;
    }

    public PaintState(PaintState parent, int x, int y, float opacity) {
        this.picture = null;
        this.canvas = parent.canvas;
        this.baseX = parent.baseX + x;
        this.baseY = parent.baseY + y;
        this.opacity = opacity;
        this.roundClipWorkaround = parent.roundClipWorkaround;
    }

    public PaintState(Picture picture, int w, int h, float opacity) {
        this.picture = picture;
        this.canvas = picture.beginRecording(w, h);
        this.baseX = 0;
        this.baseY = 0;
        this.opacity = opacity;
        this.roundClipWorkaround = false;
    }

    public void end() {
        if (picture != null) {
            picture.endRecording();
        }
    }

    public static boolean visible(float opacity) {
        return opacity >= opacityThreshold;
    }
}
