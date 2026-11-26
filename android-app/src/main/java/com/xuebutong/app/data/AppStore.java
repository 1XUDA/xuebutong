package com.xuebutong.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class AppStore {
    public static final String DEFAULT_PROMPT = "请直接给出题目答案。如果是选择题，只返回选项字母（例如A、B、C、D）；如果是填空题，输出完整的答案内容。不要有任何其他解释或文字。";
    private static final String FILE = "xuebutong_v5";
    private static final int HISTORY_MAX = 50;

    private final SharedPreferences prefs;

    public AppStore(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        ensureModels();
        disableModelsByDefaultOnce();
        requireDiscoveryBeforeSelectionOnce();
    }

    public JSONArray models() {
        try {
            return new JSONArray(prefs.getString("models", "[]"));
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    public void saveModels(JSONArray models) {
        prefs.edit().putString("models", models.toString()).apply();
    }

    public int enabledModelCount() {
        JSONArray models = models();
        int count = 0;
        for (int i = 0; i < models.length(); i++) {
            JSONObject model = models.optJSONObject(i);
            if (isModelReady(model)) count++;
        }
        return count;
    }

    public static String captureMode(Context ctx) {
        SharedPreferences prefs=ctx.getSharedPreferences("question_capture",Context.MODE_PRIVATE);
        return prefs.getString("mode",prefs.getBoolean("screen",false)?"screen":"learning");
    }

    public static boolean isModelReady(JSONObject model) {
        if(model!=null && model.optBoolean("directConfig"))
            return model.optBoolean("enabled") && !model.optString("apiKey").trim().isEmpty()
                && !model.optString("model").trim().isEmpty() && !model.optString("apiUrl").trim().isEmpty();
        if (model == null || !model.optBoolean("enabled") || !model.optBoolean("autoDetected")
                || model.optString("apiKey").length() <= 10
                || model.optString("apiUrl").isEmpty()
                || model.optString("model").isEmpty()) return false;
        JSONArray detected = model.optJSONArray("detectedModels");
        if (detected == null) return false;
        for (int i = 0; i < detected.length(); i++) {
            if (model.optString("model").equals(detected.optString(i))) return true;
        }
        return false;
    }

    public String prompt() {
        return prefs.getString("prompt", DEFAULT_PROMPT);
    }

    public void savePrompt(String prompt) {
        prefs.edit().putString("prompt", prompt).apply();
    }

    public String windowMode() {
        return prefs.getString("windowMode", "small");
    }

    public void toggleWindowMode() {
        prefs.edit().putString("windowMode", "small".equals(windowMode()) ? "large" : "small").apply();
    }

    public void saveWindowMode(String mode) {
        prefs.edit().putString("windowMode", "large".equals(mode) ? "large" : "small").apply();
    }

    public void resetAnswerDisplay() {
        prefs.edit().putString("windowMode","small").putString("answerTextColor","#D4AF37").putInt("answerAlpha",85)
                .remove("answerSmallPosX").remove("answerSmallPosY").remove("answerLargePosX").remove("answerLargePosY").apply();
    }

    public int intSetting(String key, int fallback) {
        return prefs.getInt(key, fallback);
    }

    public boolean boolSetting(String key, boolean fallback) {
        return prefs.getBoolean(key, fallback);
    }

    public String stringSetting(String key, String fallback) {
        return prefs.getString(key, fallback);
    }

    public void saveAppearance(int floatAlpha, String floatColor, int floatSize,
                               int answerAlpha, String answerTextColor,
                               int vibrationDuration, boolean vibrationEnabled) {
        prefs.edit()
                .putInt("floatAlpha", floatAlpha)
                .putString("floatColor", floatColor)
                .putInt("floatSize", floatSize)
                .putInt("answerAlpha", answerAlpha)
                .putString("answerTextColor", answerTextColor)
                .putInt("vibrationDuration", vibrationDuration)
                .putBoolean("vibrationEnabled", vibrationEnabled)
                .apply();
    }

    public void saveBubblePosition(int x, int y) {
        prefs.edit().putInt("floatPosX", x).putInt("floatPosY", y).apply();
    }

    public void saveAnswerPosition(String mode, int x, int y) {
        String prefix = "large".equals(mode) ? "answerLarge" : "answerSmall";
        prefs.edit().putInt(prefix + "PosX", x).putInt(prefix + "PosY", y).apply();
    }

    public void addHistory(String question, JSONObject answers) {
        String trimmedQuestion = question == null ? "" : question.trim();
        String storedQuestion = trimmedQuestion.length() > 200
                ? trimmedQuestion.substring(0, 200) : trimmedQuestion;
        JSONArray current = history();
        JSONArray history = new JSONArray();
        for (int i = 0; i < current.length(); i++) {
            JSONObject existing = current.optJSONObject(i);
            if (existing == null || storedQuestion.equals(existing.optString("q").trim())) continue;
            history.put(existing);
        }
        JSONObject item = new JSONObject();
        try {
            item.put("q", storedQuestion);
            item.put("a", answers);
            item.put("timestamp", System.currentTimeMillis());
            item.put("time", java.text.DateFormat.getDateTimeInstance().format(new java.util.Date()));
            history.put(item);
            while (history.length() > HISTORY_MAX) {
                JSONArray trimmed = new JSONArray();
                for (int i = 1; i < history.length(); i++) trimmed.put(history.opt(i));
                history = trimmed;
            }
            prefs.edit().putString("history", history.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    public boolean deleteHistoryAt(int index) {
        JSONArray current = history();
        if (index < 0 || index >= current.length()) return false;
        JSONArray next = new JSONArray();
        for (int i = 0; i < current.length(); i++) {
            if (i != index) next.put(current.opt(i));
        }
        prefs.edit().putString("history", next.toString()).apply();
        return true;
    }

    public JSONArray history() {
        try {
            return new JSONArray(prefs.getString("history", "[]"));
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    public void clearHistory() {
        prefs.edit().remove("history").apply();
    }

    private void ensureModels() {
        if (prefs.contains("models")) return;
        JSONArray defaults = new JSONArray();
        defaults.put(model("deepseek", "DeepSeek", false,
                "https://api.deepseek.com/v1/chat/completions", ""));
        defaults.put(model("qwen", "千问", false,
                "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", ""));
        defaults.put(model("zhipu", "智谱", false,
                "https://open.bigmodel.cn/api/paas/v4/chat/completions", ""));
        defaults.put(model("doubao", "豆包2.0 Pro", false,
                "https://ark.cn-beijing.volces.com/api/v3/responses", ""));
        saveModels(defaults);
    }

    private void disableModelsByDefaultOnce() {
        if (prefs.getBoolean("models_default_disabled_v46", false)) return;
        JSONArray models = models();
        for (int i = 0; i < models.length(); i++) {
            JSONObject model = models.optJSONObject(i);
            if (model == null) continue;
            try {
                model.put("enabled", false);
            } catch (JSONException ignored) {
            }
        }
        prefs.edit()
                .putString("models", models.toString())
                .putBoolean("models_default_disabled_v46", true)
                .apply();
    }

    private void requireDiscoveryBeforeSelectionOnce() {
        if (prefs.getBoolean("models_require_discovery_v47", false)) return;
        JSONArray models = models();
        for (int i = 0; i < models.length(); i++) {
            JSONObject model = models.optJSONObject(i);
            if (model == null) continue;
            try {
                model.put("enabled", false);
                model.put("model", "");
                model.put("autoDetected", false);
                model.remove("detectedModelCount");
                model.remove("detectedModels");
                model.remove("detectedAt");
            } catch (JSONException ignored) {
            }
        }
        prefs.edit()
                .putString("models", models.toString())
                .putBoolean("models_require_discovery_v47", true)
                .apply();
    }

    private JSONObject model(String id, String name, boolean enabled, String url, String model) {
        JSONObject value = new JSONObject();
        try {
            value.put("id", id);
            value.put("name", name);
            value.put("enabled", enabled);
            value.put("apiKey", "");
            value.put("apiUrl", url);
            value.put("model", model);
        } catch (JSONException ignored) {
        }
        return value;
    }
}
