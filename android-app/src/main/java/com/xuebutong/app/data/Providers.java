package com.xuebutong.app.data;

import org.json.JSONArray;
import org.json.JSONObject;

public final class Providers {
    public final String id, name, url, text, vision;
    private Providers(String id,String name,String url,String text,String vision) {
        this.id=id; this.name=name; this.url=url; this.text=text; this.vision=vision;
    }
    public static final Providers[] ALL={
        new Providers("openai","OpenAI","https://api.openai.com/v1/chat/completions","gpt-4o","gpt-4o"),
        new Providers("qwen","阿里云百炼 · 千问","https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions","qwen-plus","qwen3-vl-plus"),
        new Providers("deepseek","DeepSeek","https://api.deepseek.com/v1/chat/completions","deepseek-flash",""),
        new Providers("zhipu","智谱开放平台","https://open.bigmodel.cn/api/paas/v4/chat/completions","glm-4.6v-flash","glm-4.6v-flash"),
        new Providers("doubao","火山方舟 · 豆包","https://ark.cn-beijing.volces.com/api/v3/responses","doubao-seed-2-1-lite-260915","doubao-seed-2-1-lite-260915"),
        new Providers("moonshot","月之暗面 · Kimi","https://api.moonshot.cn/v1/chat/completions","kimi-k3.1-pro","kimi-k3.1-pro"),
        new Providers("siliconflow","硅基流动","https://api.siliconflow.cn/v1/chat/completions","Qwen/Qwen3-VL-32B-Instruct","Qwen/Qwen3-VL-32B-Instruct"),
        new Providers("hunyuan","腾讯混元","https://api.hunyuan.cloud.tencent.com/v1/chat/completions","hunyuan-turbos-latest","hunyuan-vision-latest")
    };
    public static Providers get(String id){
        for(Providers p:ALL) if(p.id.equals(id))return p;
        return new Providers(id,"自定义兼容接口","","","");
    }
    public static JSONObject find(JSONArray rows,String id){
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);
            if(row!=null && id.equals(row.optString("id")))return row;
        }
        return null;
    }
    public static String visionId(JSONObject m){
        return m.optString("visionModel",get(m.optString("id")).vision).trim();
    }
    public static JSONObject vision(JSONArray rows){
        JSONObject first=null;
        for(int i=0;i<rows.length();i++){
            JSONObject m=rows.optJSONObject(i);
            if(!AppStore.isModelReady(m)||visionId(m).isEmpty())continue;
            if(m.optBoolean("preferVision"))return m;
            if(first==null)first=m;
        }
        return first;
    }
    public static String label(JSONArray rows,String id){
        JSONObject m=find(rows,id);return m==null?id:m.optString("name",id);
    }
}
