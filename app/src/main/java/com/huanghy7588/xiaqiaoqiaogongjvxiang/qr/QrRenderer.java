package com.huanghy7588.xiaqiaoqiaogongjvxiang.qr;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.google.zxing.common.BitMatrix;

/**
 * 二维码自定义渲染器：把模块矩阵画成 Bitmap，支持
 *  - 前景色 / 背景色（背景可透明）
 *  - 方形 / 圆点 两种码点样式
 *  - 保留三个定位角（finder pattern）始终为方形，保证可扫描
 *  - 四周留静区（quiet zone），提升识别率
 */
public class QrRenderer {

    public static final int DOT_SQUARE = 0;
    public static final int DOT_ROUND = 1;

    /**
     * @param matrix   原始模块矩阵（不含静区）
     * @param sizePx   输出边长（含静区）
     * @param quiet    静区模块数（建议 4）
     * @param fg       前景色 ARGB
     * @param bg       背景色 ARGB（alpha=0 表示透明）
     * @param dotStyle DOT_SQUARE / DOT_ROUND
     */
    public static Bitmap render(BitMatrix matrix, int sizePx, int quiet, int fg, int bg,
                                int dotStyle) {
        int modules = matrix.getWidth();
        if (modules <= 0) return null;
        int total = modules + quiet * 2;
        int moduleSize = Math.max(1, sizePx / total);
        int imgSize = moduleSize * total;

        Bitmap bmp = Bitmap.createBitmap(imgSize, imgSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);

        // 背景（含静区），透明背景则不绘制
        if ((bg & 0xFF000000) != 0) {
            paint.setColor(bg);
            canvas.drawRect(0, 0, imgSize, imgSize, paint);
        }

        float dot = moduleSize;
        // 圆点样式：模块略微缩进，形成点阵间隙，更有“圆点”观感
        float inset = (dotStyle == DOT_ROUND) ? moduleSize * 0.16f : 0f;
        float radius = (dotStyle == DOT_ROUND) ? (dot / 2f) : 0f;

        for (int r = 0; r < modules; r++) {
            for (int c = 0; c < modules; c++) {
                if (!matrix.get(r, c)) continue;
                boolean finder = isFinder(r, c, modules);
                float x = (c + quiet) * moduleSize;
                float y = (r + quiet) * moduleSize;
                paint.setColor(fg);
                if (dotStyle == DOT_ROUND && !finder) {
                    RectF rect = new RectF(x + inset, y + inset, x + dot - inset, y + dot - inset);
                    canvas.drawRoundRect(rect, radius - inset, radius - inset, paint);
                } else {
                    // 定位角与方形样式：实心方块，保证清晰可扫
                    canvas.drawRect(x, y, x + dot, y + dot, paint);
                }
            }
        }
        return bmp;
    }

    /** 是否为三个定位角所在的 8×8 区域（含分隔带）：保持方形不圆化 */
    private static boolean isFinder(int r, int c, int modules) {
        boolean tl = r < 8 && c < 8;
        boolean tr = r < 8 && c >= modules - 8;
        boolean bl = r >= modules - 8 && c < 8;
        return tl || tr || bl;
    }
}
