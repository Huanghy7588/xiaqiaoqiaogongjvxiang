package com.huanghy7588.xiaqiaoqiaogongjvxiang.bead;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * 图纸导出：PNG（整图 + 标题区署名 + 图例）与 PDF（A4 按 29×29 拼豆板分页）。
 * 两种格式都会带固定署名「图片来源：夏乔乔」（不可关闭），并保存到系统 Downloads。
 */
public class BeadExporter {

    private static final String DL = Environment.DIRECTORY_DOWNLOADS;

    /** 生成 PNG Bitmap（不含保存）。 */
    public static Bitmap buildPng(BeadColor[][] grid, BeadConfig cfg, String brandTitle) {
        if (grid == null || grid.length == 0) return null;
        int gh = grid.length;
        int gw = grid[0].length;
        int cell = 24;
        int margin = 40;
        int titleH = 60;
        float gridOx = margin;
        float gridOy = titleH + 40;
        int gridW = gw * cell;
        int gridH = gh * cell;

        List<BeadDraw.Usage> usage = BeadDraw.buildUsage(grid);

        int colW = 300;
        int rowH = 26;
        int swatch = 20;
        int textSize = 16;
        int w = Math.max(gridW + margin * 2, 600);
        float legendY = gridOy + gridH + 20;
        int legendCols = Math.max(1, w / colW);
        int legendRows = (usage.size() + legendCols - 1) / legendCols;
        float legendH = legendRows * rowH + 30;
        int h = (int) (legendY + legendH + 20);

        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.WHITE);

        BeadDraw.DrawOptions opt = new BeadDraw.DrawOptions();
        opt.showCodes = cfg.showCodes;
        opt.showGrid = cfg.showGrid;
        opt.boldEvery5 = cfg.boldEvery5;

        // 标题（左）：品牌 + 尺寸
        Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
        title.setColor(Color.DKGRAY);
        title.setTextSize(24);
        title.setFakeBoldText(true);
        title.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(brandTitle + "   " + gw + "×" + gh + " 颗", margin, 40, title);

        // 署名（右上）
        Paint credit = new Paint(Paint.ANTI_ALIAS_FLAG);
        credit.setColor(Color.parseColor(BeadDraw.CREDIT_COLOR));
        credit.setTextSize(16);
        credit.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(BeadDraw.CREDIT_TEXT, w - margin, 40, credit);

        // 网格
        BeadDraw.drawChart(canvas, grid, opt, gridOx, gridOy, cell, 0, 0, gh, gw, true);

        // 图例
        Paint lgTitle = new Paint(Paint.ANTI_ALIAS_FLAG);
        lgTitle.setColor(Color.DKGRAY);
        lgTitle.setTextSize(18);
        lgTitle.setFakeBoldText(true);
        canvas.drawText("图例：", margin, legendY + 16, lgTitle);
        BeadDraw.drawLegend(canvas, usage, margin, legendY + 30,
                w - margin * 2, colW, rowH, swatch, textSize);

        // 署名（右下）
        canvas.drawText(BeadDraw.CREDIT_TEXT, w - margin, h - 20, credit);

