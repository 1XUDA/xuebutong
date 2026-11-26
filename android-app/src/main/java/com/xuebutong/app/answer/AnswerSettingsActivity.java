package com.xuebutong.app.answer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import com.xuebutong.app.data.AppStore;
import com.xuebutong.app.overlay.OverlayService;

public final class AnswerSettingsActivity extends Activity {
    public static final String PREFS="answer_display";
    private SharedPreferences prefs;private AppStore store;private LinearLayout body;private TextView preview;
    private Spinner mode,layout,timeout,copy,preset;private SeekBar width,font,spacing,opacity;
    private CheckBox showQuestion,showModel,showSource,showAnalysis;private EditText background,color,template;
    private final int[] times={0,10,30,60};
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(0xff242428);return t;}
    private void heading(String name){TextView t=text(name,16);t.setTypeface(null,Typeface.BOLD);t.setPadding(0,dp(16),0,dp(6));body.addView(t);}
    private Spinner choice(String label,String[] options,int selected){body.addView(text(label,14));Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options);s.setAdapter(a);s.setSelection(selected);body.addView(s);return s;}
    private SeekBar slider(String label,int min,int max,int value){TextView t=text("",14);body.addView(t);SeekBar bar=new SeekBar(this);bar.setMax(max-min);bar.setProgress(Math.max(0,Math.min(max-min,value-min)));body.addView(bar);Runnable labelUpdate=()->t.setText(label+"："+(bar.getProgress()+min));labelUpdate.run();bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean user){labelUpdate.run();updatePreview();}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});return bar;}
    private EditText field(String label,String value){body.addView(text(label,14));EditText e=new EditText(this);e.setSingleLine(true);e.setText(value);e.setTextSize(14);body.addView(e);return e;}
    private CheckBox check(String label,boolean checked){CheckBox c=new CheckBox(this);c.setText(label);c.setChecked(checked);body.addView(c);c.setOnCheckedChangeListener((v,b)->updatePreview());return c;}
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(0xfff8f8f8);getWindow().setNavigationBarColor(0xfff8f8f8);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);prefs=getSharedPreferences(PREFS,0);store=new AppStore(this);build();}
    @Override protected void onResume(){super.onResume();if(OverlayService.isRunning()){Intent i=new Intent(this,OverlayService.class);i.setAction(OverlayService.ACTION_HIDE_ANSWER_SETTINGS);startService(i);}}
    @Override protected void onPause(){notifyOverlay();super.onPause();}
    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(0xfff8f8f8);TextView title=text("‹    答案显示设置",21);title.setTypeface(null,Typeface.BOLD);title.setPadding(dp(18),dp(12),0,dp(12));title.setOnClickListener(v->finish());root.addView(title);
        LinearLayout previewBox=new LinearLayout(this);previewBox.setOrientation(1);previewBox.setPadding(dp(18),0,dp(18),dp(6));TextView previewTitle=text("实时预览",14);previewTitle.setTypeface(null,Typeface.BOLD);previewBox.addView(previewTitle);preview=text("",14);preview.setPadding(dp(14),dp(8),dp(14),dp(8));preview.setMaxLines(5);preview.setEllipsize(android.text.TextUtils.TruncateAt.END);previewBox.addView(preview,new LinearLayout.LayoutParams(-1,-2));root.addView(previewBox);
        ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(18),0,dp(18),dp(24));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        heading("窗口外观");mode=choice("窗口样式",new String[]{"小窗","大窗"},"large".equals(store.windowMode())?1:0);
        width=slider("窗口宽度（屏幕百分比）",40,100,prefs.getInt("width",80));TextView minWidthHint=text("为显示操作按钮，实际窗口最窄约 210 dp。",11);minWidthHint.setTextColor(0xff777777);body.addView(minWidthHint);font=slider("文字大小（sp）",10,26,prefs.getInt("font",14));spacing=slider("行间距（百分比）",100,180,prefs.getInt("spacing",115));
        background=field("背景颜色（例如 #181818）",prefs.getString("background","#181818"));opacity=slider("背景不透明度（百分比）",20,100,prefs.getInt("opacity",90));color=field("文字颜色（例如 #D4AF37）",prefs.getString("color",store.stringSetting("answerTextColor","#D4AF37")));
        heading("显示内容");showQuestion=check("显示题目",prefs.getBoolean("question",false));showModel=check("显示模型名称",prefs.getBoolean("model",true));showSource=check("显示答案来源",prefs.getBoolean("source",true));showAnalysis=check("显示解析",prefs.getBoolean("analysis",true));
        layout=choice("多模型排列",new String[]{"分段排列","并排（空间不足时自动分段）"},prefs.getBoolean("columns",false)?1:0);
        timeout=choice("显示时长",new String[]{"手动关闭","10 秒","30 秒","60 秒"},indexOf(times,prefs.getInt("timeout",0)));
        copy=choice("复制按钮",new String[]{"复制完整显示内容","只复制答案"},prefs.getBoolean("copyAnswer",false)?1:0);
        heading("答案格式");preset=choice("快捷样式",new String[]{"当前自定义","标准","简洁","学习笔记"},0);
        template=new EditText(this);template.setTextSize(14);template.setMinLines(4);template.setGravity(android.view.Gravity.TOP);template.setText(prefs.getString("template",AnswerFormatter.DEFAULT_TEMPLATE));body.addView(template);
        TextView hints=text("可用：{题目} {答案} {解析} {模型名称} {答案来源}。模板必须包含 {答案}；无内容的行会隐藏。",12);hints.setTextColor(0xff777777);body.addView(hints);
        TextView warning=text("模型未按格式返回时，始终保留完整原文；因此该情况下无法单独隐藏解析或只复制答案。",12);warning.setTextColor(0xff777777);warning.setPadding(0,dp(8),0,0);body.addView(warning);
        LinearLayout actions=new LinearLayout(this);Button reset=new Button(this);reset.setText("恢复默认");reset.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setMessage("恢复答案显示的默认设置？").setNegativeButton("取消",null).setPositiveButton("恢复",(d,w)->{prefs.edit().clear().apply();store.resetAnswerDisplay();build();}).show());actions.addView(reset,new LinearLayout.LayoutParams(0,dp(52),1));Button save=new Button(this);save.setText("保存设置");save.setTextColor(0xffbc292c);save.setOnClickListener(v->save());actions.addView(save,new LinearLayout.LayoutParams(0,dp(52),1));root.addView(actions);setContentView(root);
        TextWatcher watch=new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){updatePreview();}public void afterTextChanged(Editable e){}};background.addTextChangedListener(watch);color.addTextChangedListener(watch);template.addTextChangedListener(watch);
        preset.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int i,long id){if(i==1)template.setText(AnswerFormatter.DEFAULT_TEMPLATE);else if(i==2)template.setText("{答案}\n{答案来源}");else if(i==3)template.setText("题目：{题目}\n答案：{答案}\n解析：{解析}\n模型：{模型名称}\n来源：{答案来源}");}});
        updatePreview();preview.post(this::updatePreview);
    }
    private int indexOf(int[] a,int n){for(int i=0;i<a.length;i++)if(a[i]==n)return i;return 0;}
    private int parseColor(String value,int fallback){try{if(!value.matches("#[0-9a-fA-F]{6}"))return fallback;return Color.parseColor(value);}catch(Exception e){return fallback;}}
    private void updatePreview(){if(preview==null||template==null||width==null||font==null||spacing==null||opacity==null||background==null||color==null||showQuestion==null||showModel==null||showSource==null||showAnalysis==null)return;String sample="【参考题库】\n{\"answer\":\"B\",\"analysis\":\"根据题目条件，正确选项为 B。\"}";preview.setText(AnswerFormatter.render(template.getText().toString(),"示例题目：请选择正确选项。","示例模型",sample,showQuestion.isChecked(),showModel.isChecked(),showSource.isChecked(),showAnalysis.isChecked()));preview.setTextSize(font.getProgress()+10);preview.setLineSpacing(0,(spacing.getProgress()+100)/100f);preview.setTextColor(parseColor(color.getText().toString(),Color.WHITE));int bg=parseColor(background.getText().toString(),0xff181818);GradientDrawable d=new GradientDrawable();d.setColor(Color.argb(Math.round((opacity.getProgress()+20)*2.55f),Color.red(bg),Color.green(bg),Color.blue(bg)));d.setCornerRadius(dp(8));preview.setBackground(d);int available=body.getWidth()-body.getPaddingLeft()-body.getPaddingRight();if(available>0){android.view.ViewGroup.LayoutParams lp=preview.getLayoutParams();int screen=getResources().getDisplayMetrics().widthPixels;int actual=Math.min(screen-dp(16),Math.max(dp(210),screen*(width.getProgress()+40)/100));int desired=available*actual/screen;if(lp.width!=desired){lp.width=desired;preview.setLayoutParams(lp);}}}
    private void notifyOverlay(){if(OverlayService.isRunning()){Intent i=new Intent(this,OverlayService.class);i.setAction(OverlayService.ACTION_REFORMAT_ANSWER);startService(i);}}
    private void save(){String bg=background.getText().toString().trim(),fg=color.getText().toString().trim(),format=template.getText().toString();if(!bg.matches("#[0-9a-fA-F]{6}")||!fg.matches("#[0-9a-fA-F]{6}")){Toast.makeText(this,"颜色请填写 # 加 6 位十六进制字符",Toast.LENGTH_LONG).show();return;}if(!format.contains("{答案}")){Toast.makeText(this,"模板必须包含 {答案}",Toast.LENGTH_LONG).show();return;}if(format.length()>4000){Toast.makeText(this,"模板不能超过 4000 字",Toast.LENGTH_LONG).show();return;}prefs.edit().putInt("width",width.getProgress()+40).putInt("font",font.getProgress()+10).putInt("spacing",spacing.getProgress()+100).putString("background",bg).putString("color",fg).putInt("opacity",opacity.getProgress()+20).putBoolean("question",showQuestion.isChecked()).putBoolean("model",showModel.isChecked()).putBoolean("source",showSource.isChecked()).putBoolean("analysis",showAnalysis.isChecked()).putBoolean("columns",layout.getSelectedItemPosition()==1).putInt("timeout",times[timeout.getSelectedItemPosition()]).putBoolean("copyAnswer",copy.getSelectedItemPosition()==1).putString("template",format).apply();store.saveWindowMode(mode.getSelectedItemPosition()==1?"large":"small");Toast.makeText(this,"答案显示设置已保存",Toast.LENGTH_SHORT).show();finish();}
}
