package com.huanghy7588.xiaqiaoqiaogongjvxiang.cardgen;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * 头像裁剪视图：在圆形裁剪框内显示被缩放/平移的图片，用户可自由拖动、捏合缩放，
 * 选择要作为头像的部分。调用 {@link #getCroppedBitmap(int)} 得到裁剪出的圆形头像。
 */
public class CropView extends View {

    private Bitmap src;
    private float scale = 1f;
    private float tx = 0f;
    private float ty = 0f;
    private float minScale = 1f;

    private int viewW, viewH;
    private float cx, cy, radius;

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    public CropView(Context context) {
        super(context);
        init(context);
    }

    public CropView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scale *= detector.getScaleFactor();
                scale = Math.max(minScale, Math.min(scale, minScale * 5f));
                clampTranslation();
                invalidate();
                return true;
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                tx += dx;
                ty += dy;
                clampTranslation();
                invalidate();
                return true;
            }
        });
    }

    /** 设置待裁剪的源图片（已采样到合适尺寸）。 */
    public void setSourceBitmap(Bitmap b) {
        this.src = b;
        if (viewW > 0 && b != null) {
            recomputeBase();
            invalidate();
        }
    }

    private void recomputeBase() {
        if (src == null) return;
        minScale = Math.max((radius * 2) / src.getWidth(), (radius * 2) / src.getHeight());
        scale = minScale;
        tx = 0;
        ty = 0;
        clampTranslation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewW = w;
        viewH = h;
        cx = w / 2f;
        cy = h / 2f;
        radius = Math.min(w, h) * 0.38f;
        if (src != null) recomputeBase();
    }

    private void clampTranslation() {
        if (src == null) return;
        float halfW = src.getWidth() * scale / 2f;
        float halfH = src.getHeight() * scale / 2f;
        float maxX = Math.max(0f, halfW - radius);
        float maxY = Math.max(0f, halfH - radius);
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

        // 暗化背景
        Paint dim = new Paint();
        dim.setColor(Color.argb(150, 0, 0, 0));
        canvas.drawRect(0, 0, viewW, viewH, dim);

        if (src == null) return;

        // 圆形裁剪框内绘制图片
        canvas.save();
        Path circle = new Path();
        circle.addCircle(cx, cy, radius, Path.Direction.CW);
        canvas.clipPath(circle);
        canvas.translate(cx + tx, cy + ty);
        canvas.scale(scale, scale);
        canvas.translate(-src.getWidth() / 2f, -src.getHeight() / 2f);
        canvas.drawBitmap(src, 0, 0, null);
        canvas.restore();

        // 圆形边框
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setColor(Color.WHITE);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(3);
        canvas.drawCircle(cx, cy, radius, border);
    }

    /** 返回裁剪出的圆形头像 Bitmap（透明背景，边长 = outSize 像素）。 */
    public Bitmap getCroppedBitmap(int outSize) {
        if (src == null) return null;
        float outRadius = outSize / 2f;
        float s = outSize / (2 * radius);

        Bitmap out = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);

        // 裁剪到输出圆形
        Path circle = new Path();
        circle.addCircle(outRadius, outRadius, outRadius, Path.Direction.CW);
        c.clipPath(circle);

        // 先把画布从「视图坐标」映射到「输出坐标」：out = s * (view - (cx-radius))
        c.scale(s, s);
        c.translate(-(cx - radius), -(cy - radius));
        // 与 onDraw 完全相同的图片变换
        c.translate(cx + tx, cy + ty);
        c.scale(scale, scale);
        c.translate(-src.getWidth() / 2f, -src.getHeight() / 2f);
        c.drawBitmap(src, 0, 0, null);

        return out;
    }
}
