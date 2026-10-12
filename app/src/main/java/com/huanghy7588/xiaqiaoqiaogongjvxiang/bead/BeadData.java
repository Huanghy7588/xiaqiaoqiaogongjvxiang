package com.huanghy7588.xiaqiaoqiaogongjvxiang.bead;

import android.graphics.Bitmap;
import android.net.Uri;

/**
 * 拼豆流程跨 Activity 的临时数据传递（图片源、配置、生成结果）。
 * 进程级静态持有，进入预览后会被消费；退出流程时清理，避免内存泄漏。
 */
public class BeadData {
    public static Bitmap source;     // 当前待生成源图（已裁剪）
    public static Uri sourceUri;     // 原始图片 Uri
    public static BeadConfig config; // 当前配置
    public static BeadColor[][] grid;// 生成结果

    public static void recycleSource() {
        if (source != null && !source.isRecycled()) {
            source.recycle();
        }
        source = null;
    }

    public static void clearAll() {
        recycleSource();
        sourceUri = null;
        config = null;
        grid = null;
    }
}
