package com.huanghy7588.xiaqiaoqiaogongjvxiang.qr;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;

/** 二维码工具入口：图片转二维码 / 文字转二维码 两个大选项。 */
public class QrToolActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_tool);

        findViewById(R.id.btn_back_home).setOnClickListener(v -> finish());
        TextView tv = findViewById(R.id.tv_top_title);
        tv.setVisibility(View.VISIBLE);
        tv.setText(R.string.qr_tool_title);

        findViewById(R.id.btn_qr_image).setOnClickListener(v ->
                startActivity(new Intent(this, QrImageActivity.class)));
        findViewById(R.id.btn_qr_text).setOnClickListener(v ->
                startActivity(new Intent(this, QrTextActivity.class)));
    }
}
