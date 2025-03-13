package com.pureqml.android.runtime;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Picture;
import android.graphics.Rect;
import android.graphics.RectF;

public final class PaintState {
    public static final float opacityThreshold = 1.0f / 255;

    public final Picture picture;
    private final Canvas canvas;
    private final Rect dirtyRect;
    public final int baseX;
    public final int baseY;
    public final float opacity;
    public final Matrix transform;

    public PaintState(Canvas canvas) {
        this.picture = null;
        this.canvas = canvas;
        this.baseX = this.baseY = 0;
        this.opacity = 1.0f;
        this.dirtyRect = new Rect();
        this.transform = new Matrix();
    }

    public PaintState(PaintState parent, int x, int y, float opacity) {
        this.picture = null;
        this.canvas = parent.canvas;
        this.baseX = parent.baseX + x;
        this.baseY = parent.baseY + y;
        this.opacity = opacity;
        this.dirtyRect = new Rect();
        this.transform = new Matrix();
    }

    public PaintState(Picture picture, int w, int h, float opacity) {
        this.picture = picture;
        this.canvas = picture.beginRecording(w, h);
        this.baseX = 0;
        this.baseY = 0;
        this.opacity = opacity;
        this.dirtyRect = new Rect();
        this.transform = new Matrix();
    }

    public void end() {
        if (picture != null) {
            picture.endRecording();
        }
    }

    public int save() {
        return canvas.save();
    }

    public void restore() {
        canvas.restore();
    }

    public void restoreToCount(int checkpoint) {
        canvas.restoreToCount(checkpoint);
    }

    public void translate(float dx, float dy) {
        canvas.translate(dx, dy);
        transform.postTranslate(dx, dy);
    }

    public void scale(float sx, float sy, float px, float py) {
        translate(px, py);
        canvas.scale(sx, sy);
        transform.postScale(sx, sy);
        translate(-px, -py);
    }

    public void rotate(float degrees, float px, float py) {
        canvas.rotate(degrees, px, py);
        transform.postRotate(degrees, px, py);
    }

    public boolean clipPath(Path path) {
        return canvas.clipPath(path);
    }

    public boolean clipRect(Rect rect) {
        return canvas.clipRect(rect);
    }

    public static boolean visible(float opacity) {
        return opacity >= opacityThreshold;
    }

    public void drawPicture(Picture picture) {
        canvas.drawPicture(picture);
        int w = picture.getWidth();
        int h = picture.getHeight();
        addDirtyRect(0, 0, w, h);
    }

    public void drawBitmap(Bitmap bitmap, Rect src, Rect dst,
                           Paint paint) {
        canvas.drawBitmap(bitmap, src, dst, paint);
        addDirtyRect(dst);
    }

    private void updateTextBounds(float dstX, float dstY, int w, Paint paint) {
        int x = (int)Math.floor(dstX);
        int y = (int)Math.floor(dstY);
        int a = (int)Math.ceil(paint.getFontMetrics().top);
        int d = (int)Math.ceil(paint.getFontMetrics().bottom);
        // top/ascent normally is negative, adding it to y.
        addDirtyRect(x, y + a, x + w, y + d);
    }

    public void drawText(String text, int start, int end, float x, float y,
                         Paint paint) {
        canvas.drawText(text, start, end, x, y, paint);
        int w = (int)Math.ceil(paint.measureText(text, start, end));
        updateTextBounds(x, y, w, paint);
    }

    public void drawText(String text, float x, float y,
                         Paint paint) {
        canvas.drawText(text, x, y, paint);
        int w = (int)Math.ceil(paint.measureText(text));
        updateTextBounds(x, y, w, paint);
    }

    public void drawRoundRect(final Rect rect, float rx, float ry, Paint paint) {
        float sw = paint.getStrokeWidth();
        RectF dst = new RectF(rect);
        canvas.drawRoundRect(dst, rx, ry, paint);
        addDirtyRect(dst.left - sw, dst.top - sw, dst.right + sw, dst.bottom + sw);
    }

    public void drawRoundRect(RectF rect, float rx, float ry, Paint paint) {
        float sw = paint.getStrokeWidth();
        canvas.drawRoundRect(rect, rx, ry, paint);
        addDirtyRect(rect.left - sw, rect.top - sw, rect.right + sw, rect.bottom + sw);
    }

    public void drawRect(Rect rect, Paint paint) {
        canvas.drawRect(new RectF(rect), paint);
        int sw = (int)paint.getStrokeWidth();
        addDirtyRect(rect.left - sw, rect.top - sw, rect.right + sw, rect.bottom + sw);
    }

    public void drawRect(RectF rect, Paint paint) {
        float sw = paint.getStrokeWidth();
        canvas.drawRect(rect, paint);
        addDirtyRect(rect.left - sw, rect.top - sw, rect.right + sw, rect.bottom + sw);
    }

    private void addTransformedDirtyRect(RectF rect) {
        transform.mapRect(rect);
        Rect dst = new Rect();
        rect.round(dst);
        dirtyRect.union(dst);
    }

    public void addDirtyRect(int x, int y, int r, int b) {
        if (transform.isIdentity()) {
            dirtyRect.union(x, y, r, b);
        } else {
            addTransformedDirtyRect(new RectF(x, y, r, b));
        }
    }

    public void addDirtyRect(float x, float y, float r, float b) {
        if (transform.isIdentity()) {
            dirtyRect.union((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(r), (int) Math.ceil(b));
        } else {
            addTransformedDirtyRect(new RectF(x, y, r, b));
        }
    }

    public void addDirtyRect(Rect rect) {
        if (transform.isIdentity()) {
            dirtyRect.union(rect);
        } else {
            addTransformedDirtyRect(new RectF(rect));
        }
    }

    public void addDirtyRect(RectF rect) {
        int x = (int)Math.floor(rect.left);
        int y = (int)Math.floor(rect.top);
        int r = (int)Math.ceil(rect.right);
        int b = (int)Math.ceil(rect.bottom);
        addDirtyRect(x, y, r, b);
    }

    public Rect getDirtyRect() { return dirtyRect; }
}
