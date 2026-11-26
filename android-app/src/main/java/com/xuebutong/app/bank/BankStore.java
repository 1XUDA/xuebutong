package com.xuebutong.app.bank;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.Normalizer;
import java.util.*;

/** 本机题库。只在题干和完整选项均可严格核对时提供参考。 */
public final class BankStore extends SQLiteOpenHelper {
    public BankStore(Context c){super(c,"question-bank.db",null,1);}
    public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE questions (_id INTEGER PRIMARY KEY, type TEXT NOT NULL, stem TEXT NOT NULL, a TEXT NOT NULL, b TEXT NOT NULL, c TEXT NOT NULL, d TEXT NOT NULL, answer TEXT NOT NULL, explanation TEXT NOT NULL)");}
    public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){}
    public static boolean enabled(Context c){return c.getSharedPreferences("question_bank",0).getBoolean("enabled",false);}
    public static void setEnabled(Context c,boolean value){c.getSharedPreferences("question_bank",0).edit().putBoolean("enabled",value).apply();}
    public static String norm(String s){return Normalizer.normalize(s==null?"":s,Normalizer.Form.NFKC).replaceAll("[\\s\\u200b\\ufeff]+", "").toLowerCase(Locale.ROOT);}
    public static String validate(String[] v){
        if(v.length!=8)return "字段数量不正确";
        for(String s:v)if(s.length()>4000)return "单个字段超过 4000 字";
        if(v[0].trim().isEmpty()||v[1].trim().isEmpty()||v[6].trim().isEmpty())return "题型、题目和答案不能为空";
        String type=v[0].trim();
        if(!Arrays.asList("单选题","多选题","判断题","简答题").contains(type))return "题型应为单选题、多选题、判断题或简答题";
        if(type.endsWith("选题")){
            if(v[2].trim().isEmpty()||v[3].trim().isEmpty())return "选择题至少填写 A、B 两个选项";
            HashSet<String> options=new HashSet<>();for(int i=2;i<6;i++)if(!v[i].trim().isEmpty()&&!options.add(norm(v[i])))return "选项内容重复";
            String answer=v[6].toUpperCase(Locale.ROOT).replaceAll("[\\s、,，;；]","");
            if(!answer.matches("[A-D]+"))return "选择题答案请填写选项字母，例如 A 或 AC";
            HashSet<Character> chars=new HashSet<>();for(char ch:answer.toCharArray())if(!chars.add(ch)||v[2+ch-'A'].trim().isEmpty())return "答案字母重复或对应选项为空";
            if(type.equals("单选题")&&chars.size()!=1)return "单选题只能填写一个答案";
        }
        return "";
    }
    private ContentValues values(String[] row){ContentValues v=new ContentValues();String[] names={"type","stem","a","b","c","d","answer","explanation"};for(int i=0;i<8;i++)v.put(names[i],row[i].trim());return v;}
    public long save(long id,String[] row){String err=validate(row);if(!err.isEmpty())throw new IllegalArgumentException(err);SQLiteDatabase db=getWritableDatabase();if(id>0){db.update("questions",values(row),"_id=?",new String[]{String.valueOf(id)});return id;}return db.insertOrThrow("questions",null,values(row));}
    public void delete(long id){getWritableDatabase().delete("questions","_id=?",new String[]{String.valueOf(id)});}
    public ArrayList<JSONObject> list(String query){ArrayList<JSONObject> result=new ArrayList<>();try(Cursor c=getReadableDatabase().query("questions",null,query.isEmpty()?null:"stem LIKE ?",query.isEmpty()?null:new String[]{"%"+query+"%"},null,null,"_id DESC")){while(c.moveToNext()){JSONObject x=new JSONObject();for(int i=0;i<c.getColumnCount();i++)try{x.put(c.getColumnName(i),c.getString(i));}catch(Exception ignored){}result.add(x);}}return result;}
    public int importRows(List<String[]> rows){SQLiteDatabase db=getWritableDatabase();int count=0;db.beginTransaction();try{for(String[] row:rows){save(0,row);count++;}db.setTransactionSuccessful();}finally{db.endTransaction();}return count;}
    /** 仅接受含完整题干、全部选项且唯一的候选；冲突时放弃。 */
    public String match(String question){
        String source=norm(question);JSONObject found=null;
        for(JSONObject q:list("")){
            String stem=norm(q.optString("stem"));if(stem.length()<4)continue;
            String type=q.optString("type");
            if(type.endsWith("选题")){
                // 对当前题目解析 A-D，必须与题库的选项集合完全相同。
                java.util.regex.Matcher first=java.util.regex.Pattern.compile("(?m)(?:^|\\n)\\s*[A-DＡ-Ｄ][.．、:：)）]").matcher(question);
                if(!first.find()||!norm(question.substring(0,first.start())).equals(stem))continue;
                Map<String,String> current=parseOptions(question);Set<String> expected=new HashSet<>();
                for(String key:new String[]{"a","b","c","d"}){String o=norm(q.optString(key));if(!o.isEmpty())expected.add(o);}
                if(current.size()!=expected.size()||!new HashSet<>(current.values()).equals(expected))continue;
            }else if(!source.equals(stem))continue;
            if(found!=null)return null;found=q;
        }
        if(found==null)return null;
        String answer=found.optString("answer");String type=found.optString("type");
        if(type.endsWith("选题")){
            Map<String,String> current=parseOptions(question);StringBuilder mapped=new StringBuilder();String letters=answer.toUpperCase(Locale.ROOT).replaceAll("[\\s、,，;；]","");
            for(char ch:letters.toCharArray()){String target=norm(found.optString(new String[]{"a","b","c","d"}[ch-'A']));for(Map.Entry<String,String> e:current.entrySet())if(e.getValue().equals(target))mapped.append(e.getKey());}
            if(mapped.length()!=letters.length())return null;answer=mapped.toString();
        }
        return "题库参考答案："+answer+(found.optString("explanation").isEmpty()?"":"\n题库解析："+found.optString("explanation"));
    }
    private static Map<String,String> parseOptions(String text){
        LinkedHashMap<String,String> out=new LinkedHashMap<>();
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?m)(?:^|\\n)\\s*([A-DＡ-Ｄ])[.．、:：)）]\\s*([^\\r\\n]+)").matcher(text);
        while(m.find()){String key=Normalizer.normalize(m.group(1),Normalizer.Form.NFKC);if(out.containsKey(key))return Collections.emptyMap();out.put(key,norm(m.group(2)));}return out;
    }
}
