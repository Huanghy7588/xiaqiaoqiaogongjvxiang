package com.huanghy7588.xiaqiaoqiaogongjvxiang.watermark;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * 水印导出器：在原始分辨率 Bitmap 上绘制水印。
 *
 * 绘制规则：
 * - 支持多行文字（按 \n 拆分）：整块先画一遍全黑，再复制一份画全白。
 * - 开启描边时：黑字白边、白字黑边。
 * - 开启序号时：最后一行文字末尾追加数字。
 * - 保留原图透明通道（透明底导出 PNG 时不丢失）。
 * - 支持位置模式（居中 / 左下角 / 自定义）。
 * - 自动缩放字号：文字过长时缩小，保证完整显示（防吞字），与预览逻辑一致。
 *
 * 位置和大小使用百分比，保证与预览一致。
 */
public class WatermarkExporter {

    /** 内容边距占图片宽度的比例（与预览 WatermarkView 保持一致） */
    private static final float MARGIN_FRAC = 0.04f;

    /** 文字最大可占用宽度/高度的比例 */
    private static final float MAX_FILL = 0.92f;

    private final Paint blackFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whiteFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blackStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whiteStroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    public WatermarkExporter() {
        blackFill.setColor(Color.BLACK);
        blackFill.setStyle(Paint.Style.FILL);
        blackFill.setTextAlign(Paint.Align.CENTER);

        whiteFill.setColor(Color.WHITE);
        whiteFill.setStyle(Paint.Style.FILL);
        whiteFill.setTextAlign(Paint.Align.CENTER);

        blackStroke.setColor(Color.WHITE);
        blackStroke.setStyle(Paint.Style.STROKE);
        blackStroke.setTextAlign(Paint.Align.CENTER);

        whiteStroke.setColor(Color.BLACK);
        whiteStroke.setStyle(Paint.Style.STROKE);
        whiteStroke.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * 在 bitmap 上绘制水印并返回新 Bitmap。
     *
     * @param bitmap        原图
     * @param text          水印文字
     * @param stroke        是否描边
     * @param numbering     是否加序号
     * @param number        序号数值
     * @param positionMode  位置模式（居中 / 左下角 / 自定义）
     * @param centerXFrac   水印中心 X（占图片宽度比例，仅自定义模式使用）
     * @param centerYFrac   水印中心 Y（占图片高度比例，仅自定义模式使用）
     * @param textSizeFactor 文字大小（占图片宽度比例）
     * @return 带水印的 Bitmap
     */
    public Bitmap export(Bitmap bitmap, String text, boolean stroke, boolean numbering,
                         int number, int positionMode, float centerXFrac, float centerYFrac,
                         float textSizeFactor) {
        if (bitmap == null) return null;

        Bitmap result = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(bitmap, 0, 0, null);

        if (text == null || text.isEmpty()) return result;

        // 按换行符拆分为多行，序号追加到最后一行
        String[] srcLines = text.split("\n");
        if (numbering && srcLines.length > 0) {
            srcLines[srcLines.length - 1] = srcLines[srcLines.length - 1] + number;
        }

        // 渲染总行数 = 源行数 × 2（前一半黑色，后一半白色），与预览保持一致
        int srcCount = srcLines.length;
        int lineCount = srcCount * 2;

        int bmpW = bitmap.getWidth();
        int bmpH = bitmap.getHeight();

        // 自动缩放字号：超长文字缩小到完整放下，与预览逻辑一致
        float textSize = fitTextSize(srcLines, bmpW * textSizeFactor, bmpW, bmpH, lineCount);
        blackFill.setTextSize(textSize);
        whiteFill.setTextSize(textSize);
        blackStroke.setTextSize(textSize);
        whiteStroke.setTextSize(textSize);

        float strokeWidth = textSize * 0.12f;
        blackStroke.setStrokeWidth(strokeWidth);
        whiteStroke.setStrokeWidth(strokeWidth);

        float lineSpacing = textSize * 0.2f;
        float totalHeight = textSize * lineCount + lineSpacing * (lineCount - 1);

        // 最宽行宽度（用于左下角定位）
        float maxLineWidth = 0f;
        for (String line : srcLines) {
            maxLineWidth = Math.max(maxLineWidth, blackFill.measureText(line));
        }

        // 按位置模式计算水印中心点
        float cx, cy;
        float margin = bmpW * MARGIN_FRAC;
        switch (positionMode) {
            case ImageData.POSITION_BOTTOM_LEFT:
                cx = margin + maxLineWidth / 2f;
                cy = bmpH - margin - totalHeight / 2f;
                break;
            case ImageData.POSITION_CUSTOM:
                cx = bmpW * centerXFrac;
                cy = bmpH * centerYFrac;
                break;
            case ImageData.POSITION_CENTER:
            default:
                cx = bmpW / 2f;
                cy = bmpH / 2f;
                break;
        }
        float top = cy - totalHeight / 2f;

        // 逐行绘制：i < srcCount 为黑色块，之后为白色块
        for (int i = 0; i < lineCount; i++) {
            float baseline = top + textSize + i * (textSize + lineSpacing);
            boolean isBlackLine = (i < srcCount);
            String line = srcLines[i % srcCount];
            Paint fillPaint = isBlackLine ? blackFill : whiteFill;
            Paint strokePaint = isBlackLine ? blackStroke : whiteStroke;

            if (stroke) {
                canvas.drawText(line, cx, baseline, strokePaint);
            }
            canvas.drawText(line, cx, baseline, fillPaint);
        }

        return result;
    }

    /** 计算自适应后的文字大小（超长缩小保证完整显示，与预览一致） */
    private float fitTextSize(String[] lines, float baseSize, float areaW, float areaH,
                              int lineCount) {
        blackFill.setTextSize(baseSize);
        float maxWidth = 0f;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, blackFill.measureText(line));
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
}
