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
import android.view.ViewConfiguration;
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
 * 6. 水印位置模式：居中 / 左下角 / 自定义。
 * 7. 自动缩放字号：文字过长时自动缩小，保证完整显示在图片内（防吞字）。
 * 8. 点击空白处（非水印区域）回调，用于进入大图预览。
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

    /** 水印位置模式（居中 / 左下角 / 自定义） */
    private int positionMode = ImageData.POSITION_CENTER;

    /**
     * 水印中心位置（以图片显示区域宽高的百分比表示，0~1，仅 CUSTOM 模式使用）。
     */
    private float centerXFrac = 0.5f;
    private float centerYFrac = 0.5f;

    /** 文字大小占图片显示区域宽度的比例 */
    private float textSizeFactor = 0.06f;

    /** 内容边距占图片宽度的比例（左右下留白，保证文字不被裁切） */
    private static final float MARGIN_FRAC = 0.04f;

    /** 文字最大可占用宽度/高度的比例（自动缩放保证完整显示） */
    private static final float MAX_FILL = 0.92f;

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
    private float downX, downY;
    private long downTime;
    private int touchSlop;

    /** 是否允许拖拽移动水印（主界面小图禁用，大图预览启用） */
    private boolean dragEnabled = true;

    /** 点击监听（点击空白处触发，用于打开大图预览） */
    private Runnable onTapListener;

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
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
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

    /** 设置水印位置模式（居中 / 左下角 / 自定义） */
    public void setPositionMode(int mode) {
        this.positionMode = mode;
        invalidate();
    }

    public int getPositionMode() {
        return positionMode;
    }

    /** 设置水印位置（百分比 0~1，自动切换为自定义模式） */
    public void setPositionFraction(float xFrac, float yFrac) {
        this.centerXFrac = clamp(xFrac, 0.05f, 0.95f);
        this.centerYFrac = clamp(yFrac, 0.05f, 0.95f);
        invalidate();
    }

    /** 设置水印位置（百分比 0~1），可指定是否切换为自定义模式 */
    public void setPositionFraction(float xFrac, float yFrac, boolean asCustom) {
        this.centerXFrac = clamp(xFrac, 0.05f, 0.95f);
        this.centerYFrac = clamp(yFrac, 0.05f, 0.95f);
        if (asCustom) this.positionMode = ImageData.POSITION_CUSTOM;
        invalidate();
    }

    public float getCenterXFrac() {
        return centerXFrac;
    }

    public float getCenterYFrac() {
        return centerYFrac;
    }

    /** 获取最近一次实际绘制的水印中心（百分比坐标），用于模式切换时保持位置不跳变 */
    public float getActualCenterXFrac() {
        if (imageRect.width() <= 0) return centerXFrac;
        return clamp((watermarkRect.centerX() - imageRect.left) / imageRect.width(), 0.05f, 0.95f);
    }

    public float getActualCenterYFrac() {
        if (imageRect.height() <= 0) return centerYFrac;
        return clamp((watermarkRect.centerY() - imageRect.top) / imageRect.height(), 0.05f, 0.95f);
    }

    public float getTextSizeFactor() {
        return textSizeFactor;
    }

    /** 微调文字大小（0.03~0.15） */
    public void setTextSizeFactor(float factor) {
        this.textSizeFactor = clamp(factor, 0.03f, 0.15f);
        invalidate();
    }

    /** 设置点击空白处的监听（用于进入大图预览） */
    public void setOnTapListener(Runnable listener) {
        this.onTapListener = listener;
    }

    /** 设置是否允许拖拽水印（false 时触摸不移动水印，仅保留点击回调） */
    public void setDragEnabled(boolean enabled) {
        this.dragEnabled = enabled;
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

    /**
     * 计算自适应后的文字大小。
     * 文字过长时按比例缩小，保证最宽行和总高度都落在图片区域内（防吞字）。
     */
    private float fitTextSize(String[] lines, float baseSize, float areaW, float areaH,
                              int lineCount) {
        blackFillPaint.setTextSize(baseSize);
        float maxWidth = 0f;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, blackFillPaint.measureText(line));
        }
        float lineSpacing = baseSize * 0.2f;
        float totalHeight = baseSize * lineCount + lineSpacing * (lineCount - 1);

        float scale = 1f;
        if (maxWidth > areaW * MAX_FILL && maxWidth > 0) {
            scale = Math.min(scale, (areaW * MAX_FILL) / maxWidth);
        }
        if (totalHeight > areaH * MAX_FILL && totalHeight > 0) {
            scale = Math.min(scale, (areaH * MAX_FILL) / totalHeight);
        }
        return baseSize * scale;
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

        // 文字大小 = 图片显示宽度 × 比例，超长自动缩小保证完整显示
        float textSize = fitTextSize(srcLines, imageRect.width() * textSizeFactor,
                imageRect.width(), imageRect.height(), lineCount);
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

        // 最宽行宽度（用于命中矩形和左下角定位）
        float maxLineWidth = 0f;
        for (String line : srcLines) {
            maxLineWidth = Math.max(maxLineWidth, blackFillPaint.measureText(line));
        }

        // 按位置模式计算水印中心点（像素坐标）
        float cx, cy;
        float margin = imageRect.width() * MARGIN_FRAC;
        switch (positionMode) {
            case ImageData.POSITION_BOTTOM_LEFT:
                // 左下角：文字块左边贴左边距、底边贴下边距，永远完整显示
                cx = imageRect.left + margin + maxLineWidth / 2f;
                cy = imageRect.bottom - margin - totalHeight / 2f;
                break;
            case ImageData.POSITION_BOTTOM_RIGHT:
                // 右下角：文字块右边贴右边距、底边贴下边距，永远完整显示
                cx = imageRect.right - margin - maxLineWidth / 2f;
                cy = imageRect.bottom - margin - totalHeight / 2f;
                break;
            case ImageData.POSITION_CUSTOM:
                cx = imageRect.left + imageRect.width() * centerXFrac;
                cy = imageRect.top + imageRect.height() * centerYFrac;
                break;
            case ImageData.POSITION_CENTER:
            default:
                cx = imageRect.centerX();
                cy = imageRect.centerY();
                break;
        }
        float top = cy - totalHeight / 2f;

        // 逐行绘制：i < srcCount 为黑色块，之后为白色块
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
                downX = x;
                downY = y;
                downTime = System.currentTimeMillis();
                // 判断是否按在水印区域（稍微放大命中范围）；拖拽被禁用时永不进入拖拽
                RectF hitRect = new RectF(watermarkRect);
                hitRect.inset(-40, -40);
                isDragging = dragEnabled && hitRect.contains(x, y);
                lastTouchX = x;
                lastTouchY = y;
                if (isDragging) {
                    // 禁止父 View 拦截触摸事件
                    ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    // 首次拖动时从当前位置模式切换为自定义（以当前实际中心为基准，避免跳变）
                    if (positionMode != ImageData.POSITION_CUSTOM) {
                        centerXFrac = clamp(
                                (watermarkRect.centerX() - imageRect.left) / imageRect.width(),
                                0.05f, 0.95f);
                        centerYFrac = clamp(
                                (watermarkRect.centerY() - imageRect.top) / imageRect.height(),
                                0.05f, 0.95f);
                        positionMode = ImageData.POSITION_CUSTOM;
                    }
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
                }
                return true;

            case MotionEvent.ACTION_UP:
                // 未拖动且未点在水印上 → 视为点击空白，触发大图预览
                if (!isDragging
                        && Math.abs(x - downX) < touchSlop
                        && Math.abs(y - downY) < touchSlop
                        && System.currentTimeMillis() - downTime < 400) {
                    if (onTapListener != null) {
                        onTapListener.run();
                    }
                }
                isDragging = false;
                // 恢复父 View 拦截
                ViewParent p = getParent();
                if (p != null) {
                    p.requestDisallowInterceptTouchEvent(false);
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                ViewParent pc = getParent();
                if (pc != null) {
                    pc.requestDisallowInterceptTouchEvent(false);
                }
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    // ==================== 工具方法 ====================

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
