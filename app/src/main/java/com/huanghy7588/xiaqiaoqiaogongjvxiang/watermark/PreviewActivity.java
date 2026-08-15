package com.huanghy7588.xiaqiaoqiaogongjvxiang.watermark;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 大图预览 Activity。
 *
 * 功能：
 * 1. 全屏黑底查看当前图片大图（含水印效果）。
 * 2. 左右按钮切换上一张 / 下一张。
 * 3. 可直接拖拽调整水印位置，改动会同步保存（与主界面共用同一份 ImageData 列表）。
 *
 * 数据传递：与主界面同进程，直接使用静态字段共享图片列表和当前索引，
 * 简单可靠，Activity 关闭时清空引用避免泄漏。
 */
public class PreviewActivity extends AppCompatActivity {

    /** 共享的图片列表（与主界面同一个引用，拖拽结果直接生效） */
    public static List<ImageData> sharedImages;
    /** 共享的当前索引 */
    public static int sharedIndex;
    /** 共享的水印文字 */
    public static String sharedText;
    /** 共享的描边开关 */
    public static boolean sharedStroke;
    /** 共享的序号开关 */
    public static boolean sharedNumbering;
    /** 是否打开过预览（主界面据此判断 onResume 时要不要同步预览里的改动） */
    public static boolean sharedUsed = false;

    private WatermarkView previewView;
    private TextView tvIndex;

    /** 当前预览用 Bitmap */
    private Bitmap currentBitmap;

    private int currentIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

        previewView = findViewById(R.id.preview_view);
        tvIndex = findViewById(R.id.tv_preview_index);

        if (sharedImages == null || sharedImages.isEmpty()) {
            Toast.makeText(this, "没有可预览的图片", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 点击图片空白处关闭预览
        previewView.setOnTapListener(this::finish);

        Button btnPrev = findViewById(R.id.btn_prev);
        Button btnNext = findViewById(R.id.btn_next);
        Button btnClose = findViewById(R.id.btn_close);
        btnPrev.setOnClickListener(v -> navigate(-1));
        btnNext.setOnClickListener(v -> navigate(1));
        btnClose.setOnClickListener(v -> finish());

        currentIndex = Math.min(Math.max(sharedIndex, 0), sharedImages.size() - 1);
        sharedUsed = true;
        showImage();
    }

    /** 显示当前索引的图片 */
    private void showImage() {
        ImageData data = sharedImages.get(currentIndex);

        // 回收旧 Bitmap
        if (currentBitmap != null) {
            currentBitmap.recycle();
            currentBitmap = null;
        }

        // 大图预览用更高分辨率加载
        currentBitmap = loadSampledBitmap(data.uri, 2048);
        if (currentBitmap != null) {
            previewView.setImageBitmap(currentBitmap);
            previewView.setWatermarkText(sharedText);
            previewView.setStrokeEnabled(sharedStroke);
            previewView.setNumberingEnabled(sharedNumbering);
            previewView.setCurrentNumber(currentIndex + 1);
            previewView.setPositionMode(data.positionMode);
            previewView.setPositionFraction(data.centerXFrac, data.centerYFrac);
            previewView.setTextSizeFactor(data.textSizeFactor);
        } else {
            Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show();
        }

        tvIndex.setText(String.format("%d / %d", currentIndex + 1, sharedImages.size()));
    }

    /** 切换图片 */
    private void navigate(int delta) {
        saveCurrent();
        int newIndex = currentIndex + delta;
        if (newIndex < 0) newIndex = 0;
        if (newIndex >= sharedImages.size()) newIndex = sharedImages.size() - 1;
        if (newIndex != currentIndex) {
            currentIndex = newIndex;
            showImage();
        }
    }

    /** 保存当前预览里的水印状态到 ImageData（与主界面共享，直接生效） */
    private void saveCurrent() {
        ImageData data = sharedImages.get(currentIndex);
        data.positionMode = previewView.getPositionMode();
        data.centerXFrac = previewView.getActualCenterXFrac();
        data.centerYFrac = previewView.getActualCenterYFrac();
        data.textSizeFactor = previewView.getTextSizeFactor();
    }

    @Override
    public void finish() {
        saveCurrent();
        sharedIndex = currentIndex;
        super.finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清空静态引用，避免泄漏（主界面 onResume 在此之前已执行完同步）
        sharedImages = null;
        sharedText = null;
        sharedUsed = false;
        if (currentBitmap != null && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
            currentBitmap = null;
        }
    }

    // ==================== 图片加载 ====================

    /** 加载图片 Bitmap，降采样到指定宽度以内 */
    private Bitmap loadSampledBitmap(android.net.Uri uri, int reqWidth) {
        InputStream is = null;
        try {
            // 先只读尺寸
            is = getContentResolver().openInputStream(uri);
            if (is == null) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, opts);
            is.close();
            is = null;

            // 计算采样率
            int sampleSize = 1;
            while (opts.outWidth / sampleSize > reqWidth) {
                sampleSize *= 2;
            }
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sampleSize;

            // 重新打开流读取像素
            is = getContentResolver().openInputStream(uri);
            if (is == null) return null;
            Bitmap bmp = BitmapFactory.decodeStream(is, null, opts);
            is.close();
            is = null;
            return bmp;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (is != null) {
                try { is.close(); } catch (IOException ignored) {}
            }
        }
    }
}
