package com.huanghy7588.xiaqiaoqiaogongjvxiang.watermark;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private TextView tvIndex;
    private Button btnImportEmpty;

    /** 导入的图片列表 */
    private final List<ImageData> imageList = new ArrayList<>();
    /** 当前显示的图片索引 */
    private int currentIndex = 0;

    /** 当前预览用 Bitmap */
    private Bitmap currentBitmap;

    /** 记忆：水印设置持久化（SharedPreferences），避免误返回后文字与设置丢失 */
    private static final String PREFS_NAME = "wm_prefs";
    private static final String KEY_TEXT = "watermark_text";
    private static final String KEY_STROKE = "stroke";
    private static final String KEY_NUMBERING = "numbering";
    private static final String KEY_POS_MODE = "position_mode";

    /** M2 修复：后台图片解码线程池，避免大图解码阻塞主线程 */
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();
    /** 当前正在加载的图片索引，用于判断后台加载完成后是否仍需要显示 */
    private volatile int loadingIndex = -1;

    /** 图片选择器（GetMultipleContents：走系统相册/文件选择器，兼容多数机型多选） */
    private final ActivityResultLauncher<String> imagePicker = registerForActivityResult(
            new ActivityResultContracts.GetMultipleContents(),
            uris -> {
                if (uris != null && !uris.isEmpty()) {
                    int defaultMode = getSelectedPositionMode();
                    for (Uri uri : uris) {
                        ImageData data = new ImageData(uri);
                        // 新导入的图使用当前选中的默认位置模式
                        data.positionMode = defaultMode;
                        imageList.add(data);
                    }
                    currentIndex = imageList.size() - uris.size();
                    showCurrentImage();
                    Toast.makeText(this, getString(R.string.wm_imported, uris.size()),
                            Toast.LENGTH_SHORT).show();
                }
            });

    /** 存储权限请求（Android 9 及以下保存图片用，M1 修复） */
    private final ActivityResultLauncher<String> storagePermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (Boolean.TRUE.equals(granted)) {
                    startExport();
                } else {
                    Toast.makeText(this, getString(R.string.wm_export_fail), Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watermark);

        initViews();
        initListeners();
        loadPrefs();
        // 统一初始化预览区状态（空列表时显示「导入图片」按钮、隐藏序号）
        showCurrentImage();
        updateIndexText();
    }

    private void initViews() {
        watermarkView = findViewById(R.id.watermark_view);
        etText = findViewById(R.id.et_text);
        swStroke = findViewById(R.id.sw_stroke);
        swNumbering = findViewById(R.id.sw_numbering);
        tvIndex = findViewById(R.id.tv_index);
        btnImportEmpty = findViewById(R.id.btn_import_empty);
    }

    private void initListeners() {
        // 返回首页（结束本页回到主界面）
        findViewById(R.id.btn_back_home).setOnClickListener(v -> finish());

        // 主界面小图不允许拖拽水印，调整位置请进大图预览或用"位置调整"
        watermarkView.setDragEnabled(false);

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

        // 默认位置切换（居中 / 左下角）
        android.widget.RadioGroup rgPosition = findViewById(R.id.rg_position);
        rgPosition.setOnCheckedChangeListener((group, checkedId) ->
                watermarkView.setPositionMode(getSelectedPositionMode()));

        // 点击预览图空白处进入大图预览
        watermarkView.setOnTapListener(this::openLargePreview);

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
                imagePicker.launch("image/*"));

        // 空状态（尚未导入图片）下的「导入图片」按钮，逻辑与上方导入完全一致
        btnImportEmpty.setOnClickListener(v -> imagePicker.launch("image/*"));

        // 导出
        findViewById(R.id.btn_export).setOnClickListener(v -> exportImages());

        // 清除导入的图片（带二次确认，文字与设置保留）
        findViewById(R.id.btn_clear_images).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.wm_clear_confirm_title)
                    .setMessage(R.string.wm_clear_confirm_msg)
                    .setPositiveButton(R.string.wm_clear_confirm_ok, (d, w) -> clearImages())
                    .setNegativeButton(R.string.wm_clear_confirm_cancel, null)
                    .show();
        });
    }

    // ==================== 图片显示与导航 ====================

    /** 显示当前索引的图片（M2 修复：后台解码，避免大图卡主线程） */
    private void showCurrentImage() {
        if (imageList.isEmpty()) {
            btnImportEmpty.setVisibility(android.view.View.VISIBLE);
            watermarkView.setImageBitmap(null);
            if (currentBitmap != null) { currentBitmap.recycle(); currentBitmap = null; }
            updateIndexText();
            return;
        }

        btnImportEmpty.setVisibility(android.view.View.GONE);
        final ImageData data = imageList.get(currentIndex);
        final int indexForLoad = currentIndex;
        loadingIndex = indexForLoad;

        // 先清空旧 Bitmap，显示空白占位
        if (currentBitmap != null) { currentBitmap.recycle(); currentBitmap = null; }
        watermarkView.setImageBitmap(null);
        updateIndexText();

        // M2 修复：后台线程解码，完成后回到主线程设置
        imageExecutor.execute(() -> {
            final Bitmap bmp = loadSampledBitmap(data.uri, 1080);
            runOnUiThread(() -> {
                // 后台加载期间用户可能已切到其他图片，只有索引一致才设置
                if (loadingIndex != indexForLoad) {
                    if (bmp != null) bmp.recycle();
                    return;
                }
                if (bmp == null) {
                    Toast.makeText(this, R.string.wm_load_fail, Toast.LENGTH_SHORT).show();
                    return;
                }
                currentBitmap = bmp;
                watermarkView.setImageBitmap(bmp);
                watermarkView.setPositionMode(data.positionMode);
                watermarkView.setPositionFraction(data.centerXFrac, data.centerYFrac);
                watermarkView.setTextSizeFactor(data.textSizeFactor);
                watermarkView.setCurrentNumber(currentIndex + 1);
                watermarkView.setWatermarkText(etText.getText().toString());
                watermarkView.setStrokeEnabled(swStroke.isChecked());
                watermarkView.setNumberingEnabled(swNumbering.isChecked());
            });
        });
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
        data.positionMode = watermarkView.getPositionMode();
        data.centerXFrac = watermarkView.getActualCenterXFrac();
        data.centerYFrac = watermarkView.getActualCenterYFrac();
        data.textSizeFactor = watermarkView.getTextSizeFactor();
    }

    // ==================== 微调操作 ====================

    /** 微调位置（切换为自定义模式，以当前实际位置为基准，不跳变） */
    private void moveBy(float dxFrac, float dyFrac) {
        float x, y;
        if (watermarkView.getPositionMode() == ImageData.POSITION_CUSTOM) {
            x = watermarkView.getCenterXFrac() + dxFrac;
            y = watermarkView.getCenterYFrac() + dyFrac;
        } else {
            // 居中/左下角模式下以当前实际显示位置为基准
            x = watermarkView.getActualCenterXFrac() + dxFrac;
            y = watermarkView.getActualCenterYFrac() + dyFrac;
        }
        watermarkView.setPositionFraction(x, y, true);
    }

    /** 微调大小 */
    private void scaleBy(float delta) {
        watermarkView.setTextSizeFactor(watermarkView.getTextSizeFactor() + delta);
    }

    /** 重置位置到当前选中的默认模式 */
    private void resetPosition() {
        watermarkView.setPositionMode(getSelectedPositionMode());
        watermarkView.setTextSizeFactor(0.06f);
    }

    /** 将当前图的水印位置应用到所有图 */
    private void applyPositionToAll() {
        if (imageList.isEmpty()) return;
        int mode = watermarkView.getPositionMode();
        float x = watermarkView.getActualCenterXFrac();
        float y = watermarkView.getActualCenterYFrac();
        float size = watermarkView.getTextSizeFactor();
        for (ImageData data : imageList) {
            data.positionMode = mode;
            data.centerXFrac = x;
            data.centerYFrac = y;
            data.textSizeFactor = size;
        }
        Toast.makeText(this, R.string.wm_applied_all, Toast.LENGTH_SHORT).show();
    }

    /** 获取当前选中的水印位置模式（居中 / 左下角 / 右下角） */
    private int getSelectedPositionMode() {
        android.widget.RadioButton rbCenter = findViewById(R.id.rb_pos_center);
        if (rbCenter.isChecked()) return ImageData.POSITION_CENTER;
        android.widget.RadioButton rbBottomLeft = findViewById(R.id.rb_pos_bottom_left);
        if (rbBottomLeft.isChecked()) return ImageData.POSITION_BOTTOM_LEFT;
        return ImageData.POSITION_BOTTOM_RIGHT;
    }

    // ==================== 记忆（持久化水印设置） ====================

    /** 恢复上次保存的水印文字与基础设置 */
    private void loadPrefs() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String text = sp.getString(KEY_TEXT, "");
        if (text != null && !text.isEmpty()) {
            // 触发 TextWatcher 同步给 watermarkView，这里无需重复设置
            etText.setText(text);
        }
        boolean stroke = sp.getBoolean(KEY_STROKE, false);
        boolean numbering = sp.getBoolean(KEY_NUMBERING, false);
        swStroke.setChecked(stroke);
        swNumbering.setChecked(numbering);
        int mode = sp.getInt(KEY_POS_MODE, ImageData.POSITION_CENTER);
        int rbId = R.id.rb_pos_center;
        if (mode == ImageData.POSITION_BOTTOM_LEFT) rbId = R.id.rb_pos_bottom_left;
        else if (mode == ImageData.POSITION_BOTTOM_RIGHT) rbId = R.id.rb_pos_bottom_right;
        ((android.widget.RadioGroup) findViewById(R.id.rg_position)).check(rbId);
    }

    /** 保存当前水印文字与基础设置（返回 / 切后台时调用，避免误退出丢失） */
    private void savePrefs() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(KEY_TEXT, etText.getText().toString());
        editor.putBoolean(KEY_STROKE, swStroke.isChecked());
        editor.putBoolean(KEY_NUMBERING, swNumbering.isChecked());
        editor.putInt(KEY_POS_MODE, getSelectedPositionMode());
        editor.apply();
    }

    /** 清除所有已导入的图片（水印文字与设置保留） */
    private void clearImages() {
        imageList.clear();
        currentIndex = 0;
        loadingIndex = -1;
        if (currentBitmap != null && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
        }
        currentBitmap = null;
        watermarkView.setImageBitmap(null);
        btnImportEmpty.setVisibility(android.view.View.VISIBLE);
        updateIndexText();
        Toast.makeText(this, R.string.wm_cleared_toast, Toast.LENGTH_SHORT).show();
    }

    /** 打开大图预览 */
    private void openLargePreview() {
        if (imageList.isEmpty()) return;
        saveCurrentPosition();
        PreviewActivity.sharedImages = imageList;
        PreviewActivity.sharedIndex = currentIndex;
        PreviewActivity.sharedText = etText.getText().toString();
        PreviewActivity.sharedStroke = swStroke.isChecked();
        PreviewActivity.sharedNumbering = swNumbering.isChecked();
        startActivity(new android.content.Intent(this, PreviewActivity.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从大图预览返回时，同步预览里拖拽过的位置（仅打开过预览才处理）
        if (PreviewActivity.sharedUsed && !imageList.isEmpty()) {
            int idx = PreviewActivity.sharedIndex;
            if (idx >= 0 && idx < imageList.size()) {
                if (idx != currentIndex) {
                    // S2 修复：索引变了必须重新加载对应 Bitmap，否则画面与序号错位
                    currentIndex = idx;
                    showCurrentImage();
                } else {
                    // 同一张，但预览里可能拖拽改了水印位置，重新应用
                    ImageData data = imageList.get(currentIndex);
                    watermarkView.setPositionMode(data.positionMode);
                    watermarkView.setPositionFraction(data.centerXFrac, data.centerYFrac);
                    watermarkView.setTextSizeFactor(data.textSizeFactor);
                }
                updateIndexText();
            }
        }
    }

    // ==================== 导出 ====================

    /** 导出所有图片到相册（入口：先做权限检查，再执行实际导出） */
    private void exportImages() {
        if (imageList.isEmpty()) {
            Toast.makeText(this, R.string.wm_no_images_toast, Toast.LENGTH_SHORT).show();
            return;
        }
        if (etText.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, R.string.wm_no_text_toast, Toast.LENGTH_SHORT).show();
            return;
        }
        // M1 修复：Android 9 及以下通过 MediaStore 写入需要 WRITE_EXTERNAL_STORAGE
        // 运行时权限，此前从未请求，导致导出失败。先请求，授权后再导出。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            storagePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }
        startExport();
    }

    /** 实际执行导出（权限已就绪或运行在 Android 10+） */
    private void startExport() {
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
        // M5 修复：导出前拷贝一份快照，避免子线程与主线程共享 ArrayList 造成数据竞争
        final List<ImageData> snapshot = new ArrayList<>(imageList);

        new Thread(() -> {
            try {
                for (int i = 0; i < total; i++) {
                    ImageData data = snapshot.get(i);
                    boolean ok;
                    try {
                        ok = exportSingle(data, text, stroke, numbering, i + 1);
                    } catch (Throwable t) { // S3/M3 修复：捕获 OOM 等，单张失败不影响其余
                        t.printStackTrace();
                        ok = false;
                    }
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
            } finally {
                // S3 修复：无论成功还是异常，都保证弹窗关闭，避免永久卡死
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()) {
                        progressDialog.dismiss();
                        return;
                    }
                    if (progressDialog.isShowing()) progressDialog.dismiss();
                    if (successCount[0] > 0) {
                        Toast.makeText(this,
                                String.format(getString(R.string.wm_export_done), successCount[0]),
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, getString(R.string.wm_export_fail),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    /** 导出单张图片（限制最大 4096px 宽度防止 OOM） */
    private boolean exportSingle(ImageData data, String text, boolean stroke,
                                 boolean numbering, int number) {
        Bitmap fullBitmap = loadSampledBitmap(data.uri, 4096);
        if (fullBitmap == null) return false;

        // 透明底图片必须导出 PNG（JPEG 不支持透明，会变黑底）
        boolean hasAlpha = fullBitmap.hasAlpha();

        // 使用临时 WatermarkView 逻辑绘制水印到原图
        WatermarkExporter exporter = new WatermarkExporter();
        Bitmap result = exporter.export(fullBitmap, text, stroke, numbering, number,
                data.positionMode, data.centerXFrac, data.centerYFrac, data.textSizeFactor);
        fullBitmap.recycle();

        if (result == null) return false;

        // 保存到相册
        boolean saved = saveToGallery(result, hasAlpha);
        result.recycle();
        return saved;
    }

    /** 保存 Bitmap 到相册 Pictures/夏乔乔工具箱，hasAlpha 为 true 时保存 PNG 保留透明 */
    private boolean saveToGallery(Bitmap bitmap, boolean hasAlpha) {
        OutputStream os = null;
        try {
            String ext = hasAlpha ? ".png" : ".jpg";
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME,
                    "watermark_" + System.currentTimeMillis() + ext);
            values.put(MediaStore.Images.Media.MIME_TYPE,
                    hasAlpha ? "image/png" : "image/jpeg");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/夏乔乔工具箱");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
            }

            ContentResolver resolver = getContentResolver();
            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) return false;

            os = resolver.openOutputStream(uri);
            if (os == null) return false;
            if (hasAlpha) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
            } else {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os);
            }
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
     * 加载图片 Bitmap，降采样到指定宽度以内（M4：同时限制总像素防长图 OOM）。
     * @param reqWidth 目标宽度
     */
    private Bitmap loadSampledBitmap(Uri uri, int reqWidth) {
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

            // M4 修复：同时限制宽度和总像素数，防止长图（如 4096×8000）OOM
            int sampleSize = 1;
            long totalPixels = (long) opts.outWidth * opts.outHeight;
            while (opts.outWidth / sampleSize > reqWidth
                    || totalPixels / (sampleSize * sampleSize) > 24_000_000) {
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
        } catch (IOException | OutOfMemoryError e) { // M3 修复：大图解码可能抛 OOM
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
    protected void onPause() {
        super.onPause();
        // 返回 / 切后台时保存水印文字与设置，下次进入不再丢失
        savePrefs();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        imageExecutor.shutdownNow();
        loadingIndex = -1;
        if (currentBitmap != null && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
            currentBitmap = null;
        }
    }
}
