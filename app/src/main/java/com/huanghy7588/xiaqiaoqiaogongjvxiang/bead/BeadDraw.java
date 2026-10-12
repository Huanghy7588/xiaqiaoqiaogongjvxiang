package com.huanghy7588.xiaqiaoqiaogongjvxiang.bead;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 拼豆图纸绘制：预览、PNG、PDF 共用同一套网格/图例绘制逻辑（只是目标 Canvas 不同）。
 */
public class BeadDraw {

    /** 导出署名（不可关闭）。 */
    public static final String CREDIT_TEXT = "图片来源：夏乔乔";
    public static final String CREDIT_COLOR = "#79747E";

    public static class DrawOptions {
        public boolean showCodes = true;
        public boolean showGrid = true;
        public boolean boldEvery5 = true;
    }

    /** 深色格（亮度低）自动用白色文字，浅色格用黑色文字。 */
    public static int textColorFor(int color) {
        double lum = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        return lum > 0.6 ? Color.BLACK : Color.WHITE;
    }

    /** 统计每种颜色的使用次数（按频率降序）。 */
    public static List<Usage> buildUsage(BeadColor[][] grid) {
        Map<String, Usage> map = new HashMap<>();
        if (grid != null) {
            for (BeadColor[] row : grid) {
                for (BeadColor c : row) {
                    if (c == null) continue;
                    Usage u = map.get(c.code);
                    if (u == null) {
                        u = new Usage(c);
                        map.put(c.code, u);
                    }
                    u.count++;
                }
            }
        }
        List<Usage> list = new ArrayList<>(map.values());
        list.sort((a, b) -> b.count - a.count);
        return list;
    }

    public static class Usage {
        public final BeadColor color;
        public int count;

        public Usage(BeadColor color) {
            this.color = color;
            this.count = 0;
        }
    }

    /**
     * 绘制网格（可只绘制其中的一个子块，用于 PDF 分页）。
     *
     * @param r0    起始行（含）
     * @param c0    起始列（含）
     * @param rows  行数（子块高度）
     * @param cols  列数（子块宽度）
     * @param numbers 是否绘制行列号（每 5 格一个）
     */
    public static void drawChart(Canvas canvas, BeadColor[][] grid, DrawOptions opt,
                                 float ox, float oy, float cell,
                                 int r0, int c0, int rows, int cols, boolean numbers) {
        if (grid == null || grid.length == 0) return;
        int gh = grid.length;
        int gw = grid[0].length;
        int rEnd = Math.min(gh, r0 + rows);
        int cEnd = Math.min(gw, c0 + cols);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint codePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        codePaint.setTextAlign(Paint.Align.CENTER);
        codePaint.setTextSize(cell * 0.30f);

        // 格子
        for (int r = r0; r < rEnd; r++) {
            for (int c = c0; c < cEnd; c++) {
                BeadColor col = grid[r][c];
                float left = ox + (c - c0) * cell;
                float top = oy + (r - r0) * cell;
                fill.setColor(col == null ? Color.WHITE : Color.parseColor(col.hex));
                canvas.drawRect(left, top, left + cell, top + cell, fill);
                if (opt.showCodes && col != null) {
                    codePaint.setColor(textColorFor(fill.getColor()));
                    canvas.drawText(col.code, left + cell / 2f, top + cell * 0.62f, codePaint);
                }
            }
        }

        // 网格线（每 5 格加粗）
        if (opt.showGrid) {
            line.setStyle(Paint.Style.STROKE);
            for (int r = r0; r <= rEnd; r++) {
                float y = oy + (r - r0) * cell;
                boolean bold = opt.boldEvery5 && (r % 5 == 0);
                line.setStrokeWidth(bold ? cell * 0.05f : cell * 0.015f);
                line.setColor(bold ? Color.DKGRAY : Color.LTGRAY);
                canvas.drawLine(ox, y, ox + (cEnd - c0) * cell, y, line);
            }
            for (int c = c0; c <= cEnd; c++) {
                float x = ox + (c - c0) * cell;
                boolean bold = opt.boldEvery5 && (c % 5 == 0);
                line.setStrokeWidth(bold ? cell * 0.05f : cell * 0.015f);
                line.setColor(bold ? Color.DKGRAY : Color.LTGRAY);
                canvas.drawLine(x, oy, x, oy + (rEnd - r0) * cell, line);
            }
        }

        // 行列号：每 5 格标一个分组序号
        if (numbers) {
            Paint numPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            numPaint.setColor(Color.GRAY);
            numPaint.setTextSize(cell * 0.30f);
            numPaint.setTextAlign(Paint.Align.CENTER);
            float pad = cell * 0.7f;
            for (int r = r0; r < rEnd; r++) {
                if (r % 5 == 0) {
                    int gc = r / 5 + 1;
                    canvas.drawText(String.valueOf(gc), ox - pad / 2f, oy + (r - r0) * cell + cell * 0.6f, numPaint);
                }
            }
            for (int c = c0; c < cEnd; c++) {
                if (c % 5 == 0) {
                    int gc = c / 5 + 1;
                    canvas.drawText(String.valueOf(gc), ox + (c - c0) * cell + cell / 2f, oy - pad / 3f, numPaint);
                }
            }
        }
    }

    /** 在 (x,y) 起绘制图例（色块 + 色号 + 数量），多列排布，返回绘制高度。 */
    public static float drawLegend(Canvas canvas, List<Usage> usage, float x, float y,
                                   float maxWidth, float colW, float rowH,
                                   float swatch, float textSize) {
        if (usage == null || usage.isEmpty()) return 0;
        Paint sw = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint tx = new Paint(Paint.ANTI_ALIAS_FLAG);
        tx.setColor(Color.DKGRAY);
        tx.setTextSize(textSize);
        tx.setTextAlign(Paint.Align.LEFT);

        int cols = Math.max(1, (int) (maxWidth / colW));
        int per = (usage.size() + cols - 1) / cols;
        float cx0 = x;
        float cy = y;
        for (int i = 0; i < usage.size(); i++) {
            int col = i / per;
            int row = i % per;
            float cx = cx0 + col * colW;
            float ry = cy + row * rowH;
            Usage u = usage.get(i);
            sw.setColor(Color.parseColor(u.color.hex));
            canvas.drawRect(cx, ry, cx + swatch, ry + swatch, sw);
            canvas.drawText(u.color.code + " ×" + u.count, cx + swatch + 6, ry + swatch * 0.8f, tx);
        }
        int rowsUsed = (usage.size() + cols - 1) / cols;
        return rowsUsed * rowH;
    }
}
