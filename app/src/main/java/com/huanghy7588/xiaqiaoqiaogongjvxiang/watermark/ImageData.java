package com.huanghy7588.xiaqiaoqiaogongjvxiang.watermark;

import android.net.Uri;

/**
 * 图片数据模型。
 * 每张图保存自己的水印位置和大小，便于单独微调或批量应用。
 */
public class ImageData {

    /** 水印位置模式：居中 */
    public static final int POSITION_CENTER = 0;
    /** 水印位置模式：左下角（文字自动贴左下，保证完整显示） */
    public static final int POSITION_BOTTOM_LEFT = 1;
    /** 水印位置模式：自定义（用户拖拽或微调后自动切换） */
    public static final int POSITION_CUSTOM = 2;
    /** 水印位置模式：右下角（文字自动贴右下，保证完整显示） */
    public static final int POSITION_BOTTOM_RIGHT = 3;

    /** 图片 URI */
    public Uri uri;

    /** 水印位置模式（居中 / 左下角 / 右下角 / 自定义） */
    public int positionMode = POSITION_CENTER;

    /** 水印中心 X（占图片宽度比例 0~1，仅 CUSTOM 模式使用） */
    public float centerXFrac = 0.5f;

    /** 水印中心 Y（占图片高度比例 0~1，仅 CUSTOM 模式使用） */
    public float centerYFrac = 0.5f;

    /** 文字大小比例（占图片宽度比例） */
    public float textSizeFactor = 0.06f;

    public ImageData(Uri uri) {
        this.uri = uri;
    }
}