        return bmp;
    }

    /** 导出 PNG 到 Downloads，返回 Uri（失败返回 null）。 */
    public static Uri savePngToDownloads(Context ctx, Bitmap bmp, String name) {
        if (bmp == null) return null;
        ContentValues v = new ContentValues();
        v.put(MediaStore.Downloads.DISPLAY_NAME, name);
        v.put(MediaStore.Downloads.MIME_TYPE, "image/png");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.put(MediaStore.Downloads.RELATIVE_PATH, DL);
        }
        Uri uri = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (uri == null) return null;
        try (OutputStream os = ctx.getContentResolver().openOutputStream(uri)) {
            if (os == null || !bmp.compress(Bitmap.CompressFormat.PNG, 100, os)) return null;
            return uri;
        } catch (Exception e) {
            return null;
        }
    }

    /** 生成 PDF 字节（A4，按 29×29 分页）。 */
    public static byte[] buildPdfBytes(BeadColor[][] grid, BeadConfig cfg, String brandTitle) {
        if (grid == null || grid.length == 0) return null;
        int gh = grid.length;
        int gw = grid[0].length;

        if (!cfg.paginate) {
            return buildPdfSingle(grid, cfg, brandTitle);
        }

        PdfDocument pdf = new PdfDocument();
        int pageW = 595;
        int pageH = 842;
        final float perPage = 29;
        final float cell = 16f;
        float gridX = 40f;
        float gridY = 80f;

        int pagesX = (int) Math.ceil(gw / perPage);
        int pagesY = (int) Math.ceil(gh / perPage);
        int totalPages = pagesX * pagesY;
        int pageNum = 1;

        BeadDraw.DrawOptions opt = new BeadDraw.DrawOptions();
        opt.showCodes = cfg.showCodes;
        opt.showGrid = cfg.showGrid;
        opt.boldEvery5 = cfg.boldEvery5;

        for (int by = 0; by < pagesY; by++) {
            for (int bx = 0; bx < pagesX; bx++) {
                PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create();
                PdfDocument.Page page = pdf.startPage(info);
                Canvas canvas = page.getCanvas();

                int r0 = by * 29;
                int c0 = bx * 29;
                int rows = Math.min(29, gh - r0);
                int cols = Math.min(29, gw - c0);

                // 页眉
                Paint header = new Paint(Paint.ANTI_ALIAS_FLAG);
                header.setColor(Color.DKGRAY);
                header.setTextSize(14);
                header.setTextAlign(Paint.Align.LEFT);
                canvas.drawText(brandTitle, gridX, 40, header);
                Paint pageNo = new Paint(Paint.ANTI_ALIAS_FLAG);
                pageNo.setColor(Color.DKGRAY);
                pageNo.setTextSize(14);
                pageNo.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText("第 " + pageNum + "/" + totalPages + " 页", pageW - gridX, 40, pageNo);

                Paint region = new Paint(Paint.ANTI_ALIAS_FLAG);
                region.setColor(Color.GRAY);
                region.setTextSize(10);
                region.setTextAlign(Paint.Align.LEFT);
                canvas.drawText("区域：行 " + (r0 + 1) + "-" + (r0 + rows)
                        + "，列 " + (c0 + 1) + "-" + (c0 + cols), gridX, 60, region);

                // 网格
                BeadDraw.drawChart(canvas, grid, opt, gridX, gridY, cell, r0, c0, rows, cols, true);

                // 本页用色图例
                List<BeadDraw.Usage> usage = subUsage(grid, r0, c0, rows, cols);
                Paint lgTitle = new Paint(Paint.ANTI_ALIAS_FLAG);
                lgTitle.setColor(Color.DKGRAY);
                lgTitle.setTextSize(9);
                lgTitle.setTextAlign(Paint.Align.LEFT);
                canvas.drawText("本页用色：", gridX, 564, lgTitle);
                BeadDraw.drawLegend(canvas, usage, gridX, 572,
                        pageW - gridX * 2, 270, 14, 12, 8);

                // 页脚署名
                Paint credit = new Paint(Paint.ANTI_ALIAS_FLAG);
                credit.setColor(Color.parseColor(BeadDraw.CREDIT_COLOR));
                credit.setTextSize(8);
                credit.setTextAlign(Paint.Align.LEFT);
                canvas.drawText(BeadDraw.CREDIT_TEXT, gridX, pageH - 20, credit);

                pdf.finishPage(page);
                pageNum++;
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            pdf.writeTo(baos);
        } catch (Exception e) {
            pdf.close();
            return null;
        }
        pdf.close();
        return baos.toByteArray();
    }

    /** 导出 PDF 到 Downloads，返回 Uri（失败返回 null）。 */
    public static Uri savePdfToDownloads(Context ctx, byte[] data, String name) {
        if (data == null) return null;
        ContentValues v = new ContentValues();
        v.put(MediaStore.Downloads.DISPLAY_NAME, name);
        v.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.put(MediaStore.Downloads.RELATIVE_PATH, DL);
        }
        Uri uri = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (uri == null) return null;
        try (OutputStream os = ctx.getContentResolver().openOutputStream(uri)) {
            if (os == null) return null;
            os.write(data);
            return uri;
        } catch (Exception e) {
            return null;
        }
    }

    /** 单页 PDF：整张图纸缩放到一页 A4（关闭「按拼豆板分页」时使用）。 */
    private static byte[] buildPdfSingle(BeadColor[][] grid, BeadConfig cfg, String brandTitle) {
        int gh = grid.length;
        int gw = grid[0].length;
        PdfDocument pdf = new PdfDocument();
        int pageW = 595;
        int pageH = 842;
        float margin = 40f;
        float cell = Math.min((pageW - margin * 2) / gw, (pageH - margin * 2 - 140) / gh);
        float gridX = (pageW - gw * cell) / 2f;
        float gridY = 80f;

        BeadDraw.DrawOptions opt = new BeadDraw.DrawOptions();
        opt.showCodes = cfg.showCodes;
        opt.showGrid = cfg.showGrid;
        opt.boldEvery5 = cfg.boldEvery5;

        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(pageW, pageH, 1).create();
        PdfDocument.Page page = pdf.startPage(info);
        Canvas canvas = page.getCanvas();

        Paint header = new Paint(Paint.ANTI_ALIAS_FLAG);
        header.setColor(Color.DKGRAY);
        header.setTextSize(14);
        canvas.drawText(brandTitle, gridX, 40, header);
        Paint pageNo = new Paint(Paint.ANTI_ALIAS_FLAG);
        pageNo.setColor(Color.DKGRAY);
        pageNo.setTextSize(14);
        pageNo.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("第 1/1 页", pageW - margin, 40, pageNo);

        Paint region = new Paint(Paint.ANTI_ALIAS_FLAG);
        region.setColor(Color.GRAY);
        region.setTextSize(10);
        canvas.drawText("区域：整图 " + gw + "×" + gh, gridX, 60, region);

        BeadDraw.drawChart(canvas, grid, opt, gridX, gridY, cell, 0, 0, gh, gw, true);

        List<BeadDraw.Usage> usage = BeadDraw.buildUsage(grid);
        Paint lgTitle = new Paint(Paint.ANTI_ALIAS_FLAG);
        lgTitle.setColor(Color.DKGRAY);
        lgTitle.setTextSize(9);
        float legendY = gridY + gh * cell + 20;
        canvas.drawText("全部用色：", gridX, legendY, lgTitle);
        BeadDraw.drawLegend(canvas, usage, gridX, legendY + 10, pageW - margin * 2, 270, 14, 12, 8);

        Paint credit = new Paint(Paint.ANTI_ALIAS_FLAG);
        credit.setColor(Color.parseColor(BeadDraw.CREDIT_COLOR));
        credit.setTextSize(8);
        canvas.drawText(BeadDraw.CREDIT_TEXT, margin, pageH - 20, credit);

        pdf.finishPage(page);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            pdf.writeTo(baos);
        } catch (Exception e) {
            pdf.close();
            return null;
        }
        pdf.close();
        return baos.toByteArray();
    }

    private static List<BeadDraw.Usage> subUsage(BeadColor[][] grid, int r0, int c0, int rows, int cols) {
        return BeadDraw.buildUsage(subGrid(grid, r0, c0, rows, cols));
    }

    private static BeadColor[][] subGrid(BeadColor[][] grid, int r0, int c0, int rows, int cols) {
        BeadColor[][] sub = new BeadColor[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                sub[r][c] = grid[r0 + r][c0 + c];
            }
        }
        return sub;
    }
}
