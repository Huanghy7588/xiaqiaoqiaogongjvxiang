package com.huanghy7588.xiaqiaoqiaogongjvxiang.tiemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * 贴膜水印机预览视图：按原图比例自适应显示在预览框内（contain 模式），
 * 竖图按高、横图按宽、1:1 原样放置，绝不改变原图比例。
 */
public class TiemoPreviewView extends View {

    private Bitmap bitmap;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint bgPaint = new Paint();
    private final RectF dst = new RectF();
    private final Rect src = new Rect();

    public TiemoPreviewView(Context context) {
        super(context);
        init();
    }

    public TiemoPreviewView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TiemoPreviewView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        bgPaint.setColor(Color.WHITE);
    }

    public void setBitmap(@Nullable Bitmap bmp) {
        this.bitmap = bmp;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 背景（浅灰网格感，用纯白即可）
        canvas.drawRect(0, 0, w, h, bgPaint);

        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }

        int bw = bitmap.getWidth();
        int bh = bitmap.getHeight();
        // contain 适配：按原图比例缩放后居中
        float scale = Math.min((float) w / bw, (float) h / bh);
        float dw = bw * scale;
        float dh = bh * scale;
        float left = (w - dw) / 2f;
        float top = (h - dh) / 2f;
        dst.set(left, top, left + dw, top + dh);
        src.set(0, 0, bw, bh);
        canvas.drawBitmap(bitmap, src, dst, paint);
    }
}
