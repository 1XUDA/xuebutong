package com.xuebutong.app.net;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

public final class ModelDiscoveryClientTest {
    @Test
    public void derivesEachProviderModelsUrlFromItsOwnApiUrl() {
        assertEquals(
                "https://api.deepseek.com/v1/models",
                ModelDiscoveryClient.modelsUrl(
                        "https://api.deepseek.com/v1/chat/completions", "deepseek"));
        assertEquals(
                "https://dashscope.aliyuncs.com/compatible-mode/v1/models",
                ModelDiscoveryClient.modelsUrl(
                        "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "qwen"));
        assertEquals(
                "https://open.bigmodel.cn/api/paas/v4/models",
                ModelDiscoveryClient.modelsUrl(
                        "https://open.bigmodel.cn/api/paas/v4/chat/completions", "zhipu"));
        assertEquals(
                "https://ark.cn-beijing.volces.com/api/v3/models",
                ModelDiscoveryClient.modelsUrl(
                        "https://ark.cn-beijing.volces.com/api/v3/responses", "doubao"));
    }

    @Test
    public void deepSeekPrefersChatForFastQuestionAnswering() {
        assertEquals(
                "deepseek-chat",
                ModelDiscoveryClient.selectModel("deepseek", "", Arrays.asList(
                        "deepseek-embedding", "deepseek-reasoner", "deepseek-chat")));
    }

    @Test
    public void qwenPrefersLatestMaxTextModel() {
        assertEquals(
                "qwen-max-latest",
                ModelDiscoveryClient.selectModel("qwen", "", Arrays.asList(
                        "text-embedding-v3", "qwen-turbo-latest", "qwen-plus-latest", "qwen-max-latest")));
    }

    @Test
    public void zhipuPrefersLatestGeneralGlmModel() {
        assertEquals(
                "glm-5.1",
                ModelDiscoveryClient.selectModel("zhipu", "", Arrays.asList(
                        "embedding-3", "glm-4-flash", "glm-4.5", "glm-5.1")));
    }

    @Test
    public void keepsAConfiguredModelWhenProviderStillReturnsIt() {
        assertEquals(
                "qwen-plus-latest",
                ModelDiscoveryClient.selectModel("qwen", "qwen-plus-latest", Arrays.asList(
                        "qwen-plus-latest", "qwen-max-latest")));
    }
}
