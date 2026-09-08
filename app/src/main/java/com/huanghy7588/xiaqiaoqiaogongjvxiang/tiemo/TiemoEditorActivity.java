package com.huanghy7588.xiaqiaoqiaogongjvxiang.tiemo;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 贴膜水印机：编辑主页。
 *
 * 图层（从底到顶）：图片(必选) → 水印(必选) → 底纹(选填,默认收起) → 小水印(选填,默认收起)。
 * 水印/底纹按短边对齐平铺铺满；小水印单张居中(可偏移)。
 * 顶部：清图 / 保存(保存到文件夹，可新建或已有)；底部：生成全部(合成所有图片) / 保存全部(到相册)。
 */
public class TiemoEditorActivity extends AppCompatActivity {

    /** 单个叠加层状态 */
    private static class LayerState {
        Uri uri;
        int blendIndex = 0;     // 0标准 1覆盖 2软光 3滤色
        float opacity = 1.0f;   // 0..1
        boolean tiled = true;   // 平铺？小水印为 false
        float xFrac = 0.5f;     // 小水印中心 X
        float yFrac = 0.5f;     // 小水印中心 Y
        boolean hasImage() { return uri != null; }
    }

    /** 叠加层 UI 引用 */
    private static class LayerHolder {
        View body;
        TextView toggle;
        TextView count;
        Button btnAdd;
        Button btnClear;
        FrameLayout thumbFrame;
        ImageView thumb;
        TextView clearX;
        RadioGroup rgBlend;
        SeekBar sbOpacity;
        TextView tvOpacityVal;
        LinearLayout xyRow;
        SeekBar sbX;
        SeekBar sbY;
    }

    // 数据
    private final List<Uri> baseUris = new ArrayList<>();
    private final LayerState watermark = new LayerState();
    private final LayerState texture = new LayerState();
    private final LayerState small = new LayerState();
    private List<File> generatedFiles = new ArrayList<>();

    // 预览缓存 Bitmap
    private Bitmap previewBaseBmp, previewWmBmp, previewTexBmp, previewSmallBmp, currentPreviewBmp;

    // UI
    private TiemoPreviewView previewView;
    private TextView tvImageCount;
    private GridLayout gridImage;
    private TextView tvImageEmpty;
    private LinearLayout layoutResults;
    private Button btnSaveAll;
    private TextView tvResultHint;

    private final LayerHolder wmHolder = new LayerHolder();
    private final LayerHolder texHolder = new LayerHolder();
    private final LayerHolder smallHolder = new LayerHolder();

