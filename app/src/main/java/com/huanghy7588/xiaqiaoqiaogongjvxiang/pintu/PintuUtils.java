package com.huanghy7588.xiaqiaoqiaogongjvxiang.pintu;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
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

    /** 拼图张数对应列数（默认横排） */
    public static int colsFor(int count) {
        return colsFor(count, true);
    }

    /**
     * 拼图张数对应列数。
     *
     * @param landscape true=宽优先（如 15 张为 5 列 3 行），false=高优先（15 张为 3 列 5 行）
     */
    public static int colsFor(int count, boolean landscape) {
        switch (count) {
            case 9:
            case 12:
                return 3;
            case 15:
                return landscape ? 5 : 3;
            default:
                return 2;
        }
    }

    /** 拼图张数对应行数 */
    public static int rowsFor(int count) {
        return rowsFor(count, true);
    }

    /** 拼图张数对应行数（可指定横竖） */
    public static int rowsFor(int count, boolean landscape) {
        int cols = colsFor(count, landscape);
        return (count + cols - 1) / cols;
    }

    /** 解码图片：返回不小于目标格子 2 倍的图（尽量保画质），并修正相册照片的旋转角度 */
    public static Bitmap decode(Context ctx, Uri uri, int maxW, int maxH) {
        return decode(ctx, uri, maxW, maxH, true);
    }

    /**
     * 解码图片。
     *
     * 解码通道（顺序）：
     * 1. ImageDecoder（API 28+，系统官方解码器，对 HEIC/HEIF/WebP 等拍出来的照片格式支持最全）；
     * 2. BitmapFactory 采样解码；
     * 3. BitmapFactory 原尺寸解码。
     * 三级全失败才返回 null 并打日志 —— 避免「读不到图」被静默跳过，拼出一整张透明空白图。
     */
    public static Bitmap decode(Context ctx, Uri uri, int maxW, int maxH, boolean allowFallback) {
        Bitmap bmp = null;
        if (allowFallback && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            bmp = decodeByImageDecoder(ctx, uri, maxW, maxH);
        }
        if (bmp == null) {
            bmp = decodeByFactory(ctx, uri, maxW, maxH, allowFallback);
        }
        if (bmp == null) {
            Log.e(TAG, "decode failed: uri=" + uri);
            return null;
        }
        int rotation = readRotation(ctx, uri);
        if (rotation != 0) {
            bmp = rotate(bmp, rotation);
        }
        Log.d(TAG, "decode ok: " + uri + " -> " + bmp.getWidth() + "x" + bmp.getHeight());
        return bmp;
    }

    /** 通道 1：系统 ImageDecoder 解码（API 28+），可指定目标尺寸避免解码整张大图 */
    private static Bitmap decodeByImageDecoder(Context ctx, Uri uri, int maxW, int maxH) {
        try {
            ImageDecoder.Source src = ImageDecoder.createSource(ctx.getContentResolver(), uri);
            final int tw = Math.max(1, maxW);
            final int th = Math.max(1, maxH);
            return ImageDecoder.decodeBitmap(src, (decoder, w, h) -> {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // 只解到格子够用的尺寸（约 2 倍格子），省内存又够清晰
                    decoder.setTargetSize(tw * 2, th * 2);
                }
            });
        } catch (Exception | LinkageError e) {
            Log.w(TAG, "ImageDecoder failed, try BitmapFactory: " + uri, e);
            return null;
        }
    }

    /** 通道 2/3：BitmapFactory 解码，先按采样倍数（省内存），失败再退到原尺寸 */
    private static Bitmap decodeByFactory(Context ctx, Uri uri, int maxW, int maxH, boolean allowFallback) {
        int ow = 0, oh = 0, sample = 1;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            readBitmap(ctx, uri, bounds);
            ow = bounds.outWidth;
            oh = bounds.outHeight;
            if (ow > 0 && oh > 0) {
                while (ow / sample > maxW * 2 && oh / sample > maxH * 2) {
                    sample *= 2;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "read bounds failed: " + uri, e);
        }

        Bitmap bmp = readBitmap(ctx, uri, sampleOptions(sample));
        if (bmp == null && sample > 1) {
            bmp = readBitmap(ctx, uri, sampleOptions(1));   // 采样失败 → 原尺寸再来一次
        }
        if (bmp == null && allowFallback) {
            bmp = readBitmap(ctx, uri, sampleOptions(1));
        }
        return bmp;
    }

    private static BitmapFactory.Options sampleOptions(int sample) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return opts;
    }

    /**
     * 这张图能不能读出来（只解一张小图来试，成本低）。
     * 用于在导入时就拦掉读不出来的格式（例如部分机型读不了 HEIC），
     * 而不是让它在生成时悄悄变成一张全透明的空白拼图。
     */
    public static boolean readable(Context ctx, Uri uri) {
        return decode(ctx, uri, 64, 64) != null;
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
     * @param start   本组第一张图在列表中的位置
     * @param count   本组格子数
     * @param failOut 长度 2 的计数容器：[0]=成功画上去的格数，[1]=解码失败的张数
     */
    public static Bitmap buildGrid(Context ctx, List<Uri> uris, int start, int count, int cols,
                                   int rows, int outWidth, int[] failOut) {
        int cell = Math.max(1, outWidth / cols);
        int outW = cell * cols;
        int outH = cell * rows;
        Bitmap out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        int painted = 0;
        for (int i = 0; i < count; i++) {
            int index = start + i;
            if (index >= uris.size()) break;   // 后面没图 → 保持透明底
            Bitmap bmp = decode(ctx, uris.get(index), cell, cell);
            if (bmp == null) {
                Log.e(TAG, "buildGrid: 第 " + (index + 1) + " 张解码失败，跳过这一格");
                failOut[1]++;
                continue;                      // 单张失败只空这一格，其余照常拼
            }
            drawCell(canvas, bmp, (i % cols) * cell, (i / cols) * cell, cell, cell, paint);
            painted++;
        }
        failOut[0] += painted;
        // 始终纯透明底：导入的原图本来就可能是透明 PNG，没有任何底色填充
        Log.d(TAG, "buildGrid: 画了 " + painted + " 格 -> " + outW + "x" + outH
                + (painted == 0 ? "（全透明：一张都没读出来）" : ""));
        return out;
    }
}
