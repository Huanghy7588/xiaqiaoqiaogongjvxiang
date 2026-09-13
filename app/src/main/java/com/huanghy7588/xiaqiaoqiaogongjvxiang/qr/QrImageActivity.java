package com.huanghy7588.xiaqiaoqiaogongjvxiang.qr;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;
import com.huanghy7588.xiaqiaoqiaogongjvxiang.tiemo.TiemoUtils;

/** 图片转二维码：选图 → 解析 → 编辑内容 → 生成。 */
public class QrImageActivity extends AppCompatActivity {

    private Uri pickedUri = null;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String> picker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    pickedUri = uri;
                    Bitmap thumb = TiemoUtils.decodeUri(this, uri, 800);
                    ImageView iv = findViewById(R.id.iv_thumb);
                    iv.setImageBitmap(thumb);
                    findViewById(R.id.fl_thumb).setVisibility(View.VISIBLE);
                    decodeAndShow(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_image);

        findViewById(R.id.btn_back_home).setOnClickListener(v -> finish());
        TextView tv = findViewById(R.id.tv_top_title);
        tv.setVisibility(View.VISIBLE);
        tv.setText(R.string.qr_image_title);

        findViewById(R.id.btn_pick).setOnClickListener(v -> picker.launch("image/*"));

        findViewById(R.id.btn_copy).setOnClickListener(v -> {
            EditText et = findViewById(R.id.et_content);
            String t = et.getText().toString();
            if (t.isEmpty()) {
                Toast.makeText(this, R.string.qr_empty_text, Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("qr", t));
            Toast.makeText(this, R.string.qr_copied, Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_generate).setOnClickListener(v -> {
            EditText et = findViewById(R.id.et_content);
            String t = et.getText().toString().trim();
            if (t.isEmpty()) {
                Toast.makeText(this, R.string.qr_empty_text, Toast.LENGTH_SHORT).show();
                return;
            }
            Intent it = new Intent(this, QrResultActivity.class);
            it.putExtra("qr_text", t);
            startActivity(it);
        });
    }

    private void decodeAndShow(Uri uri) {
        ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage(getString(R.string.qr_decode_ing));
        pd.setCancelable(false);
        pd.show();
        new Thread(() -> {
            Bitmap bmp = TiemoUtils.decodeUri(QrImageActivity.this, uri, 2000);
            final String text = (bmp != null) ? QrUtils.decodeBitmap(bmp) : null;
            if (bmp != null && !bmp.isRecycled()) bmp.recycle();
            ui.post(() -> {
                if (pd.isShowing()) pd.dismiss();
                EditText et = findViewById(R.id.et_content);
                if (text != null && !text.isEmpty()) {
                    et.setText(text);
                } else {
                    Toast.makeText(QrImageActivity.this, R.string.qr_decode_fail, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }
}
