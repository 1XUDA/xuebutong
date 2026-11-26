package com.xuebutong.app.answer;

import org.json.JSONObject;

/** 只在返回值符合约定时拆分答案和解析，否则完整保留原文。 */
public final class AnswerFormatter {
    public static final String DEFAULT_TEMPLATE="{模型名称}\n{题目}\n【答案】{答案}\n【解析】{解析}\n来源：{答案来源}";
    public static final String FORMAT_INSTRUCTION="请以 JSON 对象返回，字段 answer 为答案字符串，analysis 为解析字符串。若用户要求只给答案，analysis 填空字符串。不要输出 JSON 以外的文字；无法确定时如实写在 answer 中。";
    public static final class Parts {
        public String answer,analysis,source,question;
        public boolean structured;
    }
    public static Parts parse(String value){
        Parts p=new Parts();String raw=value==null?"":value.trim();p.source="";p.analysis="";p.question="";
        if(raw.startsWith("【参考题库】")){p.source="参考题库";raw=raw.substring("【参考题库】".length()).trim();}
        else if(raw.startsWith("【AI 作答】")){p.source="AI 作答";raw=raw.substring("【AI 作答】".length()).trim();}
        String json=raw.replaceFirst("(?is)^```(?:json)?\\s*","").replaceFirst("\\s*```$","");
        try{org.json.JSONTokener tokener=new org.json.JSONTokener(json);Object parsed=tokener.nextValue();if(!(parsed instanceof JSONObject)||tokener.nextClean()!=0)throw new IllegalArgumentException();JSONObject o=(JSONObject)parsed;Object a=o.opt("answer"),b=o.opt("analysis");if(a instanceof String&&b instanceof String&&!((String)a).trim().isEmpty()){p.answer=((String)a).trim();p.analysis=((String)b).trim();Object q=o.opt("question");if(q instanceof String)p.question=((String)q).trim();p.structured=true;return p;}}catch(Exception ignored){}
        p.answer=raw;return p;
    }
    public static String render(String template,String question,String model,String raw,boolean showQuestion,boolean showModel,boolean showSource,boolean showAnalysis){
        Parts p=parse(raw);String[] names={"{题目}","{模型名称}","{答案}","{解析}","{答案来源}"};
        String[] values={showQuestion?question:"",showModel?model:"",p.answer,showAnalysis?p.analysis:"",showSource?p.source:""};
        StringBuilder out=new StringBuilder();
        for(String line:template.split("\\n",-1)){boolean has=false,nonempty=false;java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("\\{(?:题目|模型名称|答案|解析|答案来源)\\}").matcher(line);StringBuffer rendered=new StringBuffer();while(matcher.find()){has=true;String replacement="";for(int i=0;i<names.length;i++)if(names[i].equals(matcher.group())){replacement=values[i]==null?"":values[i];break;}if(!replacement.isEmpty())nonempty=true;matcher.appendReplacement(rendered,java.util.regex.Matcher.quoteReplacement(replacement));}matcher.appendTail(rendered);if(has&&!nonempty)continue;if(out.length()>0)out.append('\n');out.append(rendered);}
        String result=out.toString().trim();
        // 用户模板意外丢失答案字段时，不能丢掉模型原文。
        if(!template.contains("{答案}"))result=(result.isEmpty()?"":result+"\n")+p.answer;
        return result;
    }
    public static String history(String raw){Parts p=parse(raw);return (p.source.isEmpty()?"":"【"+p.source+"】\n")+p.answer+(p.structured&&!p.analysis.isEmpty()?"\n解析："+p.analysis:"");}
}
