package com.huanghy7588.xiaqiaoqiaogongjvxiang.watermark;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.Nullable;

/**
 * 水印预览自定义 View。
 *
 * 功能：
 * 1. 以 fitCenter 方式绘制原图，透明底图片用棋盘格背景显示。
 * 2. 支持多行文字水印（回车换行）：整块文字先画一遍全黑，再复制一份画全白。
 * 3. 支持描边：黑字白边、白字黑边。
 * 4. 支持序号：在最后一行文字后追加数字（1、2、3…）。
 * 5. 支持拖拽移动水印位置，支持微调。
 * 6. 水印位置以"图片显示区域的百分比"存储，便于跨图批量应用。
 */
public class WatermarkView extends View {

    /** 原图 Bitmap */
    private Bitmap imageBitmap;

    /** 水印文字 */
    private String watermarkText = "";

    /** 是否描边 */
    private boolean strokeEnabled = false;

    /** 是否序号 */
    private boolean numberingEnabled = false;

    /** 当前序号 */
    private int currentNumber = 1;

    /**
     * 水印中心位置（以图片显示区域宽高的百分比表示，0~1）。
     * 默认右下角偏内：(0.82, 0.88)
     */
    private float centerXFrac = 0.82f;
    private float centerYFrac = 0.88f;

    /** 文字大小占图片显示区域宽度的比例 */
    private float textSizeFactor = 0.06f;

    /** 图片在 View 中的实际显示矩形 */
    private final RectF imageRect = new RectF();

    // 画笔
    private final Paint blackFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whiteFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blackStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whiteStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** 透明底棋盘格画笔（懒加载，需要 density） */
    private Paint checkerPaint;

    // 拖拽状态
    private boolean isDragging = false;
    private float lastTouchX, lastTouchY;

    // 水印测量矩形（用于命中检测）
    private final RectF watermarkRect = new RectF();

    // 可复用的 Rect（避免 onDraw 每帧创建）
    private final Rect srcRect = new Rect();

    public WatermarkView(Context context) {
        this(context, null);
    }

