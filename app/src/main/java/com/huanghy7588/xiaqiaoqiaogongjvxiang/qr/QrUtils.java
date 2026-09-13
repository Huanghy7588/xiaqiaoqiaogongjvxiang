package com.huanghy7588.xiaqiaoqiaogongjvxiang.qr;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 二维码工具核心：编码（文字→BitMatrix）、解码（图片→文字）、保存（Bitmap→相册）。
 */
public class QrUtils {

    /** 编码文字为二维码模块矩阵（不含静区，原始模块）。使用 UTF-8 + M 级纠错，确保中文不乱码。 */
    public static BitMatrix encodeText(String text) throws WriterException {
        if (text == null || text.isEmpty()) {
            throw new WriterException("empty content");
        }
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 0);
        QRCode code = Encoder.encode(text, ErrorCorrectionLevel.M, hints);
        // QRCode.getMatrix() 返回 ByteMatrix，转换为 BitMatrix 便于自定义渲染
        ByteMatrix bm = code.getMatrix();
        int w = bm.getWidth();
        int h = bm.getHeight();
        BitMatrix matrix = new BitMatrix(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (bm.get(x, y) != 0) {
                    matrix.set(x, y);
                }
            }
        }
        return matrix;
    }

    /**
     * 从 Bitmap 解码二维码，成功返回文本，失败返回 null。
     * 鲁棒策略：
     *  1) 同时用「原始亮度」与「最暗通道 min(R,G,B)」两种灰度量，后者对彩色码
     *     （微信绿 / 支付宝蓝 / QQ 蓝等）友好——彩色模块在任意单色通道下都偏暗，
     *     白色底 min≈255 偏亮，据此能正确区分黑白色块。
     *  2) 对每个来源尝试 HybridBinarizer + GlobalHistogramBinarizer 两种二值化。
     *  3) 对 0°/90°/180°/270° 四个方向都尝试，兼容截图未摆正的情况。
     *  4) 整图失败后，再对中心 75% / 55% 区域重试（收款码常居中且被周边干扰）。
     */
    public static String decodeBitmap(Bitmap bitmap) {
        if (bitmap == null) return null;
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w <= 0 || h <= 0) return null;
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);

        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS,
                Collections.singletonList(BarcodeFormat.QR_CODE));

        // 1) 整图
        String r = tryAllStrategies(pixels, w, h, hints);
        if (r != null) return r;
        // 2) 中心 75%
        int[] c75 = cropCenter(pixels, w, h, 0.75f);
        if (c75 != null) {
            r = tryAllStrategies(c75, (int) (w * 0.75f), (int) (h * 0.75f), hints);
            if (r != null) return r;
        }
        // 3) 中心 55%
        int[] c55 = cropCenter(pixels, w, h, 0.55f);
        if (c55 != null) {
            r = tryAllStrategies(c55, (int) (w * 0.55f), (int) (h * 0.55f), hints);
            if (r != null) return r;
        }
        return null;
    }

    /** 对一组像素尝试「双灰度量 × 双二值化 × 四方向」解码。 */
    private static String tryAllStrategies(int[] base, int w, int h, Map<DecodeHintType, Object> hints) {
        int[] minChannel = toMinChannel(base);
        int[][] variants = new int[][] { base, minChannel };
        for (int[] cur0 : variants) {
            int vw = w, vh = h;
            int[] cur = cur0;
            for (int rot = 0; rot < 4; rot++) {
                RGBLuminanceSource src = new RGBLuminanceSource(vw, vh, cur);
                String t1 = tryDecode(new BinaryBitmap(new HybridBinarizer(src)), hints);
                if (t1 != null) return t1;
                String t2 = tryDecode(new BinaryBitmap(new GlobalHistogramBinarizer(src)), hints);
                if (t2 != null) return t2;
                int[] rotated = rotateCW(cur, vw, vh);
                cur = rotated;
                int tmp = vw; vw = vh; vh = tmp;
            }
        }
        return null;
    }

    /** 用给定二值化源尝试解码，失败返回 null。 */
    private static String tryDecode(BinaryBitmap bb, Map<DecodeHintType, Object> hints) {
        MultiFormatReader reader = new MultiFormatReader();
        reader.setHints(hints);
        try {
            Result result = reader.decode(bb);
            return (result != null && result.getText() != null) ? result.getText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 把像素转换为「最暗通道」灰度图（R=G=B=min(R,G,B)），用于增强彩色二维码的识别。 */
    private static int[] toMinChannel(int[] px) {
        int[] out = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int r = (c >> 16) & 0xff;
            int g = (c >> 8) & 0xff;
            int b = c & 0xff;
            int m = Math.min(r, Math.min(g, b));
            out[i] = (c & 0xFF000000) | (m << 16) | (m << 8) | m;
        }
        return out;
    }

    /** 顺时针旋转 90° 像素数组，返回新数组（宽高互换，新宽=原高 h）。 */
    private static int[] rotateCW(int[] px, int w, int h) {
        int[] out = new int[h * w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // 顺时针 90°：新图 [x][h-1-y] = 原图 [y][x]
                int nx = y;
                int ny = w - 1 - x;
                out[ny * h + nx] = px[y * w + x];
            }
        }
        return out;
    }

    /** 取图像中心 ratio 比例的子区域（收款码常居中），失败返回 null。 */
    private static int[] cropCenter(int[] px, int w, int h, float ratio) {
        if (ratio <= 0 || ratio >= 1) return null;
        int cw = Math.max(1, (int) (w * ratio));
        int ch = Math.max(1, (int) (h * ratio));
        int x0 = (w - cw) / 2;
        int y0 = (h - ch) / 2;
        int[] out = new int[cw * ch];
        for (int y = 0; y < ch; y++) {
            System.arraycopy(px, (y0 + y) * w + x0, out, y * cw, cw);
        }
        return out;
    }

    /** 保存 Bitmap 到相册（Pictures/夏乔乔工具箱），返回是否成功。 */
    public static boolean saveBitmapToGallery(Context ctx, Bitmap bmp) {
        if (bmp == null) return false;
        ContentValues values = new ContentValues();
        String name = "qrcode_" + System.currentTimeMillis() + ".png";
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/夏乔乔工具箱");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }
        android.content.ContentResolver resolver = ctx.getContentResolver();
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) return false;
        java.io.OutputStream os = null;
        try {
            os = resolver.openOutputStream(uri);
            if (os == null) return false;
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
            os.close();
            os = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(uri, values, null, null);
            }
            return true;
        } catch (java.io.IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (os != null) try { os.close(); } catch (java.io.IOException ignored) {
            }
        }
    }
}
