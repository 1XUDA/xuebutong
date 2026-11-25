package com.xuebutong.app.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public final class QuestionAccessibilityService extends AccessibilityService {
    private static volatile QuestionAccessibilityService connectedService;

    public static PageText capturePage() {
        return PageText.read(connectedService);
    }

    public static boolean isEnabled(Context context) {
        String enabled = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) {
            return false;
        }
        ComponentName component = new ComponentName(context, QuestionAccessibilityService.class);
        return enabled.contains(component.flattenToString());
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        connectedService = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 点击悬浮球时再按需读取屏幕，避免持续遍历界面阻塞主线程。
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        clearConnectedService();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        clearConnectedService();
        super.onDestroy();
    }

    private void clearConnectedService() {
        if (connectedService == this) {
            connectedService = null;
        }
    }
}
