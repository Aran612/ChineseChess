package com.purexiangqi.app;

import android.os.Bundle;
import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 沉浸式：内容延伸到系统栏下方，再按真实安全区给内容容器加内边距，
        // 避免顶部状态栏、底部手势条与 App 按钮重叠（华为/小米全面屏手势机型）。
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        ViewCompat.setOnApplyWindowInsetsListener(getWindow().getDecorView(), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bottom = Math.max(
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom,
                insets.getInsets(WindowInsetsCompat.Type.systemGestures()).bottom
            );
            int left = insets.getInsets(WindowInsetsCompat.Type.systemBars()).left;
            int right = insets.getInsets(WindowInsetsCompat.Type.systemBars()).right;

            View content = findViewById(android.R.id.content);
            if (content != null) {
                content.setPadding(left, top, right, bottom);
            }
            return insets;
        });
    }
}
