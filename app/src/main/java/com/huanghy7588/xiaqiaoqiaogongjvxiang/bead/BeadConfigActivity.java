package com.huanghy7588.xiaqiaoqiaogongjvxiang.bead;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;
import com.huanghy7588.xiaqiaoqiaogongjvxiang.pintu.PintuUtils;

/**
 * 拼豆图纸生成 · 第三步：配置（品牌 / 颜色数 / 尺寸 / 限色 / 抖动 / 去背景）。
 */
public class BeadConfigActivity extends AppCompatActivity {

    private BeadConfig config;
    private TextView tvBrand, tvColor, tvWidth, tvHeight, tvMax;
    private SeekBar seekWidth, seekMax;
    private Switch swDither, swBg;
    private int srcW, srcH;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bead_config);

        findViewById(R.id.btn_back_home).setOnClickListener(v -> finish());
        TextView tvTitle = findViewById(R.id.tv_top_title);
        tvTitle.setText(R.string.bead_config_title);
        tvTitle.setVisibility(View.VISIBLE);

        config = BeadConfig.load(this);

        ImageView ivThumb = findViewById(R.id.iv_thumb);
        if (BeadData.source != null) {
            ivThumb.setImageBitmap(BeadData.source);
            srcW = BeadData.source.getWidth();
            srcH = BeadData.source.getHeight();
        } else if (BeadData.sourceUri != null) {
            Bitmap t = PintuUtils.decodeThumb(this, BeadData.sourceUri, 400);
            if (t != null) {
                ivThumb.setImageBitmap(t);
                srcW = t.getWidth();
                srcH = t.getHeight();
            }
        }

        tvBrand = findViewById(R.id.tv_brand);
        tvColor = findViewById(R.id.tv_color);
        tvWidth = findViewById(R.id.tv_width);
        tvHeight = findViewById(R.id.tv_height);
        tvMax = findViewById(R.id.tv_max);
        refreshBrandText();

        seekWidth = findViewById(R.id.seek_width);
        seekWidth.setMax(150 - 20);
        seekWidth.setProgress(config.gridWidth - 20);
        seekWidth.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgress(int p) {
                int w = 20 + p;
                config.gridWidth = w;
                tvWidth.setText(w + " 颗");
                updateHeightText();
            }
        });
        tvWidth.setText(config.gridWidth + " 颗");
        updateHeightText();

        seekMax = findViewById(R.id.seek_max);
        seekMax.setMax(64 - 8);
        seekMax.setProgress(config.maxColors - 8);
        seekMax.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgress(int p) {
                config.maxColors = 8 + p;
                tvMax.setText(config.maxColors + " 种");
            }
        });
        tvMax.setText(config.maxColors + " 种");

        swDither = findViewById(R.id.sw_dither);
        swBg = findViewById(R.id.sw_bg);
        swDither.setChecked(config.dither);
        swBg.setChecked(config.removeBackground);

        findViewById(R.id.row_brand).setOnClickListener(v -> showBrandDialog());
        findViewById(R.id.row_color).setOnClickListener(v -> showCountDialog());

        findViewById(R.id.btn_generate).setOnClickListener(v -> generate());
    }

    private void refreshBrandText() {
        tvBrand.setText(config.brandName);
        tvColor.setText(config.colorCount + " 色");
    }

    private void updateHeightText() {
        if (srcW > 0 && srcH > 0) {
            int h = Math.max(1, Math.round(config.gridWidth * (float) srcH / srcW));
            tvHeight.setText(h + " 颗（自动）");
        } else {
            tvHeight.setText("自动");
        }
    }

    private void applyBrand(BeadBrands.Brand brand, BeadBrands.CountOption opt) {
        config.applyBrand(brand, opt);
        config.save(this);
        refreshBrandText();
    }

    private void showBrandDialog() {
        if (brandDialog != null && brandDialog.isShowing()) {
            brandDialog.dismiss();
        }
        final Dialog d = new Dialog(this);
        brandDialog = d;
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(R.string.bead_pick_brand);
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        for (BeadBrands.Brand b : BeadBrands.all()) {
            final boolean isCur = b.id.equals(config.brandId);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(14), 0, dp(14));
            if (isCur) {
                row.setBackgroundColor(0x114CAF50);
            }

            TextView dot = new TextView(this);
            dot.setText(isCur ? "●" : "○");
            dot.setTextSize(18);
            dot.setTextColor(0xFF4CAF50);
            row.addView(dot);

            TextView name = new TextView(this);
            name.setText(b.name);
            name.setTextSize(16);
            name.setPadding(dp(12), 0, 0, 0);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            name.setLayoutParams(np);
            row.addView(name);

            TextView cnt = new TextView(this);
            cnt.setText(b.getDefault().count + "色");
            cnt.setTextSize(14);
            cnt.setTextColor(0xFF757575);
            row.addView(cnt);

            final BeadBrands.Brand fb = b;
            row.setOnClickListener(v -> {
                // 选品牌即应用该品牌的默认色数（颜色数量可在下一页单独再选）
                applyBrand(fb, fb.getDefault());
                d.dismiss();
            });
            root.addView(row);
        }

        Button ok = new Button(this);
        ok.setText(R.string.confirm);
        ok.setBackgroundColor(0xFF4CAF50);
        ok.setTextColor(0xFFFFFFFF);
        ok.setPadding(0, dp(12), 0, dp(12));
        ok.setOnClickListener(v -> d.dismiss());
        root.addView(ok);

        sv.addView(root);
        d.setContentView(sv);
        d.show();
    }

    /** 选择颜色数量（当前品牌支持的色数，例如 MARD 有 221 / 291）。 */
    private void showCountDialog() {
        if (brandDialog != null && brandDialog.isShowing()) {
            brandDialog.dismiss();
        }
        final Dialog d = new Dialog(this);
        brandDialog = d;
        BeadBrands.Brand brand = BeadBrands.find(config.brandId);

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(R.string.bead_pick_count);
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText(brand.name);
        sub.setTextSize(13);
        sub.setTextColor(0xFF757575);
        sub.setPadding(0, 0, 0, dp(8));
        root.addView(sub);

        for (BeadBrands.CountOption o : brand.options) {
            final boolean isCur = (o.count == config.colorCount && brand.id.equals(config.brandId));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(14), 0, dp(14));
            if (isCur) {
                row.setBackgroundColor(0x114CAF50);
            }
            TextView dot = new TextView(this);
            dot.setText(isCur ? "●" : "○");
            dot.setTextSize(18);
            dot.setTextColor(0xFF4CAF50);
            row.addView(dot);

            TextView name = new TextView(this);
            name.setText(o.count + " 色" + (o.recommended ? "（推荐）" : ""));
            name.setTextSize(16);
            name.setPadding(dp(12), 0, 0, 0);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            name.setLayoutParams(np);
            row.addView(name);

            final BeadBrands.CountOption fo = o;
            row.setOnClickListener(v -> {
                applyBrand(brand, fo);
                d.dismiss();
            });
            root.addView(row);
        }

        Button ok = new Button(this);
        ok.setText(R.string.confirm);
        ok.setBackgroundColor(0xFF4CAF50);
        ok.setTextColor(0xFFFFFFFF);
        ok.setPadding(0, dp(12), 0, dp(12));
        ok.setOnClickListener(v -> d.dismiss());
        root.addView(ok);

        sv.addView(root);
        d.setContentView(sv);
        d.show();
    }

    private Dialog brandDialog;

    private void generate() {
        if (BeadData.source == null && BeadData.sourceUri == null) {
            Toast.makeText(this, R.string.bead_need_image, Toast.LENGTH_SHORT).show();
            return;
        }
        config.gridWidth = 20 + seekWidth.getProgress();
        if (srcW > 0 && srcH > 0) {
            config.gridHeight = Math.max(1, Math.round(config.gridWidth * (float) srcH / srcW));
        } else {
            config.gridHeight = config.gridWidth;
        }
        config.maxColors = 8 + seekMax.getProgress();
        config.dither = swDither.isChecked();
        config.removeBackground = swBg.isChecked();
        config.save(this);
        BeadData.config = config;
        startActivity(new Intent(this, BeadPreviewActivity.class));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private abstract class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            onProgress(progress);
        }

        abstract void onProgress(int p);

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) { }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) { }
    }
}