    public WatermarkView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WatermarkView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPaints();
    }

    private void initPaints() {
        blackFillPaint.setColor(Color.BLACK);
        blackFillPaint.setStyle(Paint.Style.FILL);
        blackFillPaint.setTextAlign(Paint.Align.CENTER);

        whiteFillPaint.setColor(Color.WHITE);
        whiteFillPaint.setStyle(Paint.Style.FILL);
        whiteFillPaint.setTextAlign(Paint.Align.CENTER);

        // 黑色文字的描边 = 白色
        blackStrokePaint.setColor(Color.WHITE);
        blackStrokePaint.setStyle(Paint.Style.STROKE);
        blackStrokePaint.setTextAlign(Paint.Align.CENTER);

        // 白色文字的描边 = 黑色
        whiteStrokePaint.setColor(Color.BLACK);
        whiteStrokePaint.setStyle(Paint.Style.STROKE);
        whiteStrokePaint.setTextAlign(Paint.Align.CENTER);

        hintPaint.setColor(Color.argb(80, 0, 0, 0));
        hintPaint.setStyle(Paint.Style.STROKE);
        hintPaint.setStrokeWidth(2f);
    }

    // ==================== 属性设置 ====================

    public void setImageBitmap(Bitmap bitmap) {
        this.imageBitmap = bitmap;
        invalidate();
    }

    public void setWatermarkText(String text) {
        this.watermarkText = text != null ? text : "";
        invalidate();
    }

    public void setStrokeEnabled(boolean enabled) {
        this.strokeEnabled = enabled;
        invalidate();
    }

    public void setNumberingEnabled(boolean enabled) {
        this.numberingEnabled = enabled;
        invalidate();
    }

    public void setCurrentNumber(int number) {
        this.currentNumber = number;
        invalidate();
    }

    /** 设置水印位置（百分比 0~1） */
    public void setPositionFraction(float xFrac, float yFrac) {
        this.centerXFrac = clamp(xFrac, 0.05f, 0.95f);
        this.centerYFrac = clamp(yFrac, 0.05f, 0.95f);
        invalidate();
    }

    public float getCenterXFrac() {
        return centerXFrac;
    }

    public float getCenterYFrac() {
        return centerYFrac;
    }

    public float getTextSizeFactor() {
        return textSizeFactor;
    }

    /** 微调文字大小（0.03~0.15） */
    public void setTextSizeFactor(float factor) {
        this.textSizeFactor = clamp(factor, 0.03f, 0.15f);
        invalidate();
    }

    // ==================== 绘制 ====================

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        computeImageRect();
    }

    /** 计算图片 fitCenter 显示矩形 */
    private void computeImageRect() {
        if (imageBitmap == null || getWidth() == 0 || getHeight() == 0) {
            imageRect.set(0, 0, getWidth(), getHeight());
            return;
        }
        float viewW = getWidth();
        float viewH = getHeight();
        float bmpW = imageBitmap.getWidth();
        float bmpH = imageBitmap.getHeight();

        float scale = Math.min(viewW / bmpW, viewH / bmpH);
        float drawW = bmpW * scale;
        float drawH = bmpH * scale;
        float left = (viewW - drawW) / 2f;
        float top = (viewH - drawH) / 2f;
        imageRect.set(left, top, left + drawW, top + drawH);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (imageBitmap == null) return;

        // 1. 先铺棋盘格底，透明底图片不会被误认为黑底
        if (checkerPaint == null) initCheckerPaint();
        computeImageRect();
        canvas.drawRect(imageRect, checkerPaint);

        // 2. 绘制原图
        srcRect.set(0, 0, imageBitmap.getWidth(), imageBitmap.getHeight());
        canvas.drawBitmap(imageBitmap, srcRect, imageRect, null);

        // 3. 绘制水印
        drawWatermark(canvas);
    }

    /** 初始化透明底棋盘格画笔 */
    private void initCheckerPaint() {
        // 每格 10dp
        int tile = Math.max(8, Math.round(getResources().getDisplayMetrics().density * 10));
        Bitmap tileBmp = Bitmap.createBitmap(tile * 2, tile * 2, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(tileBmp);
        Paint light = new Paint();
        light.setColor(Color.rgb(245, 245, 245));
        Paint dark = new Paint();
        dark.setColor(Color.rgb(205, 205, 205));
        c.drawRect(0, 0, tile * 2, tile * 2, light);
        c.drawRect(0, 0, tile, tile, dark);
        c.drawRect(tile, tile, tile * 2, tile * 2, dark);

        checkerPaint = new Paint();
        checkerPaint.setShader(new BitmapShader(tileBmp,
                Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
    }

    /** 绘制多行水印文字：整块先画一遍全黑，再复制一份画全白 */
    private void drawWatermark(Canvas canvas) {
        if (watermarkText.isEmpty()) return;

        // 按换行符拆分为多行，序号追加到最后一行
        String[] srcLines = watermarkText.split("\n");
        if (numberingEnabled && srcLines.length > 0) {
            srcLines[srcLines.length - 1] = srcLines[srcLines.length - 1] + currentNumber;
        }

        // 渲染总行数 = 源行数 × 2（前一半黑色，后一半白色）
        int srcCount = srcLines.length;
        int lineCount = srcCount * 2;

        // 文字大小 = 图片显示宽度 × 比例
        float textSize = imageRect.width() * textSizeFactor;
        blackFillPaint.setTextSize(textSize);
        whiteFillPaint.setTextSize(textSize);
        blackStrokePaint.setTextSize(textSize);
        whiteStrokePaint.setTextSize(textSize);

        // 描边宽度
        float strokeWidth = textSize * 0.12f;
        blackStrokePaint.setStrokeWidth(strokeWidth);
        whiteStrokePaint.setStrokeWidth(strokeWidth);

        // 行间距与总高度
        float lineSpacing = textSize * 0.2f;
        float totalHeight = textSize * lineCount + lineSpacing * (lineCount - 1);

        // 水印中心点（像素坐标）
        float cx = imageRect.left + imageRect.width() * centerXFrac;
        float cy = imageRect.top + imageRect.height() * centerYFrac;
        float top = cy - totalHeight / 2f;

        // 逐行绘制：i < srcCount 为黑色块，之后为白色块
        float maxLineWidth = 0f;
        for (int i = 0; i < lineCount; i++) {
            float baseline = top + textSize + i * (textSize + lineSpacing);
            boolean isBlackLine = (i < srcCount);
            String line = srcLines[i % srcCount];
            Paint fillPaint = isBlackLine ? blackFillPaint : whiteFillPaint;
            Paint strokePaint = isBlackLine ? blackStrokePaint : whiteStrokePaint;

            if (strokeEnabled) {
                canvas.drawText(line, cx, baseline, strokePaint);
            }
            canvas.drawText(line, cx, baseline, fillPaint);
            maxLineWidth = Math.max(maxLineWidth, fillPaint.measureText(line));
        }

        // 记录水印矩形用于命中检测
        watermarkRect.set(cx - maxLineWidth / 2f, top,
                cx + maxLineWidth / 2f, top + totalHeight);

        // 拖拽时显示半透明边框
        if (isDragging) {
            canvas.drawRect(watermarkRect, hintPaint);
        }
    }

    // ==================== 触摸拖拽 ====================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (imageBitmap == null) return false;

        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 判断是否按在水印区域（稍微放大命中范围）
                RectF hitRect = new RectF(watermarkRect);
                hitRect.inset(-40, -40);
                if (hitRect.contains(x, y)) {
                    isDragging = true;
                    lastTouchX = x;
                    lastTouchY = y;
                    // 禁止父 View 拦截触摸事件
                    ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                    return true;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    float dx = x - lastTouchX;
                    float dy = y - lastTouchY;
                    // 转换为百分比位移
                    float dxFrac = dx / imageRect.width();
                    float dyFrac = dy / imageRect.height();
                    centerXFrac = clamp(centerXFrac + dxFrac, 0.05f, 0.95f);
                    centerYFrac = clamp(centerYFrac + dyFrac, 0.05f, 0.95f);
                    lastTouchX = x;
                    lastTouchY = y;
                    invalidate();
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                // 恢复父 View 拦截
                ViewParent p = getParent();
                if (p != null) {
                    p.requestDisallowInterceptTouchEvent(false);
                }
                invalidate();
                break;
        }
        return super.onTouchEvent(event);
    }

    // ==================== 工具方法 ====================

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
