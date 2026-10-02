package com.huanghy7588.xiaqiaoqiaogongjvxiang.pintu;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 一键拼图：图片解码与网格合成工具。
 *
 * 规则：
 * 1. 每张图等比居中裁切填满格子（不变形、不留白、缺角）。
 * 2. 画布为透明底，导入图片不足时剩下的格子保持透明，导出 PNG 保留透明。
 * 3. 解码尽量保留原图像素（只在图片远大于格子时才下采样），保证导出画质。
 */
public class PintuUtils {

    private static final String TAG = "PintuUtils";

    /** 导入图片数量上限，防止内存溢出 */
    public static final int MAX_IMAGES = 40;

    /** 拼图张数对应列数 */
    public static int colsFor(int count) {
        switch (count) {
            case 9:
            case 12:
                return 3;
            default:
                return 2;
        }
    }

    /** 拼图张数对应行数 */
    public static int rowsFor(int count) {
        return (count + colsFor(count) - 1) / colsFor(count);
    }

    /** 解码图片：返回不小于目标格子 2 倍的图（尽量保画质），并修正相册照片的旋转角度 */
    public static Bitmap decode(Context ctx, Uri uri, int maxW, int maxH) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        if (readBitmap(ctx, uri, bounds) == null || bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int ow = bounds.outWidth;
        int oh = bounds.outHeight;
        int sample = 1;
        while (ow / sample > maxW * 2 && oh / sample > maxH * 2) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bmp = readBitmap(ctx, uri, opts);
        if (bmp == null) return null;
        int rotation = readRotation(ctx, uri);
        if (rotation != 0) {
            bmp = rotate(bmp, rotation);
        }
        return bmp;
    }

    /** 小尺寸缩略图（仅用于界面预览） */
    public static Bitmap decodeThumb(Context ctx, Uri uri, int size) {
        return decode(ctx, uri, size, size);
    }

    private static Bitmap readBitmap(Context ctx, Uri uri, BitmapFactory.Options opts) {
        try (InputStream is = ctx.getContentResolver().openInputStream(uri)) {
            return is == null ? null : BitmapFactory.decodeStream(is, null, opts);
        } catch (IOException e) {
            Log.w(TAG, "decode fail", e);
            return null;
        }
    }

    /** 读取图片方向（相册导出的照片常见 90/180/270 度旋转） */
    private static int readRotation(Context ctx, Uri uri) {
        try (InputStream is = ctx.getContentResolver().openInputStream(uri)) {
            if (is == null) return 0;
            int orientation = new ExifInterface(is).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90: return 90;
                case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default: return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private static Bitmap rotate(Bitmap src, int degrees) {
        if (degrees == 0 || src.isRecycled()) return src;
        Matrix m = new Matrix();
        m.postRotate(degrees);
        try {
            return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
        } catch (Exception e) {
            return src;
        }
    }

    /**
     * 把一张图等比居中裁切后画进格子（无缝、不变形）。
     */
    public static void drawCell(Canvas canvas, Bitmap src, int left, int top, int cellW, int cellH,
                                Paint paint) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= 0 || h <= 0) return;
        float scale = Math.max(cellW / (float) w, cellH / (float) h);
        int dw = Math.max(1, Math.round(w * scale));
        int dh = Math.max(1, Math.round(h * scale));
        int dx = left + (cellW - dw) / 2;
        int dy = top + (cellH - dh) / 2;
        canvas.drawBitmap(src, new Rect(0, 0, w, h), new Rect(dx, dy, dx + dw, dy + dh), paint);
    }

    /**
     * 合成一张拼图（透明底，图片不够的格子留透明）。
     *
     * @param start 本组第一张图在列表中的位置
     * @param count 本组格子数
     */
    public static Bitmap buildGrid(Context ctx, List<Uri> uris, int start, int count, int cols,
                                   int rows, int outWidth) {
        int cell = outWidth / cols;
        int outH = cell * rows;
        Bitmap out = Bitmap.createBitmap(outWidth, outH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        for (int i = 0; i < count; i++) {
            int index = start + i;
            if (index >= uris.size()) break;   // 后面没图 → 保持透明底
            Bitmap bmp = decode(ctx, uris.get(index), cell, cell);
            if (bmp == null) continue;         // 单张失败不留白，继续下一格
            drawCell(canvas, bmp, (i % cols) * cell, (i / cols) * cell, cell, cell, paint);
        }
        return out;
    }
}
