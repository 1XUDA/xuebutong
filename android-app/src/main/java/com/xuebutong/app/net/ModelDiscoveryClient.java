package com.xuebutong.app.net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ModelDiscoveryClient {
    public Result discover(JSONObject config) {
        String apiKey = config.optString("apiKey").trim();
        if (apiKey.length() <= 10) {
            return Result.failure("API Key 无效");
        }

        String modelsUrl = modelsUrl(config.optString("apiUrl"), config.optString("id"));
        if (modelsUrl.isEmpty()) {
            return Result.failure("无法确定模型列表地址");
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(modelsUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "XueBuTong-Android/"+com.xuebutong.app.BuildConfig.VERSION_NAME);

            int status = connection.getResponseCode();
            String raw = read(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            if (status < 200 || status >= 300) {
                return Result.failure("HTTP " + status + errorMessage(raw));
            }

            JSONObject body = raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
            List<String> models = rankTextModels(config.optString("id"), extractModelIds(body));
            if (models.isEmpty()) {
                return Result.failure("未找到可用于文本问答的模型");
            }
            return Result.success(models);
        } catch (Exception error) {
            String message = error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage();
            return Result.failure(shorten(message, 80));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static String modelsUrl(String apiUrl, String providerId) {
        String value = apiUrl == null ? "" : apiUrl.trim();
        if (value.isEmpty()) value = defaultModelsUrl(providerId);
        if (value.isEmpty()) return "";

        int query = value.indexOf('?');
        if (query >= 0) value = value.substring(0, query);
        int fragment = value.indexOf('#');
        if (fragment >= 0) value = value.substring(0, fragment);
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);

        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.endsWith("/models")) return value;
        for (String suffix : new String[]{"/chat/completions", "/responses", "/completions"}) {
            if (lower.endsWith(suffix)) {
                return value.substring(0, value.length() - suffix.length()) + "/models";
            }
        }
        return value + "/models";
    }

    static String selectModel(String providerId, String currentModel, List<String> models) {
        List<String> usable = rankTextModels(providerId, models);
        if (usable.isEmpty()) return "";

        for (String model : usable) {
            if (!currentModel.isEmpty() && model.equalsIgnoreCase(currentModel)) return model;
        }

        return usable.get(0);
    }

    static List<String> rankTextModels(String providerId, List<String> models) {
        List<String> usable = new ArrayList<>();
        for (String model : models) {
            if (isTextModel(model)) usable.add(model);
        }
        Collections.sort(usable, (left, right) -> Integer.compare(
                score(providerId, right), score(providerId, left)));
        return usable;
    }

    private static int score(String providerId, String model) {
        String id = providerId == null ? "" : providerId.toLowerCase(Locale.ROOT);
        String name = model.toLowerCase(Locale.ROOT);
        int score = 0;

        if (name.contains("latest")) score += 80;
        if (name.contains("chat")) score += 60;
        if (name.contains("instruct")) score += 40;
        if (name.contains("preview") || name.contains("beta")) score -= 40;
        if (name.contains("vision") || name.contains("-vl")) score -= 80;
        if (name.contains("coder") || name.contains("math")) score -= 120;

        if ("deepseek".equals(id)) {
            if ("deepseek-chat".equals(name)) score += 1000;
            else if ("deepseek-reasoner".equals(name)) score += 800;
            else if (name.contains("deepseek")) score += 500;
        } else if ("qwen".equals(id)) {
            if ("qwen-max-latest".equals(name)) score += 1000;
            else if (name.contains("max")) score += 750;
            else if (name.contains("plus")) score += 600;
            else if (name.contains("turbo")) score += 400;
            if (name.contains("qwen")) score += 250;
        } else if ("zhipu".equals(id)) {
            if (name.startsWith("glm-5")) score += 1000;
            else if (name.startsWith("glm-4.5")) score += 850;
            else if (name.contains("plus")) score += 700;
            else if (name.startsWith("glm-4")) score += 550;
            if (name.contains("glm")) score += 200;
        } else if ("doubao".equals(id)) {
            if (name.contains("pro")) score += 900;
            else if (name.contains("seed")) score += 750;
            else if (name.contains("lite")) score += 400;
            if (name.contains("doubao")) score += 250;
        }
        return score;
    }

    private static boolean isTextModel(String model) {
        if (model == null || model.trim().isEmpty()) return false;
        String name = model.toLowerCase(Locale.ROOT);
        for (String excluded : new String[]{
                "embedding", "rerank", "moderation", "whisper", "speech",
                "audio", "tts", "image", "video", "ocr"
        }) {
            if (name.contains(excluded)) return false;
        }
        return true;
    }

    private static List<String> extractModelIds(JSONObject body) {
        Set<String> values = new LinkedHashSet<>();
        collect(body.optJSONArray("data"), values);
        collect(body.optJSONArray("models"), values);
        collect(body.optJSONArray("items"), values);

        JSONObject data = body.optJSONObject("data");
        if (data != null) {
            collect(data.optJSONArray("data"), values);
            collect(data.optJSONArray("models"), values);
            collect(data.optJSONArray("items"), values);
        }
        return new ArrayList<>(values);
    }

    private static void collect(JSONArray array, Set<String> values) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            Object item = array.opt(i);
            String id = "";
            if (item instanceof JSONObject) {
                JSONObject object = (JSONObject) item;
                for (String key : new String[]{"id", "model", "model_id", "modelId", "name"}) {
                    id = object.optString(key).trim();
                    if (!id.isEmpty()) break;
                }
            } else if (item instanceof String) {
                id = ((String) item).trim();
            }
            if (!id.isEmpty()) values.add(id);
        }
    }

    private static String defaultModelsUrl(String providerId) {
        if ("deepseek".equals(providerId)) return "https://api.deepseek.com/v1/models";
        if ("qwen".equals(providerId)) return "https://dashscope.aliyuncs.com/compatible-mode/v1/models";
        if ("zhipu".equals(providerId)) return "https://open.bigmodel.cn/api/paas/v4/models";
        if ("doubao".equals(providerId)) return "https://ark.cn-beijing.volces.com/api/v3/models";
        return "";
    }

    private static String errorMessage(String raw) {
        try {
            JSONObject body = new JSONObject(raw);
            JSONObject error = body.optJSONObject("error");
            String message = error == null ? "" : error.optString("message");
            if (message.isEmpty()) message = body.optString("message", body.optString("detail"));
            return message.isEmpty() ? "" : "：" + shorten(message, 80);
        } catch (Exception ignored) {
            String value = shorten(raw == null ? "" : raw.trim(), 80);
            return value.isEmpty() ? "" : "：" + value;
        }
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder value = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) value.append(line);
        }
        return value.toString();
    }

    private static String shorten(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    public static final class Result {
        public final boolean ok;
        public final List<String> models;
        public final int modelCount;
        public final String message;

        private Result(boolean ok, List<String> models, String message) {
            this.ok = ok;
            this.models = models;
            this.modelCount = models.size();
            this.message = message;
        }

        static Result success(List<String> models) {
            return new Result(true, new ArrayList<>(models), "");
        }

        static Result failure(String message) {
            return new Result(false, new ArrayList<>(),
                    message == null || message.isEmpty() ? "识别失败" : message);
        }
    }
}
