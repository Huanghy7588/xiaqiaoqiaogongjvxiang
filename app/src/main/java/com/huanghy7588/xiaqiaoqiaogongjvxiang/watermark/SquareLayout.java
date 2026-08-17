package com.huanghy7588.xiaqiaoqiaogongjvxiang.watermark;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 固定 1:1 正方形的容器。
 *
 * 以可用宽度为基准，强制自身高度等于宽度，使内部内容（水印预览）处于正方形区域中，
 * 图片按真实比例居中显示（fitCenter），不会因容器是长条而被拉伸 / 放大。
 *
 * 用法：在 XML 中将 layout_width / layout_height 设为 wrap_content，并用
 * android:layout_gravity="center_horizontal" 让其在父容器中水平居中。
 */
public class SquareLayout extends FrameLayout {

    /** 预览最大边长（dp），避免平板 / 大屏上正方形过大挤占下方控件 */
    private static final int MAX_SIDE_DP = 400;

    public SquareLayout(@NonNull Context context) {
        super(context);
    }

    public SquareLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SquareLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int availableWidth = MeasureSpec.getSize(widthMeasureSpec);

        // 上限保护：不超过 MAX_SIDE_DP，避免大屏设备预览占满整屏
        int maxSidePx = Math.round(MAX_SIDE_DP * getResources().getDisplayMetrics().density);
        int side = Math.min(availableWidth, maxSidePx);
        if (side <= 0) side = availableWidth;

        // 固定为正方向：宽 = 高 = side
        int squareSpec = MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY);
        super.onMeasure(squareSpec, squareSpec);
    }
}
