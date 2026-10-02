package com.huanghy7588.xiaqiaoqiaogongjvxiang.pintu;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 一键拼图：导入图片（顶部横滑）→ 选拼图张数与画布宽度 → 自动分组合成 → 预览大图 → 保存到相册。
 *
 * 规则：
 * 1. 支持 4 / 6 / 8 / 9 / 12 / 15 张一格（15 张可横排 5×3 或竖排 3×5），每格等比居中裁切填满，整张无缝无留白。
 * 2. 导入图片多于一组时按顺序自动分组，输出多张大图（如 8 张选 4 张 → 输出 2 张）。
 * 3. 不足整组的空位保留透明底；导出 PNG，导入原图有透明底时导出也保留透明。
 */
public class PintuActivity extends AppCompatActivity {

    private final List<Uri> uris = new ArrayList<>();
    private final List<Bitmap> thumbs = new ArrayList<>();
    private final List<Bitmap> results = new ArrayList<>();

    private RecyclerView rvThumbs;
    private ThumbAdapter thumbAdapter;
    private TextView tvCount, tvLayoutHint, tvSizeHint, tvResultEmpty;
    private LinearLayout layoutResults;
    private EditText etWidth;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    /** 可选的拼图张数按钮（与布局 id 一一对应） */
    private static final int[] COUNT_IDS = {
            R.id.btn_count_4, R.id.btn_count_6, R.id.btn_count_8,
            R.id.btn_count_9, R.id.btn_count_12, R.id.btn_count_15
    };
    private static final int[] COUNT_VALUES = {4, 6, 8, 9, 12, 15};

    /** 当前选中的拼图张数（每格数量） */
    private int gridCount = 4;
    /** 生成任务的代号：只认最后一轮结果，避免旧任务的结果把新图清掉/回收后显示空白 */
    private int generation;
    /** 15 张时是否横排（true=5 列 3 行，false=3 列 5 行） */
    private boolean landscape = true;
    /** 当前选中的画布宽度 */
    private int outWidth = 1080;

    /** Android 9 及以下保存相册用的待存位图 */
    private Bitmap pendingBmp;
    private String pendingName;
    private final ActivityResultLauncher<String> storagePermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (Boolean.TRUE.equals(granted) && pendingBmp != null) {
                    doSavePreQ(pendingBmp, pendingName);
                    pendingBmp = null;
                    pendingName = null;
                } else {
                    Toast.makeText(this, R.string.pintu_save_fail, Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String[]> pickLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), result -> {
                if (result == null) return;
                List<Uri> added = new ArrayList<>();
                int skipped = 0;
                for (Uri uri : result) {
                    if (uri == null) continue;
                    String mime = getContentResolver().getType(uri);
                    if (mime != null && !mime.startsWith("image/")) continue;
                    if (uris.contains(uri)) continue;
                    if (uris.size() >= PintuUtils.MAX_IMAGES) {
                        Toast.makeText(this, R.string.pintu_max_reached, Toast.LENGTH_SHORT).show();
                        break;
                    }
                    // 读不出来的格式（HEIC 等）直接拦掉，避免拼出一张全透明的空白图
                    if (!PintuUtils.readable(PintuActivity.this, uri)) {
                        skipped++;
                        continue;
                    }
                    uris.add(uri);
                    added.add(uri);
                }
                if (added.isEmpty()) {
                    if (skipped > 0) {
                        Toast.makeText(this, getString(R.string.pintu_unreadable, skipped),
                                Toast.LENGTH_LONG).show();
                    }
                    return;
                }
                refreshThumbs(added);
                updateCountText();
                updateLayoutHint();
                if (skipped > 0) {
                    Toast.makeText(this, getString(R.string.pintu_unreadable, skipped),
                            Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pintu);

        // 统一返回按钮
        findViewById(R.id.btn_back_home).setOnClickListener(v -> finish());
        TextView tvTitle = findViewById(R.id.tv_top_title);
        tvTitle.setText(R.string.pintu_title);
        tvTitle.setVisibility(View.VISIBLE);

        tvCount = findViewById(R.id.tv_count);
        tvLayoutHint = findViewById(R.id.tv_layout_hint);
        tvSizeHint = findViewById(R.id.tv_size_hint);
        tvResultEmpty = findViewById(R.id.tv_result_empty);
        layoutResults = findViewById(R.id.layout_results);
        etWidth = findViewById(R.id.et_width);

        rvThumbs = findViewById(R.id.rv_thumbs);
        rvThumbs.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        thumbAdapter = new ThumbAdapter();
        rvThumbs.setAdapter(thumbAdapter);

        // 导入 / 清空
        findViewById(R.id.btn_import).setOnClickListener(v -> pickLauncher.launch(new String[]{"image/*"}));
        findViewById(R.id.btn_clear).setOnClickListener(v -> askClear());

        // 拼图张数
        bindCountButton(R.id.btn_count_4, 4);
        bindCountButton(R.id.btn_count_6, 6);
        bindCountButton(R.id.btn_count_8, 8);
        bindCountButton(R.id.btn_count_9, 9);
        bindCountButton(R.id.btn_count_12, 12);
        bindCountButton(R.id.btn_count_15, 15);
        bindOrientation();

        // 画布宽度
        bindWidthPreset(R.id.btn_width_720, 720);
        bindWidthPreset(R.id.btn_width_1080, 1080);
        bindWidthPreset(R.id.btn_width_1440, 1440);
        etWidth.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus) updateSizeHint(); });

