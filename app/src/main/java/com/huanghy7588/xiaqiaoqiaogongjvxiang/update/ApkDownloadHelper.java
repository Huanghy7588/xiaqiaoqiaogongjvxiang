package com.huanghy7588.xiaqiaoqiaogongjvxiang.update;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;

import java.util.Locale;

import java.io.File;
import java.lang.ref.WeakReference;

/**
 * APK 下载辅助类。
 *
 * 使用系统 DownloadManager 下载 APK：
 * - 存储到公共 Download 目录，文件名 app-release.apk。
 * - 下载 ID 保存到 SharedPreferences，供 ApkInstallReceiver 比对。
 * - 下载过程中弹出进度对话框（进度条 + 百分比 + 已下载大小）。
 * - 下载完成后由系统发送广播，Receiver 负责弹出安装界面。
 */
public class ApkDownloadHelper {

    /** SharedPreferences 文件名 */
    private static final String PREF_NAME = "update_pref";
    /** 保存下载 ID 的键 */
    private static final String KEY_DOWNLOAD_ID = "download_id";
    /** APK 文件名 */
    public static final String APK_FILE_NAME = "app-release.apk";
    /** 进度轮询间隔（毫秒） */
    private static final long POLL_INTERVAL_MS = 300;
    /** U14 修复：防止重复下载标志 */
    private static volatile boolean isDownloading = false;

    /**
     * 启动 APK 下载。
     */
    public static void downloadApk(Context context, String url) {
        // U14 修复：防止重复下载
        if (isDownloading) {
            Toast.makeText(context, "正在下载中，请稍候…", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(context.getString(R.string.app_name));
            request.setDescription(context.getString(R.string.update_downloading));
            request.setMimeType("application/vnd.android.package-archive");

            // 保存到公共 Download 目录
            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS, APK_FILE_NAME);

            // 下载中和下载完成后都显示通知
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            // 允许在移动网络下载
            request.setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);

            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) {
                Toast.makeText(context, R.string.update_download_fail, Toast.LENGTH_SHORT).show();
                return;
            }

            long downloadId = dm.enqueue(request);
            isDownloading = true; // U14：标记下载中

            // 保存下载 ID
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(KEY_DOWNLOAD_ID, downloadId)
                    .apply();

