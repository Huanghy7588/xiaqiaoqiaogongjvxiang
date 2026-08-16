package com.huanghy7588.xiaqiaoqiaogongjvxiang.wuzhong;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 表格渲染器：把多个人物渲染成固定宽 2500 的 PNG（行=字段，列=人物）。
 * 单元格支持文字与图片（图片选项直接画进对应单元格）。
 */
public class TableRenderer {

    private static final int IMG_W = 2500;          // 固定宽
    private static final int LABEL_W = 560;         // 项目列宽
    private static final int HEADER_H = 120;        // 表头高
    private static final int TEXT_ROW_H = 140;      // 纯文字行高
    private static final int MAX_IMG_H = 280;       // 图片最大高度
    private static final int PAD = 16;             // 单元格内边距

    private final Context ctx;
    private final Paint pLine = new Paint();
    private final Paint pLabelBg = new Paint();
    private final Paint pHeaderBg = new Paint();
    private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTextWhite = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBlack = new Paint();

    public TableRenderer(Context context) {
        this.ctx = context;
        pLine.setColor(Color.parseColor("#BDBDBD"));
        pLine.setStrokeWidth(3);
        pLabelBg.setColor(Color.parseColor("#F0F0F0"));
        pHeaderBg.setColor(Color.parseColor("#4CAF50"));
        pText.setColor(Color.parseColor("#212121"));
        pText.setTextAlign(Paint.Align.CENTER);
        pTextWhite.setColor(Color.WHITE);
        pTextWhite.setTextAlign(Paint.Align.CENTER);
        pBlack.setColor(Color.BLACK);
    }

