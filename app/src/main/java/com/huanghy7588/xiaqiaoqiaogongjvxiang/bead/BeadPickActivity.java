package com.huanghy7588.xiaqiaoqiaogongjvxiang.bead;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;
import com.huanghy7588.xiaqiaoqiaogongjvxiang.pintu.PintuUtils;

import java.io.File;

/**
 * 拼豆图纸生成 · 第一步：选择图片（相册 / 拍照）。
 */
public class BeadPickActivity extends AppCompatActivity {

    private ImageView ivPreview;
    private TextView tvPlaceholder;
    private Uri pickedUri;

    private File captureFile;
    private final ActivityResultLauncher<String> cameraPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (Boolean.TRUE.equals(granted)) launchCamera();
                else Toast.makeText(this, R.string.bead_need_image, Toast.LENGTH_SHORT).show();
            });
    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (Boolean.TRUE.equals(ok) && captureFile != null) {
                    setPicked(Uri.fromFile(captureFile));
                }
            });
    private final ActivityResultLauncher<String[]> albumLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    setPicked(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bead_pick);

        findViewById(R.id.btn_back_home).setOnClickListener(v -> finish());
        TextView tvTitle = findViewById(R.id.tv_top_title);
        tvTitle.setText(R.string.bead_title);
        tvTitle.setVisibility(View.VISIBLE);

        ivPreview = findViewById(R.id.iv_preview);
        tvPlaceholder = findViewById(R.id.tv_placeholder);

        findViewById(R.id.btn_album).setOnClickListener(v ->
                albumLauncher.launch(new String[]{"image/*"}));
        findViewById(R.id.btn_camera).setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                cameraPermLauncher.launch(Manifest.permission.CAMERA);
            }
        });
    }

    private void launchCamera() {
        try {
            captureFile = new File(getCacheDir(), "bead_capture.jpg");
            if (captureFile.exists() && !captureFile.delete()) { /* ignore */ }
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", captureFile);
            cameraLauncher.launch(uri);
        } catch (Exception e) {
            Toast.makeText(this, R.string.bead_pick_fail, Toast.LENGTH_SHORT).show();
        }
    }

    private void setPicked(Uri uri) {
        pickedUri = uri;
        Bitmap thumb = PintuUtils.decodeThumb(this, uri, 1024);
        if (thumb == null) {
            Toast.makeText(this, R.string.bead_pick_fail, Toast.LENGTH_SHORT).show();
            return;
        }
        ivPreview.setImageBitmap(thumb);
        ivPreview.setVisibility(View.VISIBLE);
        tvPlaceholder.setVisibility(View.GONE);

        Intent intent = new Intent(this, BeadCropActivity.class);
        intent.putExtra(BeadCropActivity.EXTRA_URI, uri);
        startActivity(intent);
    }
}
