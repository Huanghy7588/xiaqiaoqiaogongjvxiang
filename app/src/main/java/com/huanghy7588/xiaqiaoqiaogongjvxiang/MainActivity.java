package com.huanghy7588.xiaqiaoqiaogongjvxiang;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.huanghy7588.xiaqiaoqiaogongjvxiang.ui.home.HomeFragment;
import com.huanghy7588.xiaqiaoqiaogongjvxiang.ui.more.MoreFragment;
import com.huanghy7588.xiaqiaoqiaogongjvxiang.update.UpdateChecker;

/**
 * 主界面：底部双 Tab（首页 / 更多）。
 * 启动及恢复时自动检测更新（带 24 小时节流）。
 * M10 修复：Fragment 使用 show/hide 而非 replace，保留运行时状态。
 */
public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private Fragment current;
    private HomeFragment homeFragment;
    private MoreFragment moreFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initBottomNavigation();

        // 默认显示首页
        if (savedInstanceState == null) {
            switchTo(getHomeFragment());
        }

        // 启动时自动检测（带节流）
        UpdateChecker.checkAuto(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 恢复时自动检测（带节流，24 小时内只检测一次）
        UpdateChecker.checkAuto(this);
    }

    /** 初始化底部导航，切换 Fragment */
    private void initBottomNavigation() {
        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                switchTo(getHomeFragment());
                return true;
            } else if (id == R.id.nav_more) {
                switchTo(getMoreFragment());
                return true;
            }
            return false;
        });
    }

    private HomeFragment getHomeFragment() {
        if (homeFragment == null) homeFragment = new HomeFragment();
        return homeFragment;
    }

    private MoreFragment getMoreFragment() {
        if (moreFragment == null) moreFragment = new MoreFragment();
        return moreFragment;
    }

    /** M10 修复：show/hide 模式，不销毁重建 Fragment，保留运行时状态 */
    private void switchTo(@NonNull Fragment target) {
        if (target == current) return;
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        if (current != null) ft.hide(current);
        if (target.isAdded()) {
            ft.show(target);
        } else {
            ft.add(R.id.fragment_container, target);
        }
        ft.commit();
        current = target;
    }
}
