package com.huanghy7588.xiaqiaoqiaogongjvxiang.bead;

import androidx.core.graphics.ColorUtils;

/**
 * 单个拼豆颜色：色号、屏幕参考 hex、RGB、色系分组，以及预计算好的 Lab（用于 CIEDE2000 匹配）。
 * 注意：hex/rgb 只是屏幕参考值，不等于实物颜色，严谨对色以实物色卡为准。
 */
public class BeadColor {
    public final String code;
    public final String hex;
    public final int[] rgb;
    public final String group;
    public final double[] lab = new double[3];

    public BeadColor(String code, String hex, int[] rgb, String group) {
        this.code = code;
        this.hex = hex;
        this.rgb = rgb;
        this.group = (group == null) ? "" : group;
        ColorUtils.RGBToLAB(rgb[0], rgb[1], rgb[2], lab);
    }
}
