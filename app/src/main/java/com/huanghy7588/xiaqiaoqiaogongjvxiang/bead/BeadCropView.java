package com.huanghy7588.xiaqiaoqiaogongjvxiang.bead;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * 矩形裁剪视图：在居中矩形裁剪框内显示可缩放/平移的图片，用户选择要保留的区域。
 * 可选裁剪比例：自由 / 1:1 / 4:3。
 */
public class BeadCropView extends View {

    private Bitmap src;
    private float scale = 1f;
    private float tx = 0f;
    private float ty = 0f;
    private float minScale = 1f;

    private int viewW, viewH;
    private float cx, cy;
    private float frameW, frameH;
    private float frameAspect = 1f; // 宽/高

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    public BeadCropView(Context context) {
        super(context);
        init(context);
    }

    public BeadCropView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scale *= detector.getScaleFactor();
                scale = Math.max(minScale, Math.min(scale, minScale * 5f));
                clamp();
                invalidate();
                return true;
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                tx += dx;
                ty += dy;
                clamp();
                invalidate();
                return true;
            }
        });
    }

    public void setSourceBitmap(Bitmap b) {
        this.src = b;
        if (viewW > 0 && b != null) recompute();
        invalidate();
    }

    /** 设置裁剪框宽高比（w/h）。1=1:1，1.333=4:3，0=自由（用图片自身比例）。 */
    public void setFrameAspect(float aspect) {
        if (aspect <= 0 && src != null) {
            frameAspect = (float) src.getWidth() / src.getHeight();
        } else {
            frameAspect = aspect;
        }
        if (viewW > 0) recompute();
        invalidate();
    }

    private void recompute() {
        if (src == null) return;
        float maxFrame = Math.min(viewW, viewH) * 0.82f;
        if (frameAspect >= 1f) {
            frameW = maxFrame;
            frameH = maxFrame / frameAspect;
        } else {
            frameH = maxFrame;
            frameW = maxFrame * frameAspect;
        }
        // 再约束到视图内
        if (frameW > viewW * 0.92f) {
            frameW = viewW * 0.92f;
            frameH = frameW / frameAspect;
        }
        if (frameH > viewH * 0.92f) {
            frameH = viewH * 0.92f;
            frameW = frameH * frameAspect;
        }
        minScale = Math.max(frameW / src.getWidth(), frameH / src.getHeight());
        scale = minScale;
        tx = 0;
        ty = 0;
        clamp();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewW = w;
        viewH = h;
        cx = w / 2f;
        cy = h / 2f;
        if (src != null) recompute();
    }

    private void clamp() {
        if (src == null) return;
        float halfW = src.getWidth() * scale / 2f;
        float halfH = src.getHeight() * scale / 2f;
        float maxX = Math.max(0f, halfW - frameW / 2f);
        float maxY = Math.max(0f, halfH - frameH / 2f);
        tx = Math.max(-maxX, Math.min(tx, maxX));
        ty = Math.max(-maxY, Math.min(ty, maxY));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        if (!scaleDetector.isInProgress()) {
            gestureDetector.onTouchEvent(event);
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.argb(150, 0, 0, 0));
        if (src == null) return;

        canvas.save();
        RectF frame = new RectF(cx - frameW / 2f, cy - frameH / 2f, cx + frameW / 2f, cy + frameH / 2f);
        canvas.clipRect(frame);
        canvas.translate(cx + tx, cy + ty);
        canvas.scale(scale, scale);
        canvas.translate(-src.getWidth() / 2f, -src.getHeight() / 2f);
        canvas.drawBitmap(src, 0, 0, null);
        canvas.restore();

        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setColor(Color.WHITE);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(3);
        canvas.drawRect(frame, border);
    }

    /** 当前裁剪框宽高比（宽/高）。 */
    public float getCurrentAspect() {
        return frameH > 0 ? frameW / frameH : 1f;
    }

    /** 返回裁剪出的位图（透明背景，尺寸 outW × outH）。 */
    public Bitmap getCroppedBitmap(int outW, int outH) {
        if (src == null) return null;
        Bitmap out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        // 视图坐标 → 输出坐标
        c.translate(-(cx - frameW / 2f), -(cy - frameH / 2f));
        c.scale((float) outW / frameW, (float) outH / frameH);
        // 与 onDraw 完全相同的图片变换
        c.translate(cx + tx, cy + ty);
        c.scale(scale, scale);
        c.translate(-src.getWidth() / 2f, -src.getHeight() / 2f);
        c.drawBitmap(src, 0, 0, null);
        return out;
    }
}
