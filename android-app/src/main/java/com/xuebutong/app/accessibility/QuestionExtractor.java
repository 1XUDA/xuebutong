package com.xuebutong.app.accessibility;

import android.text.TextUtils;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class QuestionExtractor {
    private static final int MAX_DEPTH = 40;
    private static final int MAX_LENGTH = 1200;
    private static final Pattern NOISE = Pattern.compile(
            "^(下一题|上一题|提交|确定|取消|返回|下一章|答题卡|交卷|重做|收藏|纠错|笔记|计算器|设置|清空)$"
                    + "|^\\d{1,2}:\\d{2}(:\\d{2})?$"
                    + "|^第\\s*\\d+\\s*[/／]\\s*\\d+\\s*题$"
                    + "|^\\d+(\\.\\d+)?\\s*分$"
                    + "|^剩余.*|^答题时间.*|^共\\s*\\d+\\s*题$"
                    + "|^(单选题|多选题|判断题|填空题|简答题|材料题)\\s*[（(].*"
                    + "|^\\S+[（(]\\d{6,}[）)].*");

    private QuestionExtractor() {
    }

    static String extract(AccessibilityNodeInfo root) {
        if (root == null) {
            return "";
        }
        CharSequence packageName = root.getPackageName();
        if (packageName != null && packageName.toString().startsWith("com.xuebutong.app")) {
            return "";
        }
        Set<String> unique = new LinkedHashSet<>();
        collect(root, 0, unique);
        List<String> parts = new ArrayList<>(unique);
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (result.length() + part.length() + 1 > MAX_LENGTH) {
                break;
            }
            if (result.length() > 0) {
                result.append('\n');
            }
            result.append(part);
        }
        return clean(result.toString());
    }

    private static void collect(AccessibilityNodeInfo node, int depth, Set<String> output) {
        if (node == null || depth > MAX_DEPTH) {
            return;
        }
        add(node.getText(), output);
        add(node.getContentDescription(), output);
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            collect(child, depth + 1, output);
            if (child != null) {
                child.recycle();
            }
        }
    }

    private static void add(CharSequence value, Set<String> output) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        String text = value.toString().replaceAll("\\s+", " ").trim();
        if (text.length() == 1 && !text.matches("[A-Ha-h1-9①-⑧]")) {
            return;
        }
        if (NOISE.matcher(text).matches()) {
            return;
        }
        output.add(text);
    }

    private static String clean(String value) {
        return value
                .replaceFirst("^\\s*\\d+\\.\\s*[（(]\\s*\\d+(\\.\\d+)?\\s*分\\s*[）)]\\s*", "")
                .replaceFirst("^\\s*\\d+[.、\\s]+", "")
                .replaceAll("([A-H])([一-龥])", "$1. $2")
                .replaceAll("[ \\t]{2,}", " ")
                .trim();
    }
}