    public Bitmap render(List<PersonData> persons, int mode, PersonData.OptionalState opt) {
        if (persons == null || persons.isEmpty()) return null;

        int n = persons.size();
        int personW = (IMG_W - LABEL_W) / n;

        // 取表头标签（以第一人为准）
        List<PersonData.Row> first = persons.get(0).computeRows(mode, opt);
        int rowCount = first.size();

        // 计算每行高度（图片行需更大）
        int[] rowH = new int[rowCount];
        for (int r = 0; r < rowCount; r++) {
            int h = TEXT_ROW_H;
            for (int i = 0; i < n; i++) {
                PersonData.Cell c = persons.get(i).computeRows(mode, opt).get(r).cell;
                if (c.imageAsset != null) {
                    Size sz = imgSize(c.imageAsset);
                    if (sz != null) {
                        float tw = personW - PAD * 2;
                        float scale = tw / sz.w;
                        float ih = sz.h * scale;
                        if (ih > MAX_IMG_H) {
                            ih = MAX_IMG_H;
                            scale = MAX_IMG_H / sz.h;
                            tw = sz.w * scale;
                        }
                        h = Math.max(h, (int) ih + PAD * 2 + (c.text != null && !c.text.isEmpty() ? 50 : 0));
                    }
                }
            }
            rowH[r] = h;
        }

        int totalH = HEADER_H;
        for (int h : rowH) totalH += h;

        Bitmap bmp = Bitmap.createBitmap(IMG_W, totalH, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        cv.drawColor(Color.WHITE);

        // 表头
        cv.drawRect(0, 0, IMG_W, HEADER_H, pHeaderBg);
        drawCellText(cv, "项目", 0, 0, LABEL_W, HEADER_H, 44, true);
        for (int i = 0; i < n; i++) {
            drawCellText(cv, "左" + (i + 1), LABEL_W + i * personW, 0, personW, HEADER_H, 44, true);
        }
        cv.drawLine(0, HEADER_H, IMG_W, HEADER_H, pLine);

        // 数据行
        int y = HEADER_H;
        for (int r = 0; r < rowCount; r++) {
            int h = rowH[r];
            // 标签列
            cv.drawRect(0, y, LABEL_W, y + h, pLabelBg);
            drawCellText(cv, first.get(r).label, 0, y, LABEL_W, h, 38, false);
            // 竖向分隔线
            cv.drawLine(LABEL_W, y, LABEL_W, y + h, pLine);
            // 人物列
            for (int i = 0; i < n; i++) {
                PersonData.Cell c = persons.get(i).computeRows(mode, opt).get(r).cell;
                int cx = LABEL_W + i * personW;
                drawCell(cv, c, cx, y, personW, h);
                cv.drawLine(cx + personW, y, cx + personW, y + h, pLine);
            }
            cv.drawLine(0, y + h, IMG_W, y + h, pLine);
            y += h;
        }

        // 外边框
        cv.drawRect(1, 1, IMG_W - 1, totalH - 1, pLine);
        return bmp;
    }

    private void drawCell(Canvas cv, PersonData.Cell c, int x, int y, int w, int h) {
        if (c == null) return;
        boolean hasImg = c.imageAsset != null;
        boolean hasText = c.text != null && !c.text.isEmpty();
        if (hasImg && hasText) {
            // 文字在上，图片在下
            drawCellText(cv, c.text, x, y, w, Math.min(60, h / 3), 34, false);
            drawCellImage(cv, c.imageAsset, x, y + Math.min(60, h / 3), w, h - Math.min(60, h / 3));
        } else if (hasImg) {
            drawCellImage(cv, c.imageAsset, x, y, w, h);
        } else if (hasText) {
            drawCellText(cv, c.text, x, y, w, h, 38, false);
        }
    }

    private void drawCellImage(Canvas cv, String asset, int x, int y, int w, int h) {
        Bitmap b = loadScaled(asset, w - PAD * 2, MAX_IMG_H);
        if (b == null) return;
        int dw = b.getWidth();
        int dh = b.getHeight();
        int dx = x + (w - dw) / 2;
        int dy = y + (h - dh) / 2;
        cv.drawBitmap(b, dx, dy, null);
        b.recycle();
    }

    private void drawCellText(Canvas cv, String text, int x, int y, int w, int h, float size, boolean white) {
        if (text == null || text.isEmpty()) return;
        Paint paint = white ? pTextWhite : pText;
        paint.setTextSize(size);
        // 自适应缩放，防止文字超出列宽
        float maxW = w - PAD * 2;
        while (paint.measureText(text) > maxW && paint.getTextSize() > 14) {
            paint.setTextSize(paint.getTextSize() - 1);
        }
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = y + (h - (fm.ascent + fm.descent)) / 2f - fm.ascent;
        cv.drawText(text, x + w / 2f, baseline, paint);
    }

    private Size imgSize(String asset) {
        try {
            InputStream is = ctx.getAssets().open(asset);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, o);
            is.close();
            if (o.outWidth > 0 && o.outHeight > 0) return new Size(o.outWidth, o.outHeight);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Bitmap loadScaled(String asset, int maxW, int maxH) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream is0 = ctx.getAssets().open(asset);
            BitmapFactory.decodeStream(is0, null, o);
            is0.close();
            int sample = 1;
            while (o.outWidth / sample > maxW * 2 || o.outHeight / sample > maxH * 2) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            InputStream is1 = ctx.getAssets().open(asset);
            Bitmap b = BitmapFactory.decodeStream(is1, null, o);
            is1.close();
            if (b == null) return null;
            // 缩放到适配框
            float scale = Math.min((float) maxW / b.getWidth(), (float) maxH / b.getHeight());
            if (scale >= 1) return b;
            int tw = Math.max(1, (int) (b.getWidth() * scale));
            int th = Math.max(1, (int) (b.getHeight() * scale));
            Bitmap scaled = Bitmap.createScaledBitmap(b, tw, th, true);
            if (scaled != b) b.recycle();
            return scaled;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static class Size {
        int w, h;
        Size(int w, int h) { this.w = w; this.h = h; }
    }
}