        // 生成 / 保存
        findViewById(R.id.btn_generate).setOnClickListener(v -> generate());
        findViewById(R.id.btn_save).setOnClickListener(v -> saveAll());

        updateCountText();
        updateSizeHint();
        syncCountButtons();
    }

    // ==================== 导入图片 ====================

    /** 后台解码缩略图并刷新横滑列表 */
    private void refreshThumbs(List<Uri> added) {
        worker.execute(() -> {
            List<Bitmap> newThumbs = new ArrayList<>();
            for (Uri uri : added) {
                Bitmap bmp = PintuUtils.decodeThumb(PintuActivity.this, uri, 72);
                if (bmp != null) newThumbs.add(bmp);
            }
            runOnUiThread(() -> {
                thumbs.addAll(newThumbs);
                thumbAdapter.notifyItemRangeInserted(thumbs.size() - newThumbs.size(), newThumbs.size());
            });
        });
    }

    /** 移除一张导入的图片（缩略图与 Uri 同步删除） */
    private void removeAt(int position) {
        if (position < 0 || position >= uris.size()) return;
        uris.remove(position);
        Bitmap removed = thumbs.remove(position);
        if (removed != null && !removed.isRecycled()) removed.recycle();
        thumbAdapter.notifyItemRemoved(position);
        updateCountText();
    }

    private void askClear() {
        if (uris.isEmpty()) {
            Toast.makeText(this, R.string.pintu_no_image, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.pintu_clear)
                .setMessage(getString(R.string.pintu_clear_msg, uris.size()))
                .setPositiveButton(R.string.pintu_clear_ok, (d, w) -> {
                    uris.clear();
                    for (Bitmap b : thumbs) {
                        if (!b.isRecycled()) b.recycle();
                    }
                    thumbs.clear();
                    clearResults();
                    thumbAdapter.notifyDataSetChanged();
                    updateCountText();
                })
                .setNegativeButton(R.string.pintu_cancel, null)
                .show();
    }

    private void updateCountText() {
        tvCount.setText(getString(R.string.pintu_count, uris.size(), PintuUtils.MAX_IMAGES));
        updateLayoutHint();
    }

    // ==================== 格式与画布 ====================

    private void bindCountButton(int id, final int count) {
        MaterialButton btn = findViewById(id);
        btn.setOnClickListener(v -> {
            gridCount = count;
            syncCountButtons();
            syncOrientation();
            updateSizeHint();
            updateLayoutHint();
        });
    }

    /** 15 张的横/竖排切换（只有选中 15 时才显示） */
    private void bindOrientation() {
        MaterialButton btn = findViewById(R.id.btn_orient);
        btn.setOnClickListener(v -> {
            landscape = !landscape;
            syncOrientation();
            updateSizeHint();
            updateLayoutHint();
        });
    }

    private void syncOrientation() {
        MaterialButton btn = findViewById(R.id.btn_orient);
        boolean show = gridCount == 15;
        btn.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            btn.setText(landscape ? R.string.pintu_orient_v : R.string.pintu_orient_h);
            btn.setBackgroundTintList(landscape
                    ? ContextCompat.getColorStateList(this, R.color.brand_primary)
                    : null);
            btn.setTextColor(landscape ? Color.WHITE : Color.GRAY);
        }
    }

    private void syncCountButtons() {
        for (int i = 0; i < COUNT_IDS.length; i++) {
            MaterialButton btn = findViewById(COUNT_IDS[i]);
            boolean active = COUNT_VALUES[i] == gridCount;
            btn.setBackgroundTintList(active
                    ? ContextCompat.getColorStateList(this, R.color.brand_primary)
                    : null);
            btn.setStrokeColor(ContextCompat.getColorStateList(this, R.color.brand_primary));
            btn.setTextColor(active ? Color.WHITE : ContextCompat.getColor(this, R.color.brand_primary));
        }
    }

    private void bindWidthPreset(int id, final int width) {
        MaterialButton btn = findViewById(id);
        btn.setOnClickListener(v -> {
            outWidth = width;
            etWidth.setText(String.valueOf(width));
            updateSizeHint();
        });
    }

    /** 读取输入框里的画布宽度（默认 1080，范围 200~5000） */
    private int readWidth() {
        String raw = etWidth.getText() == null ? "" : etWidth.getText().toString().trim();
        int w = TextUtils.isEmpty(raw) ? 1080 : Integer.parseInt(raw);
        if (w < 200) w = 200;
        if (w > 5000) w = 5000;
        outWidth = w;
        return w;
    }

    private void updateSizeHint() {
        int rows = PintuUtils.rowsFor(gridCount, landscape);
        int cell = readWidth() / PintuUtils.colsFor(gridCount, landscape);
        tvSizeHint.setText(getString(R.string.pintu_size_hint, outWidth, cell * rows, rows));
    }

    private void updateLayoutHint() {
        int cols = PintuUtils.colsFor(gridCount, landscape);
        int rows = PintuUtils.rowsFor(gridCount, landscape);
        int groups = uris.isEmpty() ? 0 : (uris.size() + gridCount - 1) / gridCount;
        tvLayoutHint.setText(getString(R.string.pintu_layout_hint, cols, rows, gridCount, groups));
    }

    // ==================== 生成拼图 ====================

    private void generate() {
        if (uris.isEmpty()) {
            Toast.makeText(this, R.string.pintu_no_image, Toast.LENGTH_SHORT).show();
            return;
        }
        int width = readWidth();
        int cols = PintuUtils.colsFor(gridCount, landscape);
        int rows = PintuUtils.rowsFor(gridCount, landscape);
        int groups = (uris.size() + gridCount - 1) / gridCount;
        Toast.makeText(this, getString(R.string.pintu_generating, 1, groups), Toast.LENGTH_SHORT).show();

        final int token = ++generation;
        worker.execute(() -> {
            List<Bitmap> built = new ArrayList<>();
            int[] stat = new int[2];   // [0]=画上去的格数 [1]=解码失败的张数
            for (int g = 0; g < groups; g++) {
                Bitmap bmp = PintuUtils.buildGrid(PintuActivity.this, uris, g * gridCount, gridCount,
                        cols, rows, width, stat);
                if (bmp != null) built.add(bmp);
            }
            runOnUiThread(() -> {
                // 期间又开了新一轮（或清空了图片）→ 丢弃这轮结果
                if (token != generation) return;
                clearResults();
                results.addAll(built);
                renderResults();
                if (built.isEmpty()) {
                    Toast.makeText(this, R.string.pintu_generate_fail, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (stat[1] > 0) {
                    // 有图读不出来：直接说清楚，别让用户对着空白/缺格猜
                    Toast.makeText(this, getString(R.string.pintu_some_failed, stat[1]),
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this,
                            getString(R.string.pintu_generated, built.size()), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    /** 清掉上一次的预览结果（同时作废在途的旧任务） */
    private void clearResults() {
        generation++;
        for (Bitmap b : results) {
            if (!b.isRecycled()) b.recycle();
        }
        results.clear();
        layoutResults.removeAllViews();
        tvResultEmpty.setVisibility(View.VISIBLE);
    }

    /** 渲染结果列表：点击图片看大图，右侧按钮保存这一张 */
    private void renderResults() {
        tvResultEmpty.setVisibility(results.isEmpty() ? View.VISIBLE : View.GONE);
        for (int i = 0; i < results.size(); i++) {
            final int index = i;
            Bitmap bmp = results.get(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, 0, 0, 12);

            ImageView iv = new ImageView(this);
            iv.setLayoutParams(new LinearLayout.LayoutParams(0, dp(150), 1f));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_pintu_checker));
            iv.setImageBitmap(bmp);
            iv.setOnClickListener(v -> openPreview(index));

            MaterialButton btnSave = new MaterialButton(this);
            btnSave.setLayoutParams(new LinearLayout.LayoutParams(0, dp(44), 0.4f));
            btnSave.setMinHeight(dp(44));
            btnSave.setCornerRadius(dp(10));
            btnSave.setStrokeColor(ContextCompat.getColorStateList(this, R.color.brand_primary));
            btnSave.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
            btnSave.setText(R.string.pintu_save_this);
            btnSave.setOnClickListener(v -> saveBitmap(bmp, saveName(index)));

            row.addView(iv);
            row.addView(btnSave);
            layoutResults.addView(row);
        }
    }

    /** 打开一张导入的原图大图预览 */
    private void openImportPreview(int position) {
        if (position < 0 || position >= uris.size()) return;
        Bitmap bmp = PintuUtils.decodeThumb(this, uris.get(position), 1600);
        if (bmp == null) {
            Toast.makeText(this, R.string.pintu_decode_fail, Toast.LENGTH_SHORT).show();
            return;
        }
        PintuPreviewActivity.sharedSingle = bmp;
        PintuPreviewActivity.sharedSingleTitle = getString(R.string.pintu_preview_single,
                position + 1, uris.size());
        startActivity(new Intent(this, PintuPreviewActivity.class));
    }

    private void openPreview(int index) {
        if (results.isEmpty()) return;
        PintuPreviewActivity.sharedResults = results;
        PintuPreviewActivity.sharedIndex = index;
        startActivity(new Intent(this, PintuPreviewActivity.class));
    }

    // ==================== 保存到相册 ====================

    private String saveName(int index) {
        return String.format(Locale.CHINA, "拼图%d_%d.png", index + 1, System.currentTimeMillis());
    }

    private void saveAll() {
        if (results.isEmpty()) {
            Toast.makeText(this, R.string.pintu_no_result, Toast.LENGTH_SHORT).show();
            return;
        }
        for (int i = 0; i < results.size(); i++) {
            saveBitmap(results.get(i), saveName(i));
        }
    }

    private void saveBitmap(Bitmap bmp, String name) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveQ(bmp, name);
            return;
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingBmp = bmp;
            pendingName = name;
            storagePermLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }
        doSavePreQ(bmp, name);
    }

    private void saveQ(Bitmap bmp, String name) {
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/XiaQiaoQiao");
        v.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
        if (uri == null) {
            Toast.makeText(this, R.string.pintu_save_fail, Toast.LENGTH_SHORT).show();
            return;
        }
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            if (os == null || !bmp.compress(Bitmap.CompressFormat.PNG, 100, os)) {
                Toast.makeText(this, R.string.pintu_save_fail, Toast.LENGTH_SHORT).show();
                return;
            }
            v.clear();
            v.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(uri, v, null, null);
            Toast.makeText(this, R.string.pintu_saved, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.pintu_save_fail, Toast.LENGTH_SHORT).show();
        }
    }

    private void doSavePreQ(Bitmap bmp, String name) {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "XiaQiaoQiao");
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(this, R.string.pintu_save_fail, Toast.LENGTH_SHORT).show();
            return;
        }
        File file = new File(dir, name);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)) {
                Toast.makeText(this, R.string.pintu_save_fail, Toast.LENGTH_SHORT).show();
                return;
            }
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
            Toast.makeText(this, R.string.pintu_saved, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.pintu_save_fail, Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== 缩略图列表 ====================

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private class ThumbAdapter extends RecyclerView.Adapter<VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_pintu_thumb, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Bitmap bmp = position < thumbs.size() ? thumbs.get(position) : null;
            holder.ivThumb.setImageBitmap(bmp);
            holder.tvIndex.setText(String.valueOf(position + 1));
            holder.btnRemove.setOnClickListener(v -> removeAt(holder.getBindingAdapterPosition()));
            // 点整张缩略图 → 看大图
            holder.itemView.setOnClickListener(v -> openImportPreview(position));
        }

        @Override
        public int getItemCount() {
            return thumbs.size();
        }
    }

    private class VH extends RecyclerView.ViewHolder {
        ImageView ivThumb;
        TextView tvIndex;
        ImageButton btnRemove;

        VH(@NonNull View itemView) {
            super(itemView);
            ivThumb = itemView.findViewById(R.id.iv_thumb);
            tvIndex = itemView.findViewById(R.id.tv_index);
            btnRemove = itemView.findViewById(R.id.btn_remove);
        }
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        clearResults();
        for (Bitmap b : thumbs) {
            if (!b.isRecycled()) b.recycle();
        }
        thumbs.clear();
        super.onDestroy();
    }
}
