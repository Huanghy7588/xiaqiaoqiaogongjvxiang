package com.huanghy7588.xiaqiaoqiaogongjvxiang;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.huanghy7588.xiaqiaoqiaogongjvxiang.ui.home.HomeFragment;
import com.huanghy7588.xiaqiaoqiaogongjvxiang.ui.more.MoreFragment;
import com.huanghy7588.xiaqiaoqiaogongjvxiang.update.UpdateChecker;

/**
 * 主界面：底部双 Tab（首页 / 更多）。
 * 启动及恢复时自动检测更新（带 24 小时节流）。
 */
public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initBottomNavigation();

        // 默认显示首页
        if (savedInstanceState == null) {
            switchTo(new HomeFragment());
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
                switchTo(new HomeFragment());
                return true;
            } else if (id == R.id.nav_more) {
                switchTo(new MoreFragment());
                return true;
            }
            return false;
        });
    }

    /** 替换当前 Fragment */
    private void switchTo(@NonNull Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}
