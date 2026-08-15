package com.huanghy7588.xiaqiaoqiaogongjvxiang.watermark;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * 水印导出器：在原始分辨率 Bitmap 上绘制水印。
 *
 * 绘制规则：
 * - 支持多行文字（按 \n 拆分），逐行黑白交替：奇数行黑色，偶数行白色。
 * - 开启描边时：黑字白边、白字黑边。
 * - 开启序号时：最后一行文字末尾追加数字。
 * - 保留原图透明通道（透明底导出 PNG 时不丢失）。
 *
 * 位置和大小使用百分比，保证与预览一致。
 */
public class WatermarkExporter {

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
     * @param centerXFrac   水印中心 X（占图片宽度比例）
     * @param centerYFrac   水印中心 Y（占图片高度比例）
     * @param textSizeFactor 文字大小（占图片宽度比例）
     * @return 带水印的 Bitmap
     */
    public Bitmap export(Bitmap bitmap, String text, boolean stroke, boolean numbering,
                         int number, float centerXFrac, float centerYFrac,
                         float textSizeFactor) {
        if (bitmap == null) return null;

        Bitmap result = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(bitmap, 0, 0, null);

        if (text == null || text.isEmpty()) return result;

        // 按换行符拆分为多行，序号追加到最后一行
        String[] lines = text.split("\n");
        if (numbering && lines.length > 0) {
            lines[lines.length - 1] = lines[lines.length - 1] + number;
        }

        int bmpW = bitmap.getWidth();
        int bmpH = bitmap.getHeight();

        float textSize = bmpW * textSizeFactor;
        blackFill.setTextSize(textSize);
        whiteFill.setTextSize(textSize);
        blackStroke.setTextSize(textSize);
        whiteStroke.setTextSize(textSize);

        float strokeWidth = textSize * 0.12f;
        blackStroke.setStrokeWidth(strokeWidth);
        whiteStroke.setStrokeWidth(strokeWidth);

        float lineSpacing = textSize * 0.2f;
        int lineCount = lines.length;
        float totalHeight = textSize * lineCount + lineSpacing * (lineCount - 1);

        float cx = bmpW * centerXFrac;
        float cy = bmpH * centerYFrac;
        float top = cy - totalHeight / 2f;

        // 逐行绘制：奇数行黑色、偶数行白色，与预览保持一致
        for (int i = 0; i < lineCount; i++) {
            float baseline = top + textSize + i * (textSize + lineSpacing);
            boolean isBlackLine = (i % 2 == 0);
            Paint fillPaint = isBlackLine ? blackFill : whiteFill;
            Paint strokePaint = isBlackLine ? blackStroke : whiteStroke;

            if (stroke) {
                canvas.drawText(lines[i], cx, baseline, strokePaint);
            }
            canvas.drawText(lines[i], cx, baseline, fillPaint);
        }

        return result;
    }
}
