package com.huanghy7588.xiaqiaoqiaogongjvxiang.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;
import com.huanghy7588.xiaqiaoqiaogongjvxiang.watermark.WatermarkToolActivity;
import com.huanghy7588.xiaqiaoqiaogongjvxiang.wuzhong.WuzhongTableActivity;

/**
 * 首页 Fragment：展示功能入口列表。
 * 当前包含"素材水印工具"，点击进入对应功能页。
 */
public class HomeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        // 点击素材水印工具卡片
        View watermarkCard = root.findViewById(R.id.card_watermark);
        watermarkCard.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), WatermarkToolActivity.class);
            startActivity(intent);
        });

        // 点击无中生有表格工具卡片
        View wuzhongCard = root.findViewById(R.id.card_wuzhong);
        wuzhongCard.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), WuzhongTableActivity.class);
            startActivity(intent);
        });

        return root;
    }
}
