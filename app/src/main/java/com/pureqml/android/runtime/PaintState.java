package com.pureqml.android.runtime;

import android.graphics.Canvas;
import android.graphics.Rect;

public final class PaintState {
    public static final float opacityThreshold = 1.0f / 255;

    public final Canvas canvas;
    public int baseX = 0;
    public int baseY = 0;
    public float opacity = 1;
    public Rect clipRect;

    public PaintState(Canvas canvas) {
        this.canvas = canvas;
        this.clipRect = canvas.getClipBounds();
    }

    public PaintState(PaintState parent, int x, int y, float opacity) {
        this.canvas = parent.canvas;
        this.baseX = parent.baseX + x;
        this.baseY = parent.baseY + y;
        this.opacity = parent.opacity * opacity;
        this.clipRect = parent.clipRect;
    }

    public void setClipRect(Rect clipRect) {
        Rect rect = new Rect(this.clipRect);
        if (rect.intersect(rect))
            this.clipRect = rect;
        else
            this.clipRect = null;
    }

    public final boolean visible() {
        return opacity >= opacityThreshold;
    }
}
