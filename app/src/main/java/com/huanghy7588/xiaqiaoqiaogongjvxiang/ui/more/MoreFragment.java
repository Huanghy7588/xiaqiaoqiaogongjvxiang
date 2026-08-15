package com.huanghy7588.xiaqiaoqiaogongjvxiang.ui.more;

import android.Manifest;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;
import com.huanghy7588.xiaqiaoqiaogongjvxiang.update.UpdateChecker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * 更多 Fragment：显示当前版本号，提供手动检查更新、投稿联系方式（QQ 长按复制）、打赏入口。
 */
public class MoreFragment extends Fragment {

    private static final String QQ_NUMBER = "3089096785";
    private static final String SAVE_FILE_NAME = "xiaqiaoqiao_donate_qr.png";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_more, container, false);

        // 显示当前版本号
        TextView tvVersion = root.findViewById(R.id.tv_version);
        String versionName = UpdateChecker.getLocalVersionName(requireContext());
        int versionCode = UpdateChecker.getLocalVersionCode(requireContext());
        tvVersion.setText(versionName + " (" + versionCode + ")");

        // 手动检查更新
        Button btnCheckUpdate = root.findViewById(R.id.btn_check_update);
        btnCheckUpdate.setOnClickListener(v -> {
            if (getActivity() == null) return;
            btnCheckUpdate.setText(R.string.more_checking);
            btnCheckUpdate.setEnabled(false);
            UpdateChecker.checkManual(getActivity());

            // 3 秒后恢复按钮状态（检测会在主线程回调弹窗/Toast）
            v.postDelayed(() -> {
                btnCheckUpdate.setText(R.string.more_check_update);
                btnCheckUpdate.setEnabled(true);
            }, 3000);
        });

        // QQ 号长按复制
        TextView tvQq = root.findViewById(R.id.tv_qq_number);
        tvQq.setOnLongClickListener(v -> {
            copyToClipboard(requireContext(), QQ_NUMBER);
            Toast.makeText(requireContext(), R.string.more_qq_copied, Toast.LENGTH_SHORT).show();
            return true;
        });

        // 打赏按钮：弹窗显示赞赏码
        Button btnDonate = root.findViewById(R.id.btn_donate);
        btnDonate.setOnClickListener(v -> showDonateDialog());

        return root;
    }

    /**
     * 显示打赏弹窗：赞赏码图片长按可保存到相册。
     */
    private void showDonateDialog() {
        if (getContext() == null) return;

        final Dialog dialog = new Dialog(getContext(), R.style.Theme_XiaQiaoQiao_DonateDialog);
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_donate, null, false);
        dialog.setContentView(view);

        ImageView ivQr = view.findViewById(R.id.iv_donate_qr);
        Button btnClose = view.findViewById(R.id.btn_donate_close);

        // 让中央卡片吃掉点击事件，防止冒泡到外部背景触发关闭
        View card = (View) ivQr.getParent();
        card.setClickable(true);
        card.setOnClickListener(v -> { /* 吃掉点击，不关闭 */ });

        // 长按图片：保存到相册
        ivQr.setOnLongClickListener(v -> {
            saveQrToGallery(ivQr);
            return true;
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        // 点击图片外部（半透明黑底）也可关闭
        view.setOnClickListener(v -> dialog.dismiss());

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialog.show();
    }

    /**
     * 将 ImageView 中的二维码 Bitmap 保存到系统相册。
     */
    private void saveQrToGallery(ImageView ivQr) {
        if (ivQr.getDrawable() == null) return;
        Bitmap bitmap;
        try {
            bitmap = ((BitmapDrawable) ivQr.getDrawable()).getBitmap();
        } catch (ClassCastException e) {
            Toast.makeText(requireContext(), R.string.more_donate_save_fail, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+：通过 MediaStore 写入，无需存储权限
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, SAVE_FILE_NAME);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/XiaQiaoQiao");

                Uri uri = requireContext().getContentResolver()
                        .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    Toast.makeText(requireContext(), R.string.more_donate_save_fail, Toast.LENGTH_SHORT).show();
                    return;
                }
                try (OutputStream out = requireContext().getContentResolver().openOutputStream(uri)) {
                    if (out == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        Toast.makeText(requireContext(), R.string.more_donate_save_fail, Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            } else {
                // Android 9 及以下：需要 WRITE_EXTERNAL_STORAGE（manifest 已配）
                if (ContextCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(requireContext(), R.string.more_donate_save_fail, Toast.LENGTH_SHORT).show();
                    return;
                }
                File pictures = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES);
                File dir = new File(pictures, "XiaQiaoQiao");
                if (!dir.exists() && !dir.mkdirs()) {
                    Toast.makeText(requireContext(), R.string.more_donate_save_fail, Toast.LENGTH_SHORT).show();
                    return;
                }
                File file = new File(dir, SAVE_FILE_NAME);
                try (FileOutputStream out = new FileOutputStream(file)) {
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        Toast.makeText(requireContext(), R.string.more_donate_save_fail, Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                // 通知系统扫描
                requireContext().sendBroadcast(new android.content.Intent(
                        android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                        Uri.fromFile(file)));
            }

            Toast.makeText(requireContext(), R.string.more_donate_save_done, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.more_donate_save_fail, Toast.LENGTH_SHORT).show();
        }
    }

    private void copyToClipboard(Context context, String text) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(ClipData.newPlainText("QQ", text));
    }
}