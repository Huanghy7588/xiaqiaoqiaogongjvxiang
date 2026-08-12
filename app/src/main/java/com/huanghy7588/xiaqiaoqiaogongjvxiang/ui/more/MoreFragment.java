package com.huanghy7588.xiaqiaoqiaogongjvxiang.ui.more;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.huanghy7588.xiaqiaoqiaogongjvxiang.R;
import com.huanghy7588.xiaqiaoqiaogongjvxiang.update.UpdateChecker;

/**
 * 更多 Fragment：显示当前版本号，提供手动检查更新入口。
 */
public class MoreFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_more, container, false);

        // 显示当前版本号
        TextView tvVersion = root.findViewById(R.id.tv_version);
        String versionName = UpdateChecker.getLocalVersionName(requireContext());
        int versionCode = UpdateChecker.getLocalVersionCode(requireContext());
        tvVersion.setText(versionName + " (" + versionCode + ")");

        // 手动检查更新
        Button btnCheckUpdate = root.findViewById(R.id.btn_check_update);
        btnCheckUpdate.setOnClickListener(v -> {
            if (getActivity() == null) return;
            btnCheckUpdate.setText(R.string.more_checking);
            btnCheckUpdate.setEnabled(false);
            UpdateChecker.checkManual(getActivity());

            // 3 秒后恢复按钮状态（检测会在主线程回调弹窗/Toast）
            v.postDelayed(() -> {
                btnCheckUpdate.setText(R.string.more_check_update);
                btnCheckUpdate.setEnabled(true);
            }, 3000);
        });

        return root;
    }
}
