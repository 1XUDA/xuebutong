package com.xuebutong.app.bank;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BankActivity extends Activity {
    private static final int EXPORT=101, IMPORT=102, RED=0xffbc292c;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private BankStore bank;private LinearLayout list;private EditText search;private TextView count;
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(0xff232326);return t;}
    private Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setTextColor(RED);b.setAllCaps(false);b.setOnClickListener(v->action.run());return b;}
    @Override public void onCreate(Bundle b){super.onCreate(b);bank=new BankStore(this);getWindow().setStatusBarColor(0xfff8f8f8);getWindow().setNavigationBarColor(0xfff8f8f8);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);showScreen();}
    @Override public void onDestroy(){executor.shutdown();bank.close();super.onDestroy();}
    private void showScreen(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(16),dp(10),dp(16),0);root.setBackgroundColor(0xfff8f8f8);
        TextView title=text("‹    题库",22);title.setTypeface(null,Typeface.BOLD);title.setPadding(0,dp(8),0,dp(10));title.setOnClickListener(v->finish());root.addView(title);
        Switch enabled=new Switch(this);enabled.setText("优先使用题库");enabled.setTextSize(16);enabled.setChecked(BankStore.enabled(this));enabled.setOnCheckedChangeListener((x,on)->BankStore.setEnabled(this,on));root.addView(enabled);
        TextView hint=text("识别后优先参考本地题库；无法可靠匹配时由 AI 作答。",13);hint.setTextColor(0xff777777);hint.setPadding(0,dp(6),0,dp(8));root.addView(hint);
        LinearLayout actions=new LinearLayout(this);actions.addView(button("查看模板",this::showTemplate),new LinearLayout.LayoutParams(0,dp(48),1));actions.addView(button("导出模板",this::exportTemplate),new LinearLayout.LayoutParams(0,dp(48),1));actions.addView(button("导入题库",this::importWorkbook),new LinearLayout.LayoutParams(0,dp(48),1));root.addView(actions);
        LinearLayout searchRow=new LinearLayout(this);search=new EditText(this);search.setSingleLine(true);search.setHint("搜索题目");search.setTextSize(14);searchRow.addView(search,new LinearLayout.LayoutParams(0,dp(48),1));searchRow.addView(button("搜索",this::refresh),new LinearLayout.LayoutParams(dp(70),dp(48)));root.addView(searchRow);
        LinearLayout bar=new LinearLayout(this);count=text("",14);bar.addView(count,new LinearLayout.LayoutParams(0,dp(48),1));bar.addView(button("＋ 添加题目",()->edit(null)),new LinearLayout.LayoutParams(dp(120),dp(48)));root.addView(bar);
        ScrollView scroller=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(1);scroller.addView(list);root.addView(scroller,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);refresh();
    }
    private void refresh(){ArrayList<JSONObject> rows=bank.list(search.getText().toString().trim());count.setText("题目："+rows.size()+" 条");list.removeAllViews();if(rows.isEmpty()){TextView empty=text("还没有题目，可导入模板或手动添加。",14);empty.setPadding(0,dp(24),0,0);list.addView(empty);return;}for(JSONObject row:rows){TextView item=text(row.optString("type")+"  ·  "+row.optString("stem")+"\n答案："+row.optString("answer"),15);item.setPadding(dp(12),dp(14),dp(12),dp(14));item.setBackgroundColor(Color.WHITE);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(8));list.addView(item,p);item.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(row.optString("stem")).setItems(new String[]{"查看与编辑","删除"},(d,which)->{if(which==0)edit(row);else new AlertDialog.Builder(this).setMessage("确定删除这道题？").setNegativeButton("取消",null).setPositiveButton("删除",(x,y)->{bank.delete(row.optLong("_id"));refresh();}).show();}).show());}}
    private void edit(JSONObject row){String[] keys={"type","stem","a","b","c","d","answer","explanation"},labels={"题型：单选题、多选题、判断题、简答题","题目","选项 A","选项 B","选项 C","选项 D","正确答案","解析"};LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(20),0,dp(20),0);EditText[] fields=new EditText[8];for(int i=0;i<8;i++){fields[i]=new EditText(this);fields[i].setTextSize(14);fields[i].setHint(labels[i]);fields[i].setText(row==null?"":row.optString(keys[i]));body.addView(fields[i]);}ScrollView scroller=new ScrollView(this);scroller.addView(body);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(row==null?"添加题目":"编辑题目").setView(scroller).setNegativeButton("取消",null).setPositiveButton("保存",null).create();dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{String[] values=new String[8];for(int i=0;i<8;i++)values[i]=fields[i].getText().toString().trim();String error=BankStore.validate(values);if(!error.isEmpty()){Toast.makeText(this,error,Toast.LENGTH_LONG).show();return;}bank.save(row==null?0:row.optLong("_id"),values);dialog.dismiss();refresh();}));dialog.show();}
    private void showTemplate(){new AlertDialog.Builder(this).setTitle("题库模板与示例").setMessage("表头：题型 | 题目 | 选项A | 选项B | 选项C | 选项D | 正确答案 | 解析\n\n单选题示例：地球围绕什么运行？\nA 月球；B 太阳；C 火星；D 木星\n正确答案：B\n\n多选题示例：哪些是哺乳动物？\nA 鲸鱼；B 青蛙；C 蝙蝠；D 鳄鱼\n正确答案：AC\n\n判断题填写正确或错误，简答题填写答案文字。选择题至少填写 A、B，选项内容不能重复。\n\n模板包含“题库”和“填写示例”两张表，只有“题库”表会导入。请保留表头，勿使用公式。").setPositiveButton("知道了",null).show();}
    private void exportTemplate(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").putExtra(Intent.EXTRA_TITLE,"学不通-题库模板.xlsx");startActivityForResult(i,EXPORT);}
    private void importWorkbook(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");startActivityForResult(i,IMPORT);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();if(request==EXPORT){executor.execute(()->{try(InputStream in=getAssets().open("bank/template.xlsx");OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("无法写入文件");byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);runOnUiThread(()->Toast.makeText(this,"模板已导出",Toast.LENGTH_SHORT).show());}catch(Exception e){error(e);}});}else if(request==IMPORT){executor.execute(()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("无法读取文件");List<String[]> parsed=BankWorkbook.read(in);List<String[]> rows=new ArrayList<>();ArrayList<String> errors=new ArrayList<>();Set<String> seen=new HashSet<>();for(JSONObject item:bank.list(""))seen.add(BankStore.norm(item.optString("type"))+"|"+BankStore.norm(item.optString("stem")));for(int i=0;i<parsed.size();i++){String[] row=parsed.get(i);boolean empty=true;for(String cell:row)if(!cell.trim().isEmpty()){empty=false;break;}if(empty)continue;rows.add(row);String issue=BankStore.validate(row);String key=BankStore.norm(row[0])+"|"+BankStore.norm(row[1]);if(!seen.add(key))issue="题目重复或与现有题目冲突";if(!issue.isEmpty())errors.add("第 "+(i+2)+" 行："+issue);}runOnUiThread(()->preview(rows,errors));}catch(Exception e){error(e);}});}}
    private void preview(List<String[]> rows,List<String> errors){StringBuilder message=new StringBuilder("待导入 "+rows.size()+" 道题。\n");if(!errors.isEmpty()){message.append("发现 ").append(errors.size()).append(" 处问题，请修改表格后重新导入：\n");for(int i=0;i<Math.min(20,errors.size());i++)message.append(errors.get(i)).append('\n');if(errors.size()>20)message.append("其余问题请修正后再次检查。\n");}AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle("导入预览").setMessage(message.toString()).setNegativeButton("返回",null);if(errors.isEmpty()&&!rows.isEmpty())b.setPositiveButton("确认导入",(d,w)->{try{int n=bank.importRows(rows);refresh();Toast.makeText(this,"已导入 "+n+" 道题",Toast.LENGTH_LONG).show();}catch(Exception e){error(e);}});b.show();}
    private void error(Exception e){String msg=e.getMessage()==null?"请检查文件后重试":e.getMessage();runOnUiThread(()->new AlertDialog.Builder(this).setTitle("操作失败").setMessage(msg).setPositiveButton("知道了",null).show());}
}
