package com.huanghy7588.xiaqiaoqiaogongjvxiang.watermark;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 素材水印工具 Activity。
 *
 * 核心流程：
 * 1. 导入多张图片。
 * 2. 输入水印文字，可选择描边、序号。
 * 3. 拖拽或微调水印位置和大小。
 * 4. 可将当前图的位置应用到所有图。
 * 5. 导出带水印的图片到相册。
 */
public class WatermarkToolActivity extends AppCompatActivity {

    private WatermarkView watermarkView;
    private EditText etText;
    private SwitchMaterial swStroke, swNumbering;
    private TextView tvIndex, tvNoImage;

    /** 导入的图片列表 */
    private final List<ImageData> imageList = new ArrayList<>();
    /** 当前显示的图片索引 */
    private int currentIndex = 0;

    /** 当前预览用 Bitmap */
    private Bitmap currentBitmap;

    /** 图片选择器 */
    private final ActivityResultLauncher<String[]> imagePicker = registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(),
            uris -> {
                if (uris != null && !uris.isEmpty()) {
                    for (Uri uri : uris) {
                        try {
                            getContentResolver().takePersistableUriPermission(
                                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException ignored) {
                        }
                        imageList.add(new ImageData(uri));
                    }
                    currentIndex = 0;
                    showCurrentImage();
                    Toast.makeText(this, "已导入 " + uris.size() + " 张图片",
                            Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watermark);

        initViews();
        initListeners();
        updateIndexText();
    }

    private void initViews() {
        watermarkView = findViewById(R.id.watermark_view);
        etText = findViewById(R.id.et_text);
        swStroke = findViewById(R.id.sw_stroke);
        swNumbering = findViewById(R.id.sw_numbering);
        tvIndex = findViewById(R.id.tv_index);
        tvNoImage = findViewById(R.id.tv_no_image);
    }

    private void initListeners() {
        // 文字输入
        etText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                watermarkView.setWatermarkText(s.toString());
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        // 描边开关
        swStroke.setOnCheckedChangeListener((button, checked) ->
                watermarkView.setStrokeEnabled(checked));

        // 序号开关
        swNumbering.setOnCheckedChangeListener((button, checked) ->
                watermarkView.setNumberingEnabled(checked));

        // 微调按钮
        findViewById(R.id.btn_left).setOnClickListener(v -> moveBy(-0.01f, 0));
        findViewById(R.id.btn_right).setOnClickListener(v -> moveBy(0.01f, 0));
        findViewById(R.id.btn_up).setOnClickListener(v -> moveBy(0, -0.01f));
        findViewById(R.id.btn_down).setOnClickListener(v -> moveBy(0, 0.01f));
        findViewById(R.id.btn_bigger).setOnClickListener(v -> scaleBy(0.005f));
        findViewById(R.id.btn_smaller).setOnClickListener(v -> scaleBy(-0.005f));

        // 应用位置到所有图
        findViewById(R.id.btn_apply_all).setOnClickListener(v -> applyPositionToAll());

        // 重置位置
        findViewById(R.id.btn_reset).setOnClickListener(v -> resetPosition());

        // 上一张 / 下一张
        findViewById(R.id.btn_prev).setOnClickListener(v -> navigate(-1));
        findViewById(R.id.btn_next).setOnClickListener(v -> navigate(1));

        // 导入图片
        findViewById(R.id.btn_import).setOnClickListener(v ->
                imagePicker.launch(new String[]{"image/*"}));

        // 导出
        findViewById(R.id.btn_export).setOnClickListener(v -> exportImages());
    }

    // ==================== 图片显示与导航 ====================

    /** 显示当前索引的图片 */
    private void showCurrentImage() {
        if (imageList.isEmpty()) {
            tvNoImage.setVisibility(android.view.View.VISIBLE);
            watermarkView.setImageBitmap(null);
            currentBitmap = null;
            updateIndexText();
            return;
        }

        tvNoImage.setVisibility(android.view.View.GONE);
        ImageData data = imageList.get(currentIndex);

        // 回收旧 Bitmap
        if (currentBitmap != null) {
            currentBitmap.recycle();
            currentBitmap = null;
        }

        // 加载图片（采样降分辨率用于预览）
        currentBitmap = loadSampledBitmap(data.uri, 1080);
        if (currentBitmap != null) {
            watermarkView.setImageBitmap(currentBitmap);
            // 应用该图保存的位置
            watermarkView.setPositionFraction(data.centerXFrac, data.centerYFrac);
            watermarkView.setTextSizeFactor(data.textSizeFactor);
            watermarkView.setCurrentNumber(currentIndex + 1);
            watermarkView.setWatermarkText(etText.getText().toString());
            watermarkView.setStrokeEnabled(swStroke.isChecked());
            watermarkView.setNumberingEnabled(swNumbering.isChecked());
        } else {
            // 图片加载失败，清空预览
            watermarkView.setImageBitmap(null);
            Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show();
        }
        updateIndexText();
    }

    /** 更新索引显示 */
    private void updateIndexText() {
        if (imageList.isEmpty()) {
            tvIndex.setText("0 / 0");
        } else {
            tvIndex.setText(String.format("%d / %d", currentIndex + 1, imageList.size()));
        }
    }

    /** 切换图片 */
    private void navigate(int delta) {
        if (imageList.isEmpty()) return;
        // 保存当前图的位置
        saveCurrentPosition();
        int newIndex = currentIndex + delta;
        if (newIndex < 0) newIndex = 0;
        if (newIndex >= imageList.size()) newIndex = imageList.size() - 1;
        if (newIndex != currentIndex) {
            currentIndex = newIndex;
            showCurrentImage();
        }
    }

    /** 保存当前水印位置到当前 ImageData */
    private void saveCurrentPosition() {
        if (imageList.isEmpty()) return;
        ImageData data = imageList.get(currentIndex);
        data.centerXFrac = watermarkView.getCenterXFrac();
        data.centerYFrac = watermarkView.getCenterYFrac();
        data.textSizeFactor = watermarkView.getTextSizeFactor();
    }

    // ==================== 微调操作 ====================

    /** 微调位置 */
    private void moveBy(float dxFrac, float dyFrac) {
        float x = watermarkView.getCenterXFrac() + dxFrac;
        float y = watermarkView.getCenterYFrac() + dyFrac;
        watermarkView.setPositionFraction(x, y);
    }

    /** 微调大小 */
    private void scaleBy(float delta) {
        watermarkView.setTextSizeFactor(watermarkView.getTextSizeFactor() + delta);
    }

    /** 重置位置到右下角默认值 */
    private void resetPosition() {
        watermarkView.setPositionFraction(0.82f, 0.88f);
        watermarkView.setTextSizeFactor(0.06f);
    }

    /** 将当前图的水印位置应用到所有图 */
    private void applyPositionToAll() {
        if (imageList.isEmpty()) return;
        float x = watermarkView.getCenterXFrac();
        float y = watermarkView.getCenterYFrac();
        float size = watermarkView.getTextSizeFactor();
        for (ImageData data : imageList) {
            data.centerXFrac = x;
            data.centerYFrac = y;
            data.textSizeFactor = size;
        }
        Toast.makeText(this, "已将当前位置应用到所有图片", Toast.LENGTH_SHORT).show();
    }

    // ==================== 导出 ====================

    /** 导出所有图片到相册 */
    private void exportImages() {
        if (imageList.isEmpty()) {
            Toast.makeText(this, "请先导入图片", Toast.LENGTH_SHORT).show();
            return;
        }
        if (etText.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "请输入水印文字", Toast.LENGTH_SHORT).show();
            return;
        }

        saveCurrentPosition();

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(String.format(getString(R.string.wm_exporting), 0, imageList.size()));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(imageList.size());
        progressDialog.setCancelable(false);
        progressDialog.show();

        String text = etText.getText().toString();
        boolean stroke = swStroke.isChecked();
        boolean numbering = swNumbering.isChecked();

        Handler handler = new Handler(Looper.getMainLooper());
        final int[] successCount = {0};
        final int total = imageList.size();

        new Thread(() -> {
            for (int i = 0; i < total; i++) {
                ImageData data = imageList.get(i);
                boolean ok = exportSingle(data, text, stroke, numbering, i + 1);
                if (ok) successCount[0]++;
                final int progress = i + 1;
                handler.post(() -> {
                    // Activity 可能已销毁
                    if (isFinishing() || isDestroyed()) return;
                    progressDialog.setMessage(String.format(
                            getString(R.string.wm_exporting), progress, total));
                    progressDialog.setProgress(progress);
                });
            }
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                progressDialog.dismiss();
                if (successCount[0] > 0) {
                    Toast.makeText(this,
                            String.format(getString(R.string.wm_export_done), successCount[0]),
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, getString(R.string.wm_export_fail),
                            Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    /** 导出单张图片（限制最大 4096px 宽度防止 OOM） */
    private boolean exportSingle(ImageData data, String text, boolean stroke,
                                 boolean numbering, int number) {
        Bitmap fullBitmap = loadSampledBitmap(data.uri, 4096);
        if (fullBitmap == null) return false;

        // 使用临时 WatermarkView 逻辑绘制水印到原图
        WatermarkExporter exporter = new WatermarkExporter();
        Bitmap result = exporter.export(fullBitmap, text, stroke, numbering, number,
                data.centerXFrac, data.centerYFrac, data.textSizeFactor);
        fullBitmap.recycle();

        if (result == null) return false;

        // 保存到相册
        boolean saved = saveToGallery(result);
        result.recycle();
        return saved;
    }

    /** 保存 Bitmap 到相册 Pictures/夏乔乔工具箱 */
    private boolean saveToGallery(Bitmap bitmap) {
        OutputStream os = null;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME,
                    "watermark_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/夏乔乔工具箱");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
            }

            ContentResolver resolver = getContentResolver();
            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) return false;

            os = resolver.openOutputStream(uri);
            if (os == null) return false;
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os);
            os.close();
            os = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(uri, values, null, null);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (os != null) {
                try { os.close(); } catch (IOException ignored) {}
            }
        }
    }

    // ==================== 图片加载 ====================

    /**
     * 加载图片 Bitmap，可选择降采样。
     * @param reqWidth 目标宽度，0 表示加载原图
     */
    private Bitmap loadSampledBitmap(Uri uri, int reqWidth) {
        InputStream is = null;
        try {
            if (reqWidth == 0) {
                // 加载原图
                is = getContentResolver().openInputStream(uri);
                if (is == null) return null;
                Bitmap bmp = BitmapFactory.decodeStream(is);
                is.close();
                is = null;
                return bmp;
            }

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
            // 确保流关闭
            if (is != null) {
                try { is.close(); } catch (IOException ignored) {}
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentBitmap != null && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
            currentBitmap = null;
        }
    }
}