    // 图片选择器
    private final ActivityResultLauncher<String> basePicker = registerForActivityResult(
            new ActivityResultContracts.GetMultipleContents(), uris -> {
                if (uris != null && !uris.isEmpty()) {
                    baseUris.addAll(uris);
                    previewBaseBmp = null;
                    clearGenerated();
                    buildBaseThumbs();
                    rebuildPreview();
                }
            });
    private final ActivityResultLauncher<String> wmPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) { watermark.uri = uri; previewWmBmp = null; clearGenerated();
                    updateLayerThumb(wmHolder, watermark); rebuildPreview(); }
            });
    private final ActivityResultLauncher<String> texPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) { texture.uri = uri; previewTexBmp = null; clearGenerated();
                    updateLayerThumb(texHolder, texture); rebuildPreview(); }
            });
    private final ActivityResultLauncher<String> smallPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) { small.uri = uri; previewSmallBmp = null; clearGenerated();
                    updateLayerThumb(smallHolder, small); rebuildPreview(); }
            });

    private final ActivityResultLauncher<String> storagePermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (Boolean.TRUE.equals(granted)) saveAllToAlbum();
                else Toast.makeText(this, R.string.tiemo_saved_album, Toast.LENGTH_SHORT).show();
            });

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tiemo_editor);

        // 统一返回栏
        findViewById(R.id.btn_back_home).setOnClickListener(v -> finish());
        TextView tvTitle = findViewById(R.id.tv_top_title);
        tvTitle.setVisibility(View.VISIBLE);
        tvTitle.setText(R.string.tiemo_title);

        previewView = findViewById(R.id.preview_view);
        tvImageCount = findViewById(R.id.tv_image_count);
        gridImage = findViewById(R.id.grid_image);
        tvImageEmpty = findViewById(R.id.tv_image_empty);
        layoutResults = findViewById(R.id.layout_results);
        btnSaveAll = findViewById(R.id.btn_save_all);
        tvResultHint = findViewById(R.id.tv_result_hint);

        initTopButtons();
        initImageSection();
        setupLayer(watermark, wmHolder, R.string.tiemo_watermark, true, false, false);
        setupLayer(texture, texHolder, R.string.tiemo_texture, false, true, false);
        setupLayer(small, smallHolder, R.string.tiemo_small, false, true, true);
        initGenerateSection();

        rebuildPreview();
    }

    // ==================== 顶部按钮 ====================
    private void initTopButtons() {
        findViewById(R.id.btn_clear_all).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.tiemo_clear)
                    .setMessage(R.string.tiemo_clear_confirm)
                    .setPositiveButton(R.string.confirm, (d, w) -> clearAll())
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
        findViewById(R.id.btn_save_top).setOnClickListener(v ->
                ensureGeneratedThen(this::showSaveToFolderDialog));
    }

    private void clearAll() {
        baseUris.clear();
        watermark.uri = null; texture.uri = null; small.uri = null;
        previewBaseBmp = null; previewWmBmp = null; previewTexBmp = null; previewSmallBmp = null;
        clearGenerated();
        buildBaseThumbs();
        updateLayerThumb(wmHolder, watermark);
        updateLayerThumb(texHolder, texture);
        updateLayerThumb(smallHolder, small);
        rebuildPreview();
        Toast.makeText(this, R.string.tiemo_cleared_toast, Toast.LENGTH_SHORT).show();
    }

    // ==================== 图片（底图） ====================
    private void initImageSection() {
        findViewById(R.id.btn_image_add).setOnClickListener(v -> basePicker.launch("image/*"));
        findViewById(R.id.btn_image_clear).setOnClickListener(v -> {
            baseUris.clear();
            previewBaseBmp = null;
            clearGenerated();
            buildBaseThumbs();
            rebuildPreview();
        });
        buildBaseThumbs();
    }

    /** 重建底图缩略图网格 */
    private void buildBaseThumbs() {
        gridImage.removeAllViews();
        boolean has = !baseUris.isEmpty();
        gridImage.setVisibility(has ? View.VISIBLE : View.GONE);
        tvImageEmpty.setVisibility(has ? View.GONE : View.VISIBLE);
        findViewById(R.id.btn_image_clear).setVisibility(has ? View.VISIBLE : View.GONE);
        tvImageCount.setText(getString(R.string.tiemo_image) + " (" + baseUris.size() + ")");

        for (int i = 0; i < baseUris.size(); i++) {
            final int idx = i;
            Uri uri = baseUris.get(i);
            FrameLayout fl = new FrameLayout(this);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(0, 0, 0, 10);
            fl.setLayoutParams(lp);

            ImageView iv = new ImageView(this);
            iv.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundResource(android.R.color.darker_gray);
            Bitmap bmp = TiemoUtils.decodeUri(this, uri, 200);
            iv.setImageBitmap(bmp);

            TextView x = new TextView(this);
            x.setText("✕");
            x.setTextColor(0xFFFFFFFF);
            x.setTextSize(14);
            x.setGravity(android.view.Gravity.CENTER);
            x.setBackgroundResource(R.drawable.bg_remove_circle);
            FrameLayout.LayoutParams xp = new FrameLayout.LayoutParams(dp(22), dp(22));
            xp.setMargins(dp(2), dp(2), 0, 0);
            x.setLayoutParams(xp);
            x.setOnClickListener(vv -> {
                baseUris.remove(idx);
                previewBaseBmp = null;
                clearGenerated();
                buildBaseThumbs();
                rebuildPreview();
            });

            fl.addView(iv);
            fl.addView(x);
            gridImage.addView(fl);
        }
    }

    // ==================== 叠加层卡片 ====================
    private void setupLayer(LayerState state, LayerHolder h, @IdRes int titleRes,
                            boolean required, boolean collapsible, boolean showXY) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_tiemo_layer,
                findViewById(R.id.container_layers), false);
        ((ViewGroup) findViewById(R.id.container_layers)).addView(card);

        h.body = card.findViewById(R.id.layer_body);
        h.toggle = card.findViewById(R.id.tv_layer_toggle);
        h.count = card.findViewById(R.id.tv_layer_count);
        h.btnAdd = card.findViewById(R.id.btn_layer_add);
        h.btnClear = card.findViewById(R.id.btn_layer_clear);
        h.thumbFrame = card.findViewById(R.id.layer_thumb_frame);
        h.thumb = card.findViewById(R.id.iv_layer_thumb);
        h.clearX = card.findViewById(R.id.tv_layer_clear_x);
        h.rgBlend = card.findViewById(R.id.rg_layer_blend);
        h.sbOpacity = card.findViewById(R.id.sb_layer_opacity);
        h.tvOpacityVal = card.findViewById(R.id.tv_layer_opacity_val);
        h.xyRow = card.findViewById(R.id.layer_xy);
        h.sbX = card.findViewById(R.id.sb_layer_x);
        h.sbY = card.findViewById(R.id.sb_layer_y);

        ((TextView) card.findViewById(R.id.tv_layer_title)).setText(titleRes);
        TextView tagView = card.findViewById(R.id.tv_layer_tag);
        tagView.setText(required ? R.string.tiemo_required : R.string.tiemo_optional);
        tagView.setTextColor(required ? ContextCompat.getColor(this, R.color.brand_primary)
                : ContextCompat.getColor(this, R.color.text_secondary));

        // 折叠逻辑
        if (collapsible) {
            h.body.setVisibility(View.GONE);
            h.toggle.setText("▸");
            card.findViewById(R.id.layer_header).setOnClickListener(v -> {
                boolean expanded = h.body.getVisibility() == View.VISIBLE;
                h.body.setVisibility(expanded ? View.GONE : View.VISIBLE);
                h.toggle.setText(expanded ? "▸" : "▾");
            });
        } else {
            h.body.setVisibility(View.VISIBLE);
            h.toggle.setText("▾");
            card.findViewById(R.id.layer_header).setClickable(false);
        }

        // 导入按钮
        if (state == watermark) h.btnAdd.setOnClickListener(v -> wmPicker.launch("image/*"));
        else if (state == texture) h.btnAdd.setOnClickListener(v -> texPicker.launch("image/*"));
        else h.btnAdd.setOnClickListener(v -> smallPicker.launch("image/*"));

        h.btnClear.setOnClickListener(v -> {
            if (state == watermark) { watermark.uri = null; previewWmBmp = null; }
            else if (state == texture) { texture.uri = null; previewTexBmp = null; }
            else { small.uri = null; previewSmallBmp = null; }
            clearGenerated();
            updateLayerThumb(h, state);
            rebuildPreview();
        });
        h.clearX.setOnClickListener(v -> h.btnClear.performClick());

        // 混合模式
        h.rgBlend.check(h.rgBlend.getChildAt(state.blendIndex).getId());
        h.rgBlend.setOnCheckedChangeListener((g, checkedId) -> {
            int idx = 0;
            for (int i = 0; i < h.rgBlend.getChildCount(); i++) {
                if (h.rgBlend.getChildAt(i).getId() == checkedId) { idx = i; break; }
            }
            state.blendIndex = idx;
            rebuildPreview();
        });

        // 不透明度
        h.sbOpacity.setProgress((int) (state.opacity * 100));
        h.tvOpacityVal.setText((int) (state.opacity * 100) + "%");
        h.sbOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                state.opacity = p / 100f;
                h.tvOpacityVal.setText(p + "%");
                rebuildPreview();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        // X/Y（仅小水印）
        if (showXY) {
            h.xyRow.setVisibility(View.VISIBLE);
            h.sbX.setProgress((int) (state.xFrac * 100));
            h.sbY.setProgress((int) (state.yFrac * 100));
            h.sbX.setOnSeekBarChangeListener(simpleProgress(p -> { state.xFrac = p / 100f; rebuildPreview(); }));
            h.sbY.setOnSeekBarChangeListener(simpleProgress(p -> { state.yFrac = p / 100f; rebuildPreview(); }));
        }

        updateLayerThumb(h, state);
    }

    private SeekBar.OnSeekBarChangeListener simpleProgress(ProgressAction action) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) { action.run(p); }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        };
    }
    private interface ProgressAction { void run(int p); }

    /** 更新某层缩略图与文案 */
    private void updateLayerThumb(LayerHolder h, LayerState state) {
        if (state.hasImage()) {
            Bitmap bmp = TiemoUtils.decodeUri(this, state.uri, 200);
            h.thumb.setImageBitmap(bmp);
            h.thumbFrame.setVisibility(View.VISIBLE);
            h.btnClear.setVisibility(View.VISIBLE);
            h.count.setText(getString(R.string.tiemo_images_count, 1));
        } else {
            h.thumbFrame.setVisibility(View.GONE);
            h.btnClear.setVisibility(View.GONE);
            h.count.setText(R.string.tiemo_need_base);
        }
    }

    // ==================== 预览 ====================
    private void rebuildPreview() {
        if (baseUris.isEmpty()) {
            if (currentPreviewBmp != null && !currentPreviewBmp.isRecycled()) currentPreviewBmp.recycle();
            currentPreviewBmp = null;
            previewView.setBitmap(null);
            return;
        }
        if (previewBaseBmp == null || previewBaseBmp.isRecycled()) {
            previewBaseBmp = TiemoUtils.decodeUri(this, baseUris.get(0), 720);
        }
        if (previewBaseBmp == null) { previewView.setBitmap(null); return; }

        TiemoCompositor.LayerInput wm = null, tex = null, sm = null;
        if (watermark.hasImage()) {
            if (previewWmBmp == null || previewWmBmp.isRecycled())
                previewWmBmp = TiemoUtils.decodeUri(this, watermark.uri, 720);
            if (previewWmBmp != null) { wm = toInput(watermark, true); }
        }
        if (texture.hasImage()) {
            if (previewTexBmp == null || previewTexBmp.isRecycled())
                previewTexBmp = TiemoUtils.decodeUri(this, texture.uri, 720);
            if (previewTexBmp != null) { tex = toInput(texture, true); }
        }
        if (small.hasImage()) {
            if (previewSmallBmp == null || previewSmallBmp.isRecycled())
                previewSmallBmp = TiemoUtils.decodeUri(this, small.uri, 720);
            if (previewSmallBmp != null) { sm = toInput(small, false); }
        }

        Bitmap result = TiemoCompositor.composite(previewBaseBmp, wm, tex, sm);
        if (result != null) {
            if (currentPreviewBmp != null && !currentPreviewBmp.isRecycled()) currentPreviewBmp.recycle();
            currentPreviewBmp = result;
            previewView.setBitmap(result);
        }
    }

    private TiemoCompositor.LayerInput toInput(LayerState s, boolean tiled) {
        TiemoCompositor.LayerInput in = new TiemoCompositor.LayerInput();
        if (s == watermark) in.overlay = previewWmBmp;
        else if (s == texture) in.overlay = previewTexBmp;
        else in.overlay = previewSmallBmp;
        in.blendIndex = s.blendIndex;
        in.opacity = s.opacity;
        in.tiled = tiled;
        in.xFrac = s.xFrac;
        in.yFrac = s.yFrac;
        return in;
    }

    // ==================== 生成全部 / 保存全部 ====================
    private void initGenerateSection() {
        findViewById(R.id.btn_generate_all).setOnClickListener(v ->
                generateAll(() -> {}, () -> {}));
        btnSaveAll.setOnClickListener(v -> requestAlbumSave());
    }

    /** 生成所有底图的水印合成结果（后台线程 + 进度框） */
    private void generateAll(Runnable onDone, Runnable onFail) {
        if (baseUris.isEmpty()) {
            Toast.makeText(this, R.string.tiemo_need_base, Toast.LENGTH_SHORT).show();
            onFail.run(); return;
        }
        if (!watermark.hasImage()) {
            Toast.makeText(this, R.string.tiemo_need_watermark, Toast.LENGTH_SHORT).show();
            onFail.run(); return;
        }

        ProgressDialog pd = new ProgressDialog(this);
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setCancelable(false);
        pd.setMax(baseUris.size());
        pd.setMessage(getString(R.string.tiemo_generating, 0, baseUris.size()));
        pd.show();

        // 先解码叠加层（一次）
        final Bitmap wmBmp = TiemoUtils.decodeUri(this, watermark.uri, 2048);
        final Bitmap texBmp = texture.hasImage() ? TiemoUtils.decodeUri(this, texture.uri, 2048) : null;
        final Bitmap smallBmp = small.hasImage() ? TiemoUtils.decodeUri(this, small.uri, 2048) : null;

        final List<Uri> snapshot = new ArrayList<>(baseUris);
        final File genDir = TiemoUtils.getCacheGenDir(this);
        final List<File> out = new ArrayList<>();

        new Thread(() -> {
            int total = snapshot.size();
            final int[] ok = {0};
            try {
                for (int i = 0; i < total; i++) {
                    Uri uri = snapshot.get(i);
                    Bitmap base = TiemoUtils.decodeUri(TiemoEditorActivity.this, uri, 4096);
                    if (base == null) { report(pd, i + 1, total); continue; }
                    TiemoCompositor.LayerInput wm = null, tex = null, sm = null;
                    if (wmBmp != null) { wm = new TiemoCompositor.LayerInput(); wm.overlay = wmBmp;
                        wm.blendIndex = watermark.blendIndex; wm.opacity = watermark.opacity; wm.tiled = true; }
                    if (texBmp != null) { tex = new TiemoCompositor.LayerInput(); tex.overlay = texBmp;
                        tex.blendIndex = texture.blendIndex; tex.opacity = texture.opacity; tex.tiled = true; }
                    if (smallBmp != null) { sm = new TiemoCompositor.LayerInput(); sm.overlay = smallBmp;
                        sm.blendIndex = small.blendIndex; sm.opacity = small.opacity; sm.tiled = false;
                        sm.xFrac = small.xFrac; sm.yFrac = small.yFrac; }
                    Bitmap result = TiemoCompositor.composite(base, wm, tex, sm);
                    base.recycle();
                    if (result != null) {
                        File f = new File(genDir, "tiemo_" + System.currentTimeMillis() + "_" + i + ".png");
                        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(f)) {
                            result.compress(Bitmap.CompressFormat.PNG, 100, fos);
                            out.add(f);
                            ok[0]++;
                        } catch (java.io.IOException e) {
                            e.printStackTrace();
                        }
                        result.recycle();
                    }
                    report(pd, i + 1, total);
                }
            } finally {
                if (wmBmp != null) wmBmp.recycle();
                if (texBmp != null) texBmp.recycle();
                if (smallBmp != null) smallBmp.recycle();
                uiHandler.post(() -> {
                    if (pd.isShowing()) pd.dismiss();
                    generatedFiles = out;
                    showGeneratedResults();
                    if (ok[0] > 0) {
                        Toast.makeText(this, getString(R.string.tiemo_generated, ok[0]), Toast.LENGTH_SHORT).show();
                        onDone.run();
                    } else {
                        Toast.makeText(this, R.string.tiemo_export_fail, Toast.LENGTH_SHORT).show();
                        onFail.run();
                    }
                });
            }
        }).start();
    }

    private void report(ProgressDialog pd, int cur, int total) {
        uiHandler.post(() -> {
            if (isFinishing() || isDestroyed() || !pd.isShowing()) return;
            pd.setProgress(cur);
            pd.setMessage(getString(R.string.tiemo_generating, cur, total));
        });
    }

    /** 把生成结果显示为缩略图条，并显示"保存全部" */
    private void showGeneratedResults() {
        layoutResults.removeAllViews();
        if (generatedFiles.isEmpty()) {
            btnSaveAll.setVisibility(View.GONE);
            tvResultHint.setVisibility(View.VISIBLE);
            tvResultHint.setText(R.string.tiemo_no_results);
            return;
        }
        for (File f : generatedFiles) {
            Bitmap bmp = TiemoUtils.decodeThumb(f, 200);
            ImageView iv = new ImageView(this);
            iv.setLayoutParams(new ViewGroup.LayoutParams(dp(88), dp(88)));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundResource(android.R.color.darker_gray);
            iv.setImageBitmap(bmp);
            layoutResults.addView(iv);
        }
        btnSaveAll.setVisibility(View.VISIBLE);
        tvResultHint.setVisibility(View.GONE);
    }

    private void clearGenerated() {
        for (File f : generatedFiles) f.delete();
        generatedFiles = new ArrayList<>();
        layoutResults.removeAllViews();
        btnSaveAll.setVisibility(View.GONE);
        tvResultHint.setVisibility(View.VISIBLE);
        tvResultHint.setText(R.string.tiemo_no_results);
    }

    /** 保存全部到相册（先检查权限） */
    private void requestAlbumSave() {
        if (generatedFiles.isEmpty()) {
            Toast.makeText(this, R.string.tiemo_no_results, Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != getPackageManager().PERMISSION_GRANTED) {
            storagePermLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }
        saveAllToAlbum();
    }

    private void saveAllToAlbum() {
        int ok = 0;
        for (File f : generatedFiles) {
            if (TiemoUtils.saveFileToGallery(this, f)) ok++;
        }
        Toast.makeText(this, getString(R.string.tiemo_saved_album, ok), Toast.LENGTH_LONG).show();
    }

    // ==================== 保存到文件夹 ====================
    private void ensureGeneratedThen(Runnable after) {
        if (!generatedFiles.isEmpty()) { after.run(); return; }
        generateAll(after, () -> {});
    }

    private void showSaveToFolderDialog() {
        if (generatedFiles.isEmpty()) {
            Toast.makeText(this, R.string.tiemo_no_results, Toast.LENGTH_SHORT).show();
            return;
        }
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_tiemo_folder, null);
        EditText et = dialogView.findViewById(R.id.et_folder_name);
        TextView tvHint = dialogView.findViewById(R.id.tv_dialog_hint);
        TextView tvExistingLabel = dialogView.findViewById(R.id.tv_existing_label);
        LinearLayout layoutExisting = dialogView.findViewById(R.id.layout_existing);

        tvHint.setText(R.string.tiemo_save_title);
        tvExistingLabel.setText(R.string.tiemo_save_pick_hint);

        // 列出已有文件夹，点击填入名称
        List<TiemoUtils.FolderInfo> folders = TiemoUtils.listFolders(this);
        for (TiemoUtils.FolderInfo info : folders) {
            Button b = new Button(this, null, android.R.attr.buttonStyle);
            b.setText(info.name);
            b.setAllCaps(false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> et.setText(info.name));
            layoutExisting.addView(b);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.tiemo_save_title)
                .setView(dialogView)
                .setPositiveButton(R.string.confirm, (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, R.string.tiemo_folder_name_hint, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveGeneratedToFolder(name);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void saveGeneratedToFolder(String folderName) {
        File dir = TiemoUtils.getFolderDir(this, folderName);
        dir.mkdirs();
        boolean existed = dir.listFiles() != null && dir.listFiles().length > 0;
        int ok = 0;
        for (int i = 0; i < generatedFiles.size(); i++) {
            File dest = new File(dir, "tiemo_" + System.currentTimeMillis() + "_" + i + ".png");
            if (TiemoUtils.copyFile(generatedFiles.get(i), dest)) ok++;
        }
        String msg = existed ? getString(R.string.tiemo_folder_exists, folderName)
                : getString(R.string.tiemo_saved_folder, ok, folderName);
        Toast.makeText(this, getString(R.string.tiemo_saved_to_folder, ok, folderName), Toast.LENGTH_LONG).show();
    }

    // ==================== 工具 ====================
    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (previewBaseBmp != null && !previewBaseBmp.isRecycled()) previewBaseBmp.recycle();
        if (previewWmBmp != null && !previewWmBmp.isRecycled()) previewWmBmp.recycle();
        if (previewTexBmp != null && !previewTexBmp.isRecycled()) previewTexBmp.recycle();
        if (previewSmallBmp != null && !previewSmallBmp.isRecycled()) previewSmallBmp.recycle();
        if (currentPreviewBmp != null && !currentPreviewBmp.isRecycled()) currentPreviewBmp.recycle();
    }
}
