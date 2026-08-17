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

    /** 预览最小边长（dp），空间不足时也不小于此值，保证预览始终可见 */
    private static final int MIN_SIDE_DP = 200;

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
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        float density = getResources().getDisplayMetrics().density;
        int maxSidePx = Math.round(MAX_SIDE_DP * density);
        int minSidePx = Math.round(MIN_SIDE_DP * density);

        // 宽度候选：无明确约束时用上限；否则用父给的宽度（屏幕宽）
        int widthCandidate = (widthMode == MeasureSpec.UNSPECIFIED) ? maxSidePx : widthSize;
        int side = Math.min(widthCandidate, maxSidePx);

        // 高度若被精确约束（LinearLayout 用 weight 分配剩余空间），正方形边长不超过该高度，
        // 这样空间不足时预览会自动缩小，给下方 ScrollView 留出可滚动空间
        if (heightMode == MeasureSpec.EXACTLY && heightSize > 0) {
            side = Math.min(side, heightSize);
        }

        // 保底最小边长，保证预览始终可见
        if (side < minSidePx) side = minSidePx;
        if (side <= 0) side = widthCandidate;

        // 固定为正方形：宽 = 高 = side
        int squareSpec = MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY);
        super.onMeasure(squareSpec, squareSpec);
    }
}
