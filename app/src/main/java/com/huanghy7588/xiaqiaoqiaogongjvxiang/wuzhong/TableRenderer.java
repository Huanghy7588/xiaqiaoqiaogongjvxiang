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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 表格渲染器：把多个人物渲染成固定宽 2500 的 PNG（行=字段，列=人物）。
 * 单元格支持文字与图片（图片选项直接画进对应单元格）。
 */
public class TableRenderer {

    private static final float BASE_W = 1800f;      // 基准宽（缩窄，避免表格过宽）
    private static final float BASE_LABEL_W = 380f; // 标签列缩窄
    private static final float BASE_HEADER_H = 130f;
    private static final float BASE_TEXT_ROW_H = 150f; // 行高略增，配合更大字体
    private static final float BASE_MAX_IMG_H = 300f;  // 图片最大高度缩小
    private static final float BASE_PAD = 14f;

    /** 渲染宽度由调用方指定：导出 2500，页面内嵌预览用小图省内存 */
    public Bitmap render(List<PersonData> persons, int mode, int imgW) {
        if (persons == null || persons.isEmpty()) return null;

        final float s = imgW / BASE_W;
        final int labelW = Math.round(BASE_LABEL_W * s);
        final int headerH = Math.round(BASE_HEADER_H * s);
        final int textRowH = Math.round(BASE_TEXT_ROW_H * s);
        final int maxImgH = Math.round(BASE_MAX_IMG_H * s);
        final int pad = Math.max(2, Math.round(BASE_PAD * s));

        int n = persons.size();
        int personW = (imgW - labelW) / n;

        // W7 修复：computeRows 只调用 N 次（此前 2N 次），缓存行列表供后续复用
        List<List<PersonData.Row>> allRows = new ArrayList<>();
        // 每人的行转成 label -> cell 映射（选填项每人独立，需按行标签对齐）
        List<Map<String, PersonData.Cell>> maps = new ArrayList<>();
        for (PersonData p : persons) {
            List<PersonData.Row> rows = p.computeRows(mode);
            allRows.add(rows);
            Map<String, PersonData.Cell> m = new LinkedHashMap<>();
            for (PersonData.Row r : rows) m.put(r.label, r.cell);
            maps.add(m);
        }
        boolean anyIdBar = false;
        for (PersonData p : persons) {
            if (p.idBar == 1) { anyIdBar = true; break; }
        }

        // 统一行序列：主行（所有人一致，取第一人）+ ID条（任一人有则插在 ID名 后）+ 选填并集
        List<String> labels = new ArrayList<>();
        boolean firstHasIdBar = false;
        for (PersonData.Row r : allRows.get(0)) {
            if (isOptionalLabel(r.label)) continue;
            labels.add(r.label);
            if (r.label.equals("ID名")) {
                firstHasIdBar = persons.get(0).idBar == 1;
                if (anyIdBar && !firstHasIdBar) labels.add("ID条");
            }
        }
        for (String ol : PersonData.OPTIONAL_LABELS) {
            for (Map<String, PersonData.Cell> m : maps) {
                if (m.containsKey(ol)) { labels.add(ol); break; }
            }
        }
        int rowCount = labels.size();

        // 计算每行高度（图片行需更大）
        int[] rowH = new int[rowCount];
        for (int r = 0; r < rowCount; r++) {
            int h = textRowH;
            String label = labels.get(r);
            for (int i = 0; i < n; i++) {
                PersonData.Cell c = maps.get(i).get(label);
                if (c != null && c.imageAsset != null) {
                    Size sz = imgSize(c.imageAsset);
                    if (sz != null) {
                        float tw = personW - pad * 2;
                        float scale = tw / sz.w;
                        float ih = sz.h * scale;
                        if (ih > maxImgH) {
                            ih = maxImgH;
                            scale = maxImgH / sz.h;
                            tw = sz.w * scale;
                        }
                        h = Math.max(h, (int) ih + pad * 2
                                + (c.text != null && !c.text.isEmpty() ? Math.round(50 * s) : 0));
                    }
                }
            }
            rowH[r] = h;
        }

        int totalH = headerH;
        for (int h : rowH) totalH += h;

        Bitmap bmp = Bitmap.createBitmap(imgW, totalH, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        cv.drawColor(Color.WHITE);

        // 表头
        cv.drawRect(0, 0, imgW, headerH, pHeaderBg);
        drawCellText(cv, "项目", 0, 0, labelW, headerH, 48 * s, true, pad, null);
        for (int i = 0; i < n; i++) {
            drawCellText(cv, "左" + (i + 1), labelW + i * personW, 0, personW, headerH, 48 * s, true, pad, null);
        }
        cv.drawLine(0, headerH, imgW, headerH, pLine);

        // W8 修复：渲染期间缓存已解码的 asset Bitmap，同一图片不重复解码
        Map<String, Bitmap> assetCache = new HashMap<>();

        // 数据行
        int y = headerH;
        for (int r = 0; r < rowCount; r++) {
            int h = rowH[r];
            String label = labels.get(r);
            // 标签列
            cv.drawRect(0, y, labelW, y + h, pLabelBg);
            drawCellText(cv, label, 0, y, labelW, h, 42 * s, false, pad, null);
            // 竖向分隔线
            cv.drawLine(labelW, y, labelW, y + h, pLine);
            // 人物列
            for (int i = 0; i < n; i++) {
                PersonData.Cell c = maps.get(i).get(label);
                int cx = labelW + i * personW;
                drawCell(cv, c, cx, y, personW, h, s, pad, maxImgH, assetCache);
                cv.drawLine(cx + personW, y, cx + personW, y + h, pLine);
            }
            cv.drawLine(0, y + h, imgW, y + h, pLine);
            y += h;
        }

        // W10 修复：外边框用 stroke 模式，线宽 3px，大图上清晰可见
        pLine.setStyle(android.graphics.Paint.Style.STROKE);
        cv.drawRect(1.5f, 1.5f, imgW - 1.5f, totalH - 1.5f, pLine);
        pLine.setStyle(android.graphics.Paint.Style.FILL);

        // W8 修复：渲染结束统一回收缓存中的 Bitmap
        for (Bitmap b : assetCache.values()) {
            if (b != null && !b.isRecycled()) b.recycle();
        }
        assetCache.clear();

        return bmp;
    }

    private final Context ctx;
    private final Paint pLine = new Paint();
    private final Paint pLabelBg = new Paint();
    private final Paint pHeaderBg = new Paint();
    private final Paint pBlack = new Paint();

    public TableRenderer(Context context) {
        this.ctx = context;
        pLine.setColor(Color.parseColor("#BDBDBD"));
        pLine.setStrokeWidth(3);
        pLabelBg.setColor(Color.parseColor("#F0F0F0"));
        pHeaderBg.setColor(Color.parseColor("#4CAF50"));
        pBlack.setColor(Color.BLACK);
    }

    private boolean isOptionalLabel(String label) {
        for (String ol : PersonData.OPTIONAL_LABELS) {
            if (ol.equals(label)) return true;
        }
        return false;
    }

    private void drawCell(Canvas cv, PersonData.Cell c, int x, int y, int w, int h, float s, int pad, int maxImgH,
                          Map<String, Bitmap> assetCache) {
        if (c == null) return;
        boolean hasImg = c.imageAsset != null;
        boolean hasText = c.text != null && !c.text.isEmpty();
        int textZone = Math.min(Math.round(65 * s), h / 3);
        if (hasImg && hasText) {
            // 文字在上，图片在下
            drawCellText(cv, c.text, x, y, w, textZone, 38 * s, false, pad, c.textColor);
            drawCellImage(cv, c.imageAsset, x, y + textZone, w, h - textZone, pad, maxImgH, assetCache);
        } else if (hasImg) {
            drawCellImage(cv, c.imageAsset, x, y, w, h, pad, maxImgH, assetCache);
        } else if (hasText) {
            drawCellText(cv, c.text, x, y, w, h, 42 * s, false, pad, c.textColor);
        }
    }

    private void drawCellImage(Canvas cv, String asset, int x, int y, int w, int h, int pad, int maxImgH,
                               Map<String, Bitmap> assetCache) {
        // W8 修复：先查缓存，命中则直接用，不再重复解码
        Bitmap b = assetCache.get(asset);
        if (b == null) {
            b = loadScaled(asset, w - pad * 2, maxImgH);
            if (b == null) return;
            assetCache.put(asset, b);
        }
        int dw = b.getWidth();
        int dh = b.getHeight();
        int dx = x + (w - dw) / 2;
        int dy = y + (h - dh) / 2;
        cv.drawBitmap(b, dx, dy, null);
    }

    private void drawCellText(Canvas cv, String text, int x, int y, int w, int h, float size, boolean white,
                              int pad, Integer textColor) {
        if (text == null || text.isEmpty()) return;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextAlign(Paint.Align.CENTER);
        if (white) {
            paint.setColor(Color.WHITE);
        } else if (textColor != null) {
            paint.setColor(textColor);
        } else {
            paint.setColor(Color.parseColor("#212121"));
        }
        paint.setTextSize(size);
        // 自适应缩放，防止文字超出列宽
        float maxW = w - pad * 2;
        while (paint.measureText(text) > maxW && paint.getTextSize() > 14 * Math.max(0.4f, size / 42f)) {
            paint.setTextSize(paint.getTextSize() - 1);
        }
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = y + (h - (fm.ascent + fm.descent)) / 2f - fm.ascent;
        cv.drawText(text, x + w / 2f, baseline, paint);
    }

    private Size imgSize(String asset) {
        // W3 修复：用 try-with-resources 确保流关闭
        try (InputStream is = ctx.getAssets().open(asset)) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, o);
            if (o.outWidth > 0 && o.outHeight > 0) return new Size(o.outWidth, o.outHeight);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Bitmap loadScaled(String asset, int maxW, int maxH) {
        // W3 修复：用 try-with-resources 确保两个流都关闭
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream is0 = ctx.getAssets().open(asset)) {
                BitmapFactory.decodeStream(is0, null, o);
            }
            int sample = 1;
            while (o.outWidth / sample > maxW * 2 || o.outHeight / sample > maxH * 2) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            Bitmap b;
            try (InputStream is1 = ctx.getAssets().open(asset)) {
                b = BitmapFactory.decodeStream(is1, null, o);
            }
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
