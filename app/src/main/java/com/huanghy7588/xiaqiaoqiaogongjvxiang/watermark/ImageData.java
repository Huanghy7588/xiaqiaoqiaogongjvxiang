package com.huanghy7588.xiaqiaoqiaogongjvxiang.watermark;

import android.net.Uri;

/**
 * 图片数据模型。
 * 每张图保存自己的水印位置和大小，便于单独微调或批量应用。
 */
public class ImageData {
    /** 图片 URI */
    public Uri uri;

    /** 水印中心 X（占图片宽度比例 0~1） */
    public float centerXFrac = 0.82f;

    /** 水印中心 Y（占图片高度比例 0~1） */
    public float centerYFrac = 0.88f;

    /** 文字大小比例（占图片宽度比例） */
    public float textSizeFactor = 0.06f;

    public ImageData(Uri uri) {
        this.uri = uri;
    }
}
