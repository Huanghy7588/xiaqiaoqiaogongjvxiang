package com.huanghy7588.xiaqiaoqiaogongjvxiang.update;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 更新检测器。
 *
 * 工作流程：
 * 1. 子线程请求远程 JSON（通过 jsDelivr CDN，国内可访问）。
 * 2. 主线程先弹公告（若 enabled），公告关闭后再检测更新。
 * 3. 远程 versionCode > 本地 versionCode 时弹更新对话框。
 * 4. 非强制更新提供"立即更新"和"稍后"按钮。
 * 5. 点击"立即更新"使用 DownloadManager 下载 APK。
 *
 * 两种调用方式：
 * - {@link #checkAuto(Activity)}：自动检测，带 24 小时节流（不打扰用户）。
 * - {@link #checkManual(Activity)}：手动检测，无视节流，无更新时提示"已是最新"。
 */
public class UpdateChecker {

    // ==================== 配置 ====================

    /**
     * 远程 JSON 主地址（jsDelivr CDN，国内可访问）。
     * 使用 @latest 解析到最新 tag——tag 解析是即时的，不像 @main 分支有最长 12 小时的缓存延迟。
     * 前提：每次发版都要打 tag（v1.0.x），并把 update.json 提交到仓库根目录。
     */
    private static final String JSON_BASE_URL =
            "https://cdn.jsdelivr.net/gh/Huanghy7588/xiaqiaoqiaogongjvxiang@latest/update.json";

    /** 备用地址（@main 分支解析，可能滞后但作为兜底） */
    private static final String JSON_FALLBACK_URL =
            "https://cdn.jsdelivr.net/gh/Huanghy7588/xiaqiaoqiaogongjvxiang@main/update.json";

    /** SharedPreferences 文件名 */
    private static final String PREF_NAME = "update_pref";
    /** 上次自动检测时间的键 */
    private static final String KEY_LAST_CHECK_TIME = "last_check_time";
    /** 自动检测间隔：24 小时（毫秒） */
    private static final long CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L;

    /** 防止同一 Activity 并发检测 */
    private static final Map<Integer, Boolean> checkingMap = new ConcurrentHashMap<>();

    // ==================== 公开方法 ====================

    /**
     * 自动检测（带 24 小时节流）。
     * 适用于 App 启动、onResume 等自动触发场景。
     * 24 小时内只检测一次，避免频繁请求网络。
     */
    public static void checkAuto(@NonNull final Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        long lastCheck = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_CHECK_TIME, 0);
        long now = System.currentTimeMillis();
        if (now - lastCheck < CHECK_INTERVAL_MS) return; // 未到检测间隔

        doCheck(activity, false);
    }

    /**
     * 手动检测（无视节流）。
     * 适用于用户主动点击"检查更新"按钮。
     * 无更新时弹出"已是最新版本"提示。
     */
    public static void checkManual(@NonNull final Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        doCheck(activity, true);
    }

    // ==================== 内部逻辑 ====================

    /**
     * 执行检测。
     * @param isManual 是否手动触发（影响无更新时的提示行为）
     */
    private static void doCheck(@NonNull final Activity activity, boolean isManual) {
        int key = System.identityHashCode(activity);
        if (checkingMap.containsKey(key)) return; // 该 Activity 已有检测进行中
        checkingMap.put(key, true);

        // 记录检测时间
        activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
                .apply();

        final boolean manual = isManual;
        new Thread(() -> {
            final UpdateModel model = fetchModelWithFallback();

            new Handler(Looper.getMainLooper()).post(() -> {
                checkingMap.remove(key);
                if (model == null) {
                    if (manual) toast(activity, "检测失败，请检查网络");
                    return;
                }
                if (activity.isFinishing() || activity.isDestroyed()) return;
                showAnnouncementThenUpdate(activity, model, manual);
            });
        }).start();
    }

    /**
     * 依次尝试主地址（@latest）和备用地址（@main），带时间戳防 CDN 缓存。
     * 任一地址拿到合法 JSON 即返回；全部失败返回 null。
     */
    private static UpdateModel fetchModelWithFallback() {
        String[] bases = {JSON_BASE_URL, JSON_FALLBACK_URL};
        for (String base : bases) {
            try {
                String url = base + "?t=" + System.currentTimeMillis();
                UpdateModel model = UpdateModel.parse(fetchJson(url));
                if (model != null) return model;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /** 先弹公告，关闭后再检测更新 */
    private static void showAnnouncementThenUpdate(Activity activity, UpdateModel model, boolean isManual) {
        if (model.announcement != null && model.announcement.enabled
                && !model.announcement.content.isEmpty()) {
            showAnnouncement(activity, model.announcement, () -> checkUpdate(activity, model, isManual));
        } else {
            checkUpdate(activity, model, isManual);
        }
    }

    /** 显示公告对话框 */
    private static void showAnnouncement(Activity activity, UpdateModel.Announcement ann,
                                          Runnable onDismiss) {
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
                .setTitle(ann.title != null && !ann.title.isEmpty()
                        ? ann.title : activity.getString(R.string.announcement_title))
                .setMessage(ann.content)
                .setCancelable(false)
                .setPositiveButton(R.string.confirm, (DialogInterface d, int w) -> {
                    d.dismiss();
                    onDismiss.run();
                })
                .show();
    }

    /** 检测版本号并弹更新对话框 */
    private static void checkUpdate(Activity activity, UpdateModel model, boolean isManual) {
        if (activity.isFinishing() || model.update == null) {
            if (isManual) toast(activity, "已是最新版本");
            return;
        }

        int localCode = getLocalVersionCode(activity);
        if (model.update.versionCode > localCode) {
            showUpdateDialog(activity, model.update);
        } else {
            if (isManual) toast(activity, "已是最新版本");
        }
    }

    /** 显示更新对话框 */
    private static void showUpdateDialog(Activity activity, UpdateModel.UpdateInfo info) {
        if (activity.isFinishing()) return;

        String message = info.updateLog;
        if (message == null || message.isEmpty()) {
            message = "新版本：" + info.versionName;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(R.string.update_title)
                .setMessage(message)
                .setPositiveButton(R.string.update_now, (d, w) -> {
                    d.dismiss();
                    startDownload(activity, info.downloadUrl);
                });

        // 非强制更新提供"稍后"按钮
        if (!info.forceUpdate) {
            builder.setNegativeButton(R.string.update_later, (d, w) -> d.dismiss());
        } else {
            builder.setCancelable(false);
        }

        builder.show();
    }

    /** 使用 DownloadManager 下载 APK */
    private static void startDownload(Context context, String url) {
        if (url == null || url.isEmpty()) {
            toast(context, context.getString(R.string.update_download_fail));
            return;
        }
        ApkDownloadHelper.downloadApk(context, url);
    }

    // ==================== 工具方法 ====================

    /** 获取本地 versionCode */
    public static int getLocalVersionCode(Context context) {
        try {
            PackageInfo pi = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                return (int) pi.getLongVersionCode();
            } else {
                return pi.versionCode;
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /** 获取本地 versionName */
    public static String getLocalVersionName(Context context) {
        try {
            PackageInfo pi = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return pi.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return "未知";
        }
    }

    /** 简化的 Toast */
    private static void toast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    /** 请求 JSON */
    private static String fetchJson(String urlStr) throws Exception {
        HttpURLConnection conn = null;
        InputStream is = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestMethod("GET");
            conn.setUseCaches(false); // 不使用本地缓存

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException("HTTP " + code);
            }

            is = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } finally {
            if (is != null) try { is.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }
}
