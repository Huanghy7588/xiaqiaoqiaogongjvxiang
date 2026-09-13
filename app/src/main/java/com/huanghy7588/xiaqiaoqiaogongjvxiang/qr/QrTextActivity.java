package com.huanghy7588.xiaqiaoqiaogongjvxiang.qr;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;

/** 文字转二维码：输入文字 → 生成。 */
public class QrTextActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_text);

        findViewById(R.id.btn_back_home).setOnClickListener(v -> finish());
        TextView tv = findViewById(R.id.tv_top_title);
        tv.setVisibility(View.VISIBLE);
        tv.setText(R.string.qr_text_title);

        findViewById(R.id.btn_generate).setOnClickListener(v -> {
            EditText et = findViewById(R.id.et_text);
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
}
