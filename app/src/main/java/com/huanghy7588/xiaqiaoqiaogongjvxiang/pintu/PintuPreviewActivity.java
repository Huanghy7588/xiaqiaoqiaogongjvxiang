package com.huanghy7588.xiaqiaoqiaogongjvxiang.pintu;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;
import com.huanghy7588.xiaqiaoqiaogongjvxiang.wuzhong.ZoomImageView;

import java.util.ArrayList;
import java.util.List;

/**
 * 一键拼图：拼图结果大图预览。
 * 通过静态字段接收主界面的结果列表与当前索引（同进程内传递 Bitmap）。
 */
public class PintuPreviewActivity extends AppCompatActivity {

    /** 共享的拼图结果（与主界面同一个引用） */
    public static List<Bitmap> sharedResults;
    /** 共享的当前索引 */
    public static int sharedIndex;

    private ZoomImageView previewView;
    private TextView tvIndex;
    private Bitmap currentBitmap;
    private int index = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pintu_preview);

        previewView = findViewById(R.id.pintu_preview_view);
        tvIndex = findViewById(R.id.tv_pintu_index);

        if (sharedResults == null || sharedResults.isEmpty()) {
            Toast.makeText(this, R.string.pintu_no_result, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Button btnPrev = findViewById(R.id.btn_pintu_prev);
        Button btnNext = findViewById(R.id.btn_pintu_next);
        Button btnClose = findViewById(R.id.btn_pintu_close);
        btnPrev.setOnClickListener(v -> navigate(-1));
        btnNext.setOnClickListener(v -> navigate(1));
        btnClose.setOnClickListener(v -> finish());

        index = Math.min(Math.max(sharedIndex, 0), sharedResults.size() - 1);
        show();
    }

    private void show() {
        Bitmap bmp = sharedResults.get(index);
        if (currentBitmap != null && currentBitmap != bmp) {
            currentBitmap = null;   // 共享位图不回收，交给主界面管理
        }
        currentBitmap = bmp;
        previewView.setBitmap(bmp);
        tvIndex.setText(getString(R.string.pintu_preview_index, index + 1, sharedResults.size()));
    }

    private void navigate(int delta) {
        int next = index + delta;
        if (next < 0 || next >= sharedResults.size()) return;
        index = next;
        show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        previewView.setBitmap(null);
        sharedResults = new ArrayList<>();
        currentBitmap = null;
    }
}
