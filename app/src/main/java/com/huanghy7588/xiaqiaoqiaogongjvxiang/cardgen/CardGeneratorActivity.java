package com.huanghy7588.xiaqiaoqiaogongjvxiang.cardgen;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * 卡片生成器：输入头像/名字/留言/来源/时间/点赞数，实时预览留言卡片，一键导出为图片保存到相册。
 */
public class CardGeneratorActivity extends AppCompatActivity {

    private ImageView ivAvatarPreview;
    private ImageView ivAvatarCard;
    private TextView tvName, tvMessage, tvSource, tvTime, tvLikes;
    private Bitmap avatarBmp;        // 圆形头像（透明背景），未选为 null
    private Bitmap pendingBmp;       // 预 Q 版本保存时暂存

    private final ActivityResultLauncher<Intent> cropLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), res -> {
                if (res.getResultCode() == Activity.RESULT_OK && res.getData() != null) {
                    String path = res.getData().getStringExtra(CardAvatarCropActivity.EXTRA_RESULT_PATH);
                    if (path != null) {
                        Bitmap b = BitmapFactory.decodeFile(path);
                        if (b != null) {
                            avatarBmp = b;
                            ivAvatarPreview.setImageBitmap(b);
                            ivAvatarCard.setImageBitmap(b);
                        }
                    }
                }
            });

    private final ActivityResultLauncher<String> pickLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                Intent crop = new Intent(this, CardAvatarCropActivity.class);
                crop.putExtra(CardAvatarCropActivity.EXTRA_URI, uri);
                cropLauncher.launch(crop);
            });

    private final ActivityResultLauncher<String> preQPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (Boolean.TRUE.equals(granted) && pendingBmp != null) {
                    doSavePreQ(pendingBmp);
                    pendingBmp = null;
                } else {
                    Toast.makeText(this, R.string.cardgen_save_fail, Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card_generator);

        // 统一返回按钮
        findViewById(R.id.btn_back_home).setOnClickListener(v -> finish());
        TextView tvTitle = findViewById(R.id.tv_top_title);
        tvTitle.setText(R.string.cardgen_title);
        tvTitle.setVisibility(View.VISIBLE);

        ivAvatarPreview = findViewById(R.id.iv_avatar_preview);
        ivAvatarCard = findViewById(R.id.iv_avatar);
        tvName = findViewById(R.id.tv_name);
        tvMessage = findViewById(R.id.tv_message);
        tvSource = findViewById(R.id.tv_source);
        tvTime = findViewById(R.id.tv_time);
        tvLikes = findViewById(R.id.tv_likes);

        // 初始占位头像（灰色圆）
        Bitmap placeholder = makePlaceholder();
        ivAvatarPreview.setImageBitmap(placeholder);
        ivAvatarCard.setImageBitmap(placeholder);

        // 选择头像
        findViewById(R.id.btn_pick_avatar).setOnClickListener(v -> pickLauncher.launch("image/*"));

        // 输入框
        EditText etName = findViewById(R.id.et_name);
        EditText etMessage = findViewById(R.id.et_message);
        EditText etSource = findViewById(R.id.et_source);
        EditText etTime = findViewById(R.id.et_time);
        EditText etLikes = findViewById(R.id.et_likes);

        final String ph = getString(R.string.cardgen_placeholder);
        etName.addTextChangedListener(new SimpleWatcher(s -> tvName.setText(s.isEmpty() ? ph : s)));
        etMessage.addTextChangedListener(new SimpleWatcher(s -> tvMessage.setText(s.isEmpty() ? ph : s)));
        etSource.addTextChangedListener(new SimpleWatcher(s -> tvSource.setText(s.isEmpty() ? ph : s)));
        etTime.addTextChangedListener(new SimpleWatcher(s -> tvTime.setText(s.isEmpty() ? ph : s)));
        etLikes.addTextChangedListener(new SimpleWatcher(s -> tvLikes.setText(s.isEmpty() ? ph : s)));

        // 预览初始为「请输入」占位
        tvName.setText(ph);
        tvMessage.setText(ph);
        tvSource.setText(ph);
        tvTime.setText(ph);
        tvLikes.setText(ph);

        // 导出
        Button btnExport = findViewById(R.id.btn_export);
        btnExport.setOnClickListener(v -> exportCard());
    }

    private interface TextChange {
        void onText(String s);
    }

    private static class SimpleWatcher implements TextWatcher {
        private final TextChange cb;
        SimpleWatcher(TextChange cb) { this.cb = cb; }
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void afterTextChanged(Editable s) {
            cb.onText(s == null ? "" : s.toString());
        }
    }

    private Bitmap makePlaceholder() {
        int s = 256;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFE0E0E0);
        c.drawCircle(s / 2f, s / 2f, s / 2f, p);
        return b;
    }

    /** 生成卡片图片并保存。 */
    private void exportCard() {
        if (avatarBmp == null) {
            // 用占位头像也能导出；若想强制要求选头像可改为 Toast 提示。这里允许占位。
        }
        View card = findViewById(R.id.card_preview);
        card.post(() -> {
            int w = card.getWidth();
            int h = card.getHeight();
            if (w <= 0 || h <= 0) {
                Toast.makeText(this, R.string.cardgen_save_fail, Toast.LENGTH_SHORT).show();
                return;
            }
            float density = getResources().getDisplayMetrics().density;
            int shadow = (int) (12 * density);
            int offsetY = (int) (6 * density);
            int totalW = w + shadow * 2;
            int totalH = h + shadow * 2 + offsetY;

            Bitmap out = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);

            // 一点点阴影
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setColor(Color.BLACK);
            sp.setAlpha(38);
            float r = 16 * density;
            c.drawRoundRect(new RectF(shadow, shadow + offsetY, shadow + w, shadow + h + offsetY), r, r, sp);

            // 卡片本体
            Bitmap cardBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            card.draw(new Canvas(cardBmp));
            c.drawBitmap(cardBmp, shadow, shadow, null);

            saveToGallery(out);
        });
    }

    private void saveToGallery(Bitmap bmp) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.content.ContentValues v = new android.content.ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, "卡片_" + System.currentTimeMillis() + ".png");
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/XiaQiaoQiao");
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) {
                Toast.makeText(this, R.string.cardgen_save_fail, Toast.LENGTH_SHORT).show();
                return;
            }
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null || !bmp.compress(Bitmap.CompressFormat.PNG, 100, os)) {
                    Toast.makeText(this, R.string.cardgen_save_fail, Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(this, R.string.cardgen_saved, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, R.string.cardgen_save_fail, Toast.LENGTH_SHORT).show();
            }
        } else {
            // Android 9 及以下：需要 WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                pendingBmp = bmp;
                preQPermLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
                return;
            }
            doSavePreQ(bmp);
        }
    }

    private void doSavePreQ(Bitmap bmp) {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "XiaQiaoQiao");
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(this, R.string.cardgen_save_fail, Toast.LENGTH_SHORT).show();
            return;
        }
        File file = new File(dir, "卡片_" + System.currentTimeMillis() + ".png");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)) {
                Toast.makeText(this, R.string.cardgen_save_fail, Toast.LENGTH_SHORT).show();
                return;
            }
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
            Toast.makeText(this, R.string.cardgen_saved, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.cardgen_save_fail, Toast.LENGTH_SHORT).show();
        }
    }
}
