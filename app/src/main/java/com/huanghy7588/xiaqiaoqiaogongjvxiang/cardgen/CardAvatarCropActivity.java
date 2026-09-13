package com.huanghy7588.xiaqiaoqiaogongjvxiang.cardgen;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 头像裁剪页：接收图片 Uri，让用户拖动/缩放选择头像区域，确定后输出圆形头像文件。
 */
public class CardAvatarCropActivity extends AppCompatActivity {

    public static final String EXTRA_URI = "avatar_uri";
    public static final String EXTRA_RESULT_PATH = "avatar_path";

    private CropView cropView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card_crop);

        cropView = findViewById(R.id.crop_view);
        Uri uri = getIntent().getParcelableExtra(EXTRA_URI);

        if (uri == null) {
            finish();
            return;
        }

        Bitmap bmp = decodeSampled(getApplicationContext(), uri, 1600);
        if (bmp == null) {
            Toast.makeText(this, R.string.cardgen_save_fail, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        cropView.setSourceBitmap(bmp);

        Button btnConfirm = findViewById(R.id.btn_crop_confirm);
        btnConfirm.setOnClickListener(v -> {
            Bitmap cropped = cropView.getCroppedBitmap(512);
            if (cropped == null) {
                setResult(RESULT_CANCELED);
                finish();
                return;
            }
            File out = new File(getCacheDir(), "cardgen_avatar.png");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                cropped.compress(Bitmap.CompressFormat.PNG, 100, fos);
                Intent result = new Intent();
                result.putExtra(EXTRA_RESULT_PATH, out.getAbsolutePath());
                setResult(RESULT_OK, result);
            } catch (Exception e) {
                setResult(RESULT_CANCELED);
            }
            finish();
        });
    }

    /** 采样解码，避免大图 OOM（最长边不超过 maxDim）。 */
    private static Bitmap decodeSampled(android.content.Context ctx, Uri uri, int maxDim) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream is = ctx.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(is, null, o);
            }
            int w = o.outWidth;
            int h = o.outHeight;
            int sample = 1;
            while (Math.max(w, h) / sample > maxDim) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            try (InputStream is = ctx.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(is, null, o);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
