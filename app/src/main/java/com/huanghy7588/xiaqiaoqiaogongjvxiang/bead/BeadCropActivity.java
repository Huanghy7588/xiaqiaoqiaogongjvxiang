package com.huanghy7588.xiaqiaoqiaogongjvxiang.bead;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;
import com.huanghy7588.xiaqiaoqiaogongjvxiang.pintu.PintuUtils;

/**
 * 拼豆图纸生成 · 第二步：裁剪（可选）。默认 1:1，支持自由 / 1:1 / 4:3。
 */
public class BeadCropActivity extends AppCompatActivity {

    public static final String EXTRA_URI = "bead_src_uri";

    private BeadCropView cropView;
    private Uri uri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bead_crop);

        findViewById(R.id.btn_back_home).setOnClickListener(v -> finish());
        TextView tvTitle = findViewById(R.id.tv_top_title);
        tvTitle.setText(R.string.bead_crop_title);
        tvTitle.setVisibility(View.VISIBLE);

        uri = getIntent().getParcelableExtra(EXTRA_URI);
        if (uri == null) {
            finish();
            return;
        }
        Bitmap bmp = PintuUtils.decode(this, uri, 2000, 2000);
        if (bmp == null) {
            Toast.makeText(this, R.string.bead_pick_fail, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        cropView = findViewById(R.id.crop_view);
        cropView.setSourceBitmap(bmp);
        cropView.setFrameAspect(1f);
        syncRatioButtons(R.id.btn_ratio_1);

        bindRatio(R.id.btn_ratio_free, 0f);
        bindRatio(R.id.btn_ratio_1, 1f);
        bindRatio(R.id.btn_ratio_43, 4f / 3f);

        findViewById(R.id.btn_skip_crop).setOnClickListener(v -> skipCrop());
        findViewById(R.id.btn_crop_next).setOnClickListener(v -> next());
    }

    private void bindRatio(int id, float aspect) {
        findViewById(id).setOnClickListener(v -> {
            cropView.setFrameAspect(aspect);
            syncRatioButtons(id);
        });
    }

    private void syncRatioButtons(int activeId) {
        int[] ids = {R.id.btn_ratio_free, R.id.btn_ratio_1, R.id.btn_ratio_43};
        for (int i = 0; i < ids.length; i++) {
            MaterialButton b = findViewById(ids[i]);
            boolean active = ids[i] == activeId;
            b.setBackgroundTintList(active
                    ? android.content.res.ColorStateList.valueOf(0xFF4CAF50)
                    : null);
            b.setTextColor(active ? 0xFFFFFFFF : 0xFF4CAF50);
        }
    }

    private void skipCrop() {
        Bitmap bmp = PintuUtils.decode(this, uri, 1200, 1200);
        if (bmp == null) {
            Toast.makeText(this, R.string.bead_pick_fail, Toast.LENGTH_SHORT).show();
            return;
        }
        if (BeadData.source != null && BeadData.source != bmp && !BeadData.source.isRecycled()) {
            BeadData.source.recycle();
        }
        BeadData.source = bmp;
        BeadData.sourceUri = uri;
        goConfig();
    }

    private void next() {
        float aspect = cropView.getCurrentAspect();
        int outW = 1200;
        int outH = Math.max(400, Math.min(2000, Math.round(outW / aspect)));
        Bitmap cropped = cropView.getCroppedBitmap(outW, outH);
        if (cropped == null) {
            Toast.makeText(this, R.string.bead_pick_fail, Toast.LENGTH_SHORT).show();
            return;
        }
        if (BeadData.source != null && BeadData.source != cropped && !BeadData.source.isRecycled()) {
            BeadData.source.recycle();
        }
        BeadData.source = cropped;
        BeadData.sourceUri = uri;
        goConfig();
    }

    private void goConfig() {
        startActivity(new Intent(this, BeadConfigActivity.class));
        finish();
    }
}
