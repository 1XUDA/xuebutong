package com.xuebutong.app.accessibility;

import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.accessibilityservice.AccessibilityService;
import java.util.List;

/** 仅在用户点击时读取前台应用中可见的非密码文字。 */
public final class PageText {
    public final String text, owner;
    public final int window;

    public PageText(String owner, int window, String text) {
        this.owner = owner;
        this.window = window;
        this.text = text;
    }

    static PageText read(AccessibilityService service) {
        if (service == null) throw new IllegalStateException("无障碍服务未连接，请重新开启");
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root != null) {
            try {
                if (external(service, root)) return collect(root);
            } finally { root.recycle(); }
        }
        List<AccessibilityWindowInfo> windows = service.getWindows();
        try {
            AccessibilityWindowInfo candidate = null;
            int apps = 0;
            for (AccessibilityWindowInfo w : windows) {
                if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                AccessibilityNodeInfo node = w.getRoot();
                if (node == null) continue;
                try {
                    if (!external(service, node)) continue;
                    apps++;
                    if (w.isActive() || w.isFocused()) return collect(node);
                    candidate = w;
                } finally { node.recycle(); }
            }
            if (apps == 1 && candidate != null) {
                AccessibilityNodeInfo node = candidate.getRoot();
                if (node != null) {
                    try { if (external(service, node)) return collect(node); }
                    finally { node.recycle(); }
                }
            }
            return null;
        } finally { for (AccessibilityWindowInfo w : windows) w.recycle(); }
    }

    private static boolean external(AccessibilityService service, AccessibilityNodeInfo n) {
        CharSequence pkg = n.getPackageName();
        return pkg != null && !service.getPackageName().contentEquals(pkg)
                && !"com.android.systemui".contentEquals(pkg)
                && !"android".contentEquals(pkg);
    }

    private static PageText collect(AccessibilityNodeInfo root) {
        Scanner scan = new Scanner();
        scan.visit(root, 0);
        if (scan.overflow) throw new IllegalStateException("页面文字或层级过多，请单独打开当前题目");
        if (scan.buffer.length() == 0) return null;
        return new PageText(root.getPackageName().toString(), root.getWindowId(), scan.buffer.toString());
    }

    private static final class Scanner {
        private boolean overflow;
        private int visited;
        private final StringBuilder buffer = new StringBuilder();

        private void visit(AccessibilityNodeInfo node, int depth) {
            if (node.isPassword()) return;
            if (++visited > 3000 || depth > 120) { overflow = true; return; }
            if (node.isVisibleToUser()) {
                CharSequence value = node.getText(), description = node.getContentDescription();
                append(value);
                if (description != null && (value == null || !description.toString().contentEquals(value))) append(description);
            }
            for (int i = 0; !overflow && i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child == null) continue;
                try { visit(child, depth + 1); } finally { child.recycle(); }
            }
        }

        private void append(CharSequence value) {
            if (value == null) return;
            String s = value.toString().trim();
            if (s.isEmpty()) return;
            if (buffer.length() + s.length() + 1 > 24000) { overflow = true; return; }
            if (buffer.length() > 0) buffer.append('\n');
            buffer.append(s);
        }
    }
}