            // 弹出下载进度对话框（带浏览器下载兜底按钮）
            showProgressDialog(context, dm, downloadId, url);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, R.string.update_download_fail, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 显示下载进度对话框，并轮询 DownloadManager 更新进度。
     * 下载完成/失败后自动关闭，成功后由 ApkInstallReceiver 拉起安装界面。
     * 对话框内提供"用浏览器下载"兜底按钮，自动下载慢或失败时可随时点。
     */
    private static void showProgressDialog(Context context, DownloadManager dm, long downloadId,
                                           String url) {
        // 只能用 Activity 上下文弹对话框；非 Activity 时退化为 Toast 提示
        if (!(context instanceof Activity) || ((Activity) context).isFinishing()) {
            Toast.makeText(context, R.string.update_downloading, Toast.LENGTH_SHORT).show();
            return;
        }

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_download_progress, null);
        ProgressBar progressBar = view.findViewById(R.id.progress_bar);
        TextView tvProgress = view.findViewById(R.id.tv_progress);
        Button btnBrowser = view.findViewById(R.id.btn_browser_download);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.update_downloading)
                .setView(view)
                .setCancelable(false)
                .create();
        dialog.show();

        // 浏览器下载兜底：用系统浏览器打开 APK 直链，由用户在浏览器里完成下载
        btnBrowser.setOnClickListener(v -> openBrowserDownload(context, url));

        // 主线程轮询下载进度
        Handler handler = new Handler(Looper.getMainLooper());
        ProgressPoller poller = new ProgressPoller(context, dm, downloadId,
                dialog, progressBar, tvProgress, handler);
        handler.post(poller);
    }

    /**
     * 用系统浏览器打开下载链接（兜底：部分网络只有浏览器能下载）。
     */
    private static void openBrowserDownload(Context context, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Toast.makeText(context, R.string.update_browser_opened, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, R.string.update_browser_fail, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 进度轮询任务。
     * 每 POLL_INTERVAL_MS 查询一次 DownloadManager：
     * - 运行中/排队/暂停：刷新进度条与文字，继续轮询。
     * - 成功：关闭弹窗，停止轮询（安装界面由广播接收器拉起）。
     * - 失败：关闭弹窗并提示，停止轮询。
     */
    private static class ProgressPoller implements Runnable {
        // 全部改为弱引用，避免轮询期间持有 Activity/Dialog/View 强引用导致泄漏（U5）
        private final WeakReference<Context> contextRef;
        private final DownloadManager dm;
        private final long downloadId;
        private final WeakReference<AlertDialog> dialogRef;
        private final WeakReference<ProgressBar> progressBarRef;
        private final WeakReference<TextView> tvProgressRef;
        private final Handler handler;

        ProgressPoller(Context context, DownloadManager dm, long downloadId,
                       AlertDialog dialog, ProgressBar progressBar,
                       TextView tvProgress, Handler handler) {
            this.contextRef = new WeakReference<>(context);
            this.dm = dm;
            this.downloadId = downloadId;
            this.dialogRef = new WeakReference<>(dialog);
            this.progressBarRef = new WeakReference<>(progressBar);
            this.tvProgressRef = new WeakReference<>(tvProgress);
            this.handler = handler;
        }

        @Override
        public void run() {
            AlertDialog dialog = dialogRef.get();
            ProgressBar progressBar = progressBarRef.get();
            TextView tvProgress = tvProgressRef.get();
            Context ctx = contextRef.get();

            // Activity 已销毁/弹窗已回收则停止轮询（下载仍在系统后台继续）（U5）
            if (dialog == null || progressBar == null || tvProgress == null) {
                isDownloading = false; // U14：重置下载标志
                return;
            }
            if (ctx instanceof Activity
                    && (((Activity) ctx).isFinishing() || ((Activity) ctx).isDestroyed())) {
                isDownloading = false; // U14：重置下载标志
                dismissSafely(dialog);
                return;
            }

            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(downloadId);
            Cursor cursor = null;
            try {
                cursor = dm.query(query);
                if (cursor != null && cursor.moveToFirst()) {
                    int status = cursor.getInt(cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_STATUS));
                    long soFar = cursor.getLong(cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    long total = cursor.getLong(cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_TOTAL_SIZE_BYTES));

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        progressBar.setProgress(100);
                        tvProgress.setText("下载完成");
                        isDownloading = false; // U14：重置下载标志
                        dismissSafely(dialog);
                        return; // 安装界面由 ApkInstallReceiver 拉起
                    }
                    if (status == DownloadManager.STATUS_FAILED) {
                        isDownloading = false; // U14：重置下载标志
                        // 不直接关弹窗：提示改用浏览器下载，弹窗允许关闭返回
                        tvProgress.setText(R.string.update_fail_browser);
                        try {
                            dialog.setCancelable(true);
                        } catch (Exception ignored) {
                        }
                        return;
                    }

                    // 运行中 / 排队 / 暂停：刷新进度显示
                    if (total > 0) {
                        int percent = (int) (soFar * 100 / total);
                        progressBar.setIndeterminate(false);
                        progressBar.setProgress(percent);
                        tvProgress.setText(percent + "%（" + formatSize(soFar)
                                + " / " + formatSize(total) + "）");
                    } else {
                        // 总大小未知（服务器未返回 Content-Length）：显示已下载大小
                        progressBar.setIndeterminate(true);
                        tvProgress.setText("已下载 " + formatSize(soFar));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (cursor != null) cursor.close();
            }

            // 继续下一轮轮询
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }

        /** 安全关闭弹窗（可能已随 Activity 销毁） */
        private void dismissSafely(AlertDialog dialog) {
            try {
                if (dialog != null && dialog.isShowing()) dialog.dismiss();
            } catch (Exception ignored) {
            }
        }
    }

    /** 把字节数格式化为可读字符串（如 1.2 MB） */
    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
    }

    /**
     * 获取已保存的下载 ID。
     */
    public static long getDownloadId(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_DOWNLOAD_ID, -1);
    }

    /**
     * 通过下载 ID 获取已下载文件的本地路径。
     * @return 文件路径，如果未找到返回 null
     */
    /**
     * 获取已下载文件的本地路径。
     * 下载目标目录是我们显式指定的 Download/app-release.apk，
     * 因此直接返回该固定路径，避免依赖 COLUMN_LOCAL_URI
     * （某些设备会返回 content:// 导致 uri.getPath() 无效，见 U15）。
     * @return 文件路径，如果下载未完成返回 null
     */
    public static String getDownloadedFilePath(Context context, long downloadId) {
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return null;

        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(downloadId);
        Cursor cursor = dm.query(query);
        if (cursor == null) return null;

        try {
            if (cursor.moveToFirst()) {
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_STATUS));
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    // 使用我们显式设置的下载目的地，规避 content:// 解析问题（U15）
                    File file = new File(Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME);
                    return file.getAbsolutePath();
                }
            }
        } finally {
            cursor.close();
        }
        return null;
    }
}
