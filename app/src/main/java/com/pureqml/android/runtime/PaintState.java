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
    public final int cacheX;
    public final int cacheY;
    public final float opacity;
    public final Matrix transform;

    public PaintState(Canvas canvas) {
        this.picture = null;
        this.canvas = canvas;
        this.baseX = this.baseY = 0;
        this.cacheX = this.cacheY = 0;
        this.opacity = 1.0f;
        this.dirtyRect = new Rect();
        this.transform = new Matrix();
    }

    public PaintState(PaintState parent, int x, int y, float opacity) {
        this.picture = null;
        this.canvas = parent.canvas;
        this.cacheX = parent.cacheX;
        this.cacheY = parent.cacheY;
        this.baseX = parent.baseX + x;
        this.baseY = parent.baseY + y;
        this.opacity = opacity;
        this.dirtyRect = new Rect();
        this.transform = new Matrix();
    }

    public PaintState(Picture picture, PaintState parent, int x, int y, int w, int h, float opacity) {
        this.picture = picture;
        this.canvas = picture.beginRecording(w, h);
        this.cacheX = this.baseX = parent.baseX + x;
        this.cacheY = this.baseY = parent.baseY + y;
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
        if (dx == 0.0f && dy == 0.0f)
            return;
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
        translate(-cacheX, -cacheY);
        boolean clipped = canvas.clipPath(path);
        translate(cacheX, cacheY);
        return clipped;
    }

    public boolean clipRect(Rect rect) {
        translate(-cacheX, -cacheY);
        boolean clipped = canvas.clipRect(rect);
        translate(cacheX, cacheY);
        return clipped;
    }

    public static boolean visible(float opacity) {
        return opacity >= opacityThreshold;
    }

    public void drawPicture(Picture picture, int x, int y) {
        int saveCount = save();
        translate(x - cacheX, y - cacheY);
        canvas.drawPicture(picture);
        addDirtyRect(0, 0, picture.getWidth(), picture.getHeight());
        restoreToCount(saveCount);
    }

    public void drawBitmap(Bitmap bitmap, Rect src, Rect dst,
                           Paint paint) {
        dst.offset(-cacheX, -cacheY);
        canvas.drawBitmap(bitmap, src, dst, paint);
        dst.offset(cacheX, cacheY);
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


        float dstX, dstY;
        dstX = x - cacheX;
        dstY = y - cacheY;
        canvas.drawText(text, start, end, dstX, dstY, paint);
        int w = (int)Math.ceil(paint.measureText(text, start, end));
        updateTextBounds(x, y, w, paint);
    }

    public void drawText(String text, float x, float y,
                         Paint paint) {
        float dstX, dstY;
        dstX = x - cacheX;
        dstY = y - cacheY;
        canvas.drawText(text, dstX, dstY, paint);
        int w = (int)Math.ceil(paint.measureText(text));
        updateTextBounds(x, y, w, paint);
    }

    public void drawRoundRect(final Rect rect, float rx, float ry, Paint paint) {
        float sw = paint.getStrokeWidth();
        RectF dst = new RectF(rect);
        dst.offset(-cacheX, -cacheY);
        canvas.drawRoundRect(dst, rx, ry, paint);
        dst.offset(cacheX, cacheY);
        addDirtyRect(dst.left - sw, dst.top - sw, dst.right + sw, dst.bottom + sw);
    }

    public void drawRoundRect(RectF rect, float rx, float ry, Paint paint) {
        float sw = paint.getStrokeWidth();
        rect.offset(-cacheX, -cacheY);
        canvas.drawRoundRect(rect, rx, ry, paint);
        rect.offset(cacheX, cacheY);
        addDirtyRect(rect.left - sw, rect.top - sw, rect.right + sw, rect.bottom + sw);
    }

    public void drawRect(Rect rect, Paint paint) {
        rect.offset(-cacheX, -cacheY);
        canvas.drawRect(new RectF(rect), paint);
        rect.offset(cacheX, cacheY);
        int sw = (int)paint.getStrokeWidth();
        addDirtyRect(rect.left - sw, rect.top - sw, rect.right + sw, rect.bottom + sw);
    }

    public void drawRect(RectF rect, Paint paint) {
        float sw = paint.getStrokeWidth();
        rect.offset(-cacheX, -cacheY);
        canvas.drawRect(rect, paint);
        rect.offset(cacheX, cacheY);
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
