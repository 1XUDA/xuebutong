package com.xuebutong.app.update;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import org.json.JSONObject;

public final class UpdateActivity extends Activity {
    private static final int RED=0xffbc292c, TEXT=0xff242426, MUTED=0xff68696d;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SharedPreferences state;
    private TextView version,detail,message,progressText,notes;
    private LinearLayout progressCard;
    private ProgressBar progress;
    private Button download,install,check;
    private final Runnable refresh=new Runnable(){@Override public void run(){render();handler.postDelayed(this,1200);}};
    @Override public void onCreate(Bundle b){
        super.onCreate(b);state=UpdateManager.prefs(this);
        getWindow().setStatusBarColor(Color.WHITE);getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xfff7f7f7);
        LinearLayout root=column();root.setPadding(dp(14),dp(12),dp(14),dp(28));scroll.addView(root);
        TextView back=label("‹   软件更新",22,TEXT);back.setTypeface(null,Typeface.BOLD);
        back.setOnClickListener(v->finish());root.addView(back);
        LinearLayout top=card();LinearLayout identity=new LinearLayout(this);identity.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(this);logo.setImageResource(com.xuebutong.app.R.drawable.book_mark);
        identity.addView(logo,new LinearLayout.LayoutParams(dp(64),dp(64)));
        LinearLayout identityText=column();identityText.setPadding(dp(14),0,0,0);
        TextView brand=label("学不通",15,TEXT);brand.setTypeface(null,Typeface.BOLD);identityText.addView(brand);
        version=label("",25,TEXT);version.setTypeface(null,Typeface.BOLD);identityText.addView(version);
        identity.addView(identityText);top.addView(identity);
        detail=label("",12,MUTED);identityText.addView(detail);root.addView(top,space());
        LinearLayout changes=card();TextView changeTitle=label("更新内容",17,TEXT);changeTitle.setTypeface(null,Typeface.BOLD);changes.addView(changeTitle);notes=label("•  界面布局与模型配置优化\n•  支持检查和下载更新",13,TEXT);notes.setLineSpacing(dp(4),1f);changes.addView(notes,space());root.addView(changes,space());
        LinearLayout options=card();
        addSwitch(options,"自动检查更新","打开应用时定期检查新版本","auto_check",true);
        addSwitch(options,"自动下载更新","发现新版本后自动下载更新包","auto_download",false);
        addSwitch(options,"仅在 Wi-Fi 下载","避免使用移动数据下载","wifi_only",true);
        root.addView(options,space());
        progressCard=card();TextView progressTitle=label("更新下载",16,TEXT);progressTitle.setTypeface(null,Typeface.BOLD);progressCard.addView(progressTitle);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progressCard.addView(progress,space());
        progressText=label("",13,MUTED);progressCard.addView(progressText,space());progressCard.setVisibility(View.GONE);root.addView(progressCard,space());
        message=label("",14,MUTED);root.addView(message,space());
        check=button("检查更新",false);download=button("下载更新",true);install=button("安装更新",true);
        root.addView(check,space());root.addView(download,space());root.addView(install,space());
        check.setOnClickListener(v->{check.setEnabled(false);message.setText("正在检查更新…");UpdateManager.check(this,true,result->{check.setEnabled(true);message.setText(result);render();});});
        download.setOnClickListener(v->{
            try {
                JSONObject info=new JSONObject(state.getString("available",""));
                long id=state.getLong("download_id",-1);
                if(id!=-1){
                    DownloadManager dm=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
                    try(Cursor c=dm.query(new DownloadManager.Query().setFilterById(id))){
                        if(c==null||!c.moveToFirst()||c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))==DownloadManager.STATUS_FAILED){
                            dm.remove(id);state.edit().remove("download_id").commit();
                        }
                    }
                }
                UpdateManager.startDownload(this,info);message.setText("下载已加入系统下载队列");render();
            }catch(Exception e){message.setText("无法开始下载，请检查网络或稍后重试");}
        });
        install.setOnClickListener(v->new Thread(()->{
            try {
                UpdateManager.verifiedFile(this);
                runOnUiThread(()->{
                    if(isFinishing())return;
                    if(Build.VERSION.SDK_INT>=26 && !getPackageManager().canRequestPackageInstalls()){
                        message.setText("请允许学不通安装更新，返回后再次点击安装");
                        startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));return;
                    }
                    Uri uri=Uri.parse("content://"+getPackageName()+".updates/apk");
                    Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try{startActivity(intent);}catch(Exception e){message.setText("无法打开系统安装界面");}
                });
            }catch(Exception e){runOnUiThread(()->message.setText(e.getMessage()==null?"安装包核验失败":e.getMessage()));}
        },"verify-apk").start());
        setContentView(scroll);render();
    }
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    private void render(){
        try {
            String current=getPackageManager().getPackageInfo(getPackageName(),0).versionName;
            String raw=state.getString("available","");
            version.setText(current);
            if(raw.isEmpty()){detail.setText("当前版本 · 可手动检查更新");progressCard.setVisibility(View.GONE);download.setVisibility(View.GONE);install.setVisibility(View.GONE);return;}
            JSONObject j=new JSONObject(raw);version.setText(j.optString("versionName"));detail.setText("当前版本："+current);notes.setText(j.optString("notes","发现新版本"));
            long id=state.getLong("download_id",-1);
            progressCard.setVisibility(View.GONE);download.setVisibility(View.VISIBLE);install.setVisibility(View.GONE);download.setText("下载更新");
            if(id<0||state.getInt("download_version",-1)!=j.optInt("versionCode"))return;
            DownloadManager dm=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
            try(Cursor c=dm.query(new DownloadManager.Query().setFilterById(id))){
                if(c==null||!c.moveToFirst())return;
                int st=c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                if(st==DownloadManager.STATUS_SUCCESSFUL){download.setVisibility(View.GONE);install.setVisibility(View.VISIBLE);return;}
                if(st==DownloadManager.STATUS_FAILED){download.setText("重新下载");return;}
                long done=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                long total=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                progressCard.setVisibility(View.VISIBLE);
                if(total>0){progress.setIndeterminate(false);progress.setProgress((int)(done*100/total));progressText.setText(String.format(java.util.Locale.CHINA,"%.1f MB / %.1f MB",done/1048576f,total/1048576f));}
                else{progress.setIndeterminate(true);progressText.setText("正在准备下载");}
                download.setText(st==DownloadManager.STATUS_PAUSED?"等待网络":total>0?"正在下载 "+(done*100/total)+"%":"正在准备下载");
                download.setEnabled(false);return;
            }
        }catch(Exception ignored){}
        finally{if(download!=null && !download.getText().toString().startsWith("正在") && !download.getText().toString().startsWith("等待"))download.setEnabled(true);}
    }
    private void addSwitch(LinearLayout parent,String title,String description,String key,boolean def){
        LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(0,dp(6),0,dp(6));
        ImageView icon=new ImageView(this);icon.setImageResource(key.equals("auto_check")?com.xuebutong.app.R.drawable.ui_search:com.xuebutong.app.R.drawable.ui_download);line.addView(icon,new LinearLayout.LayoutParams(dp(24),dp(24)));
        LinearLayout copy=column();copy.setPadding(dp(14),0,0,0);TextView heading=label(title,15,TEXT);heading.setTypeface(null,Typeface.BOLD);copy.addView(heading);copy.addView(label(description,11,MUTED));line.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        Switch sw=new Switch(this);sw.setChecked(state.getBoolean(key,def));line.addView(sw);sw.setOnCheckedChangeListener((v,on)->{state.edit().putBoolean(key,on).apply();if(on && "auto_download".equals(key) && !state.getString("available","").isEmpty())new Thread(()->{try{UpdateManager.startDownload(this,new JSONObject(state.getString("available","")));}catch(Exception ignored){}},"auto-download").start();});parent.addView(line);
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout card(){LinearLayout l=column();l.setPadding(dp(18),dp(14),dp(18),dp(14));l.setBackground(background(Color.WHITE,16));return l;}
    private GradientDrawable background(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private LinearLayout.LayoutParams space(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(14);return p;}
    private TextView label(String text,int size,int color){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);return v;}
    private Button button(String title,boolean primary){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setMinHeight(dp(50));b.setTextColor(primary?Color.WHITE:RED);b.setBackground(background(primary?RED:Color.WHITE,14));return b;}
}
