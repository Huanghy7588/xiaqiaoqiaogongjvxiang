package com.huanghy7588.xiaqiaoqiaogongjvxiang.qr;

import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;

import java.util.ArrayList;
import java.util.List;

/** 二维码结果页：预览 + 美化（前景/背景色、圆点样式）+ 保存相册。 */
public class QrResultActivity extends AppCompatActivity {

    private static final int SAVE_SIZE = 1024;
    private static final int QUIET = 4;

    // 前景色预设
    private static final int[] FG_COLORS = {
            0xFF000000, // 黑
            0xFF1565C0, // 蓝
            0xFF2E7D32, // 绿
            0xFFC62828, // 红
            0xFF6A1B9A  // 紫
    };
    // 背景色预设（null = 透明）
    private static final Integer[] BG_COLORS = {
            0xFFFFFFFF, // 白
            null,       // 透明
            0xFFFFF9C4, // 浅黄
            0xFFE8F5E9, // 浅绿
            0xFFE3F2FD, // 浅蓝
            0xFFFCE4EC  // 浅粉
    };

    private BitMatrix matrix;
    private int fgColor = 0xFF000000;
    private int bgColor = 0xFFFFFFFF;
    private int dotStyle = QrRenderer.DOT_SQUARE;

    private Bitmap currentBmp;
    private ImageView ivPreview;
    private final List<View> fgSwatches = new ArrayList<>();
    private final List<View> bgSwatches = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_result);

        findViewById(R.id.btn_back_home).setOnClickListener(v -> finish());
        TextView tv = findViewById(R.id.tv_top_title);
        tv.setVisibility(View.VISIBLE);
        tv.setText(R.string.qr_tool_title);

        ivPreview = findViewById(R.id.iv_preview);

        String text = getIntent().getStringExtra("qr_text");
        if (text == null || text.isEmpty()) {
            Toast.makeText(this, R.string.qr_empty_text, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        try {
            matrix = QrUtils.encodeText(text);
        } catch (WriterException e) {
            Toast.makeText(this, R.string.qr_encode_fail, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        buildSwatches();
        setupDotButtons();
        findViewById(R.id.btn_save).setOnClickListener(v -> save());

        renderQr();
    }

    // ==================== 颜色选择 ====================
    private void buildSwatches() {
        LinearLayout llFg = findViewById(R.id.ll_fg);
        LinearLayout llBg = findViewById(R.id.ll_bg);
        llFg.removeAllViews();
        llBg.removeAllViews();
        fgSwatches.clear();
        bgSwatches.clear();

        for (int c : FG_COLORS) {
            final int color = c;
            View v = makeSwatch(color, null);
            v.setOnClickListener(x -> { fgColor = color; refreshSwatches(); renderQr(); });
            fgSwatches.add(v);
            llFg.addView(v);
        }
        for (Integer c : BG_COLORS) {
            final Integer color = c;
            View v = makeSwatch(color, (c == null) ? "透明" : null);
            v.setOnClickListener(x -> {
                bgColor = (color == null) ? 0 : color;
                refreshSwatches();
                renderQr();
            });
            bgSwatches.add(v);
            llBg.addView(v);
        }
        refreshSwatches();
    }

    private View makeSwatch(Integer color, String label) {
        View v;
        if (label != null) {
            TextView t = new TextView(this);
            t.setText(label);
            t.setTextSize(12);
            t.setGravity(android.view.Gravity.CENTER);
            t.setTextColor(0xFF757575);
            v = t;
        } else {
            v = new View(this);
        }
        int size = dp(44);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(0, 0, dp(12), 0);
        v.setLayoutParams(lp);
        return v;
    }

    private void applySwatchBg(View v, Integer color, String label, boolean selected) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        if (label != null) {
            d.setColor(0xFFEEEEEE); // 透明占位（灰）
        } else if (color != null) {
            d.setColor(color);
        } else {
            d.setColor(0xFFEEEEEE);
        }
        if (selected) d.setStroke(dp(4), 0xFF4CAF50);
        v.setBackground(d);
    }

    private void refreshSwatches() {
        for (int i = 0; i < fgSwatches.size(); i++) {
            int c = FG_COLORS[i];
            applySwatchBg(fgSwatches.get(i), c, null, c == fgColor);
        }
        for (int i = 0; i < bgSwatches.size(); i++) {
            Integer c = BG_COLORS[i];
            String label = (c == null) ? "透明" : null;
            boolean sel = (c == null) ? (bgColor == 0) : (c == bgColor);
            applySwatchBg(bgSwatches.get(i), c, label, sel);
        }
    }

    // ==================== 码点样式 ====================
    private void setupDotButtons() {
        Button bS = findViewById(R.id.btn_dot_square);
        Button bR = findViewById(R.id.btn_dot_round);
        bS.setOnClickListener(v -> { dotStyle = QrRenderer.DOT_SQUARE; updateDotButtons(); renderQr(); });
        bR.setOnClickListener(v -> { dotStyle = QrRenderer.DOT_ROUND; updateDotButtons(); renderQr(); });
        updateDotButtons();
    }

    private void updateDotButtons() {
        Button bS = findViewById(R.id.btn_dot_square);
        Button bR = findViewById(R.id.btn_dot_round);
        bS.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                dotStyle == QrRenderer.DOT_SQUARE ? 0xFF4CAF50 : 0xFFBDBDBD));
        bR.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                dotStyle == QrRenderer.DOT_ROUND ? 0xFF4CAF50 : 0xFFBDBDBD));
        bS.setTextColor(0xFFFFFFFF);
        bR.setTextColor(0xFFFFFFFF);
    }

    // ==================== 渲染与保存 ====================
    private void renderQr() {
        if (matrix == null) return;
        if (currentBmp != null && !currentBmp.isRecycled()) currentBmp.recycle();
        currentBmp = QrRenderer.render(matrix, SAVE_SIZE, QUIET, fgColor, bgColor, dotStyle);
        ivPreview.setImageBitmap(currentBmp);
    }

    private void save() {
        if (currentBmp == null) {
            Toast.makeText(this, R.string.qr_save_fail, Toast.LENGTH_SHORT).show();
            return;
        }
        boolean ok = QrUtils.saveBitmapToGallery(this, currentBmp);
        Toast.makeText(this, ok ? R.string.qr_saved : R.string.qr_save_fail, Toast.LENGTH_LONG).show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentBmp != null && !currentBmp.isRecycled()) currentBmp.recycle();
    }
}
