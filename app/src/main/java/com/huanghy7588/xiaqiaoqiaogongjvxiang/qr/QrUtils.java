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

    /** 从 Bitmap 解码二维码，成功返回文本，失败返回 null。 */
    public static String decodeBitmap(Bitmap bitmap) {
        if (bitmap == null) return null;
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w <= 0 || h <= 0) return null;
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        RGBLuminanceSource source = new RGBLuminanceSource(w, h, pixels);
        BinaryBitmap binary = new BinaryBitmap(new HybridBinarizer(source));
        MultiFormatReader reader = new MultiFormatReader();
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS,
                Collections.singletonList(BarcodeFormat.QR_CODE));
        reader.setHints(hints);
        try {
            Result result = reader.decode(binary);
            return result != null ? result.getText() : null;
        } catch (Exception e) {
            return null;
        }
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
