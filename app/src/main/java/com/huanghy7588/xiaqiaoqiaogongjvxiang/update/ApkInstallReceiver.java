package com.huanghy7588.xiaqiaoqiaogongjvxiang.update;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;

import java.io.File;

/**
 * APK 下载完成广播接收器。
 *
 * 下载完成后：
 * 1. 比对下载 ID 确认是本应用触发的下载。
 * 2. 获取 APK 文件路径。
 * 3. Android 7.0+ 使用 FileProvider 获取 content URI 安装。
 * 4. Android 7.0 以下直接使用 file:// URI 安装。
 */
public class ApkInstallReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) return;

        long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
        long savedId = ApkDownloadHelper.getDownloadId(context);

        // 确认是本应用的下载
        if (downloadId != savedId || downloadId == -1) return;

        // 获取下载文件路径
        String filePath = ApkDownloadHelper.getDownloadedFilePath(context, downloadId);
        if (filePath == null) {
            Toast.makeText(context, R.string.update_download_fail, Toast.LENGTH_SHORT).show();
            return;
        }

        installApk(context, filePath);
    }

    /**
     * 弹出系统安装界面。
     * 适配 Android 7.0+ FileProvider。
     */
    private void installApk(Context context, String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            Toast.makeText(context, R.string.update_download_fail, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setDataAndType(getApkUri(context, file), "application/vnd.android.package-archive");

        // Android 7.0+ 需要授予权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }

        try {
            context.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, R.string.update_download_fail, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 获取 APK 文件的 URI。
     * Android 7.0+ 使用 FileProvider，低版本直接使用 file:// URI。
     */
    private Uri getApkUri(Context context, File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Android 7.0+ 必须使用 FileProvider
            return FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", file);
        } else {
            // Android 7.0 以下直接使用 file URI
            return Uri.fromFile(file);
        }
    }
}
