package com.huanghy7588.xiaqiaoqiaogongjvxiang.update;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;

/**
 * APK 下载辅助类。
 *
 * 使用系统 DownloadManager 下载 APK：
 * - 存储到公共 Download 目录，文件名 app-release.apk。
 * - 下载 ID 保存到 SharedPreferences，供 ApkInstallReceiver 比对。
 * - 下载完成后由系统发送广播，Receiver 负责弹出安装界面。
 */
public class ApkDownloadHelper {

    /** SharedPreferences 文件名 */
    private static final String PREF_NAME = "update_pref";
    /** 保存下载 ID 的键 */
    private static final String KEY_DOWNLOAD_ID = "download_id";
    /** APK 文件名 */
    public static final String APK_FILE_NAME = "app-release.apk";

    /**
     * 启动 APK 下载。
     */
    public static void downloadApk(Context context, String url) {
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

            // 保存下载 ID
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(KEY_DOWNLOAD_ID, downloadId)
                    .apply();

            Toast.makeText(context, R.string.update_downloading, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, R.string.update_download_fail, Toast.LENGTH_SHORT).show();
        }
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
                    String localUri = cursor.getString(cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_LOCAL_URI));
                    if (localUri != null) {
                        Uri uri = Uri.parse(localUri);
                        return uri.getPath();
                    }
                }
            }
        } finally {
            cursor.close();
        }
        return null;
    }
}
