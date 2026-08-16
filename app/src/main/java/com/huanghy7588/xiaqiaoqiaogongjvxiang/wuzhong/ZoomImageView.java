package com.huanghy7588.xiaqiaoqiaogongjvxiang.wuzhong;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 支持双指缩放、单指拖动的图片预览视图。
 * 用于无中生有表格预览（表格 2500px 宽，需要缩放查看细节）。
 */
public class ZoomImageView extends View {

    private static final int MODE_NONE = 0, MODE_DRAG = 1, MODE_ZOOM = 2;

    private Bitmap bitmap;
    private final Matrix matrix = new Matrix();
    private final Matrix saved = new Matrix();
    private int mode = MODE_NONE;
    private final PointF start = new PointF();
    private final PointF mid = new PointF();
    private float oldDist = 1f;

    public ZoomImageView(Context c) { super(c); }
    public ZoomImageView(Context c, AttributeSet a) { super(c, a); }

    public void setBitmap(Bitmap b) {
        this.bitmap = b;
        fitToView();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        fitToView();
    }

    /** 初始适配：整图居中显示 */
    private void fitToView() {
        if (bitmap == null || getWidth() <= 0 || getHeight() <= 0) return;
        float scale = Math.min((float) getWidth() / bitmap.getWidth(),
                (float) getHeight() / bitmap.getHeight());
        matrix.reset();
        matrix.postScale(scale, scale);
        matrix.postTranslate((getWidth() - bitmap.getWidth() * scale) / 2f,
                (getHeight() - bitmap.getHeight() * scale) / 2f);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bitmap != null) canvas.drawBitmap(bitmap, matrix, null);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                saved.set(matrix);
                start.set(ev.getX(), ev.getY());
                mode = MODE_DRAG;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                oldDist = spacing(ev);
                if (oldDist > 10f) {
                    midPoint(mid, ev);
                    mode = MODE_ZOOM;
                    saved.set(matrix);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mode == MODE_DRAG) {
                    matrix.set(saved);
                    matrix.postTranslate(ev.getX() - start.x, ev.getY() - start.y);
                } else if (mode == MODE_ZOOM) {
                    float nd = spacing(ev);
                    if (nd > 10f) {
                        matrix.set(saved);
                        matrix.postScale(nd / oldDist, nd / oldDist, mid.x, mid.y);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                mode = MODE_NONE;
                break;
        }
        invalidate();
        return true;
    }

    private float spacing(MotionEvent e) {
        float x = e.getX(0) - e.getX(1);
        float y = e.getY(0) - e.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private void midPoint(PointF p, MotionEvent e) {
        p.set((e.getX(0) + e.getX(1)) / 2f, (e.getY(0) + e.getY(1)) / 2f);
    }
}
