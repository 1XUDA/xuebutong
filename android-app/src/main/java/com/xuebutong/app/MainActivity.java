package com.xuebutong.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.ComponentName;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.xuebutong.app.accessibility.QuestionAccessibilityService;
import com.xuebutong.app.data.AppStore;
import com.xuebutong.app.data.Providers;
import com.xuebutong.app.net.ModelDiscoveryClient;
import com.xuebutong.app.overlay.OverlayService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(248, 248, 248);
    private static final int SURFACE = Color.WHITE;
    private static final int INK = Color.rgb(35, 35, 38);
    private static final int MUTED = Color.rgb(107, 108, 112);
    private static final int LINE = Color.rgb(232, 232, 234);
    private static final int BLUE = Color.rgb(38, 111, 224);
    private static final int ORANGE = Color.rgb(220, 119, 36);
    private static final int GREEN = Color.rgb(24, 132, 88);
    private static final int RED = Color.rgb(188, 41, 44);

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private AppStore store;
    private Dialog authDialog;

    private TextView statusEmoji;
    private TextView statusTitle;
    private TextView statusText;
    private TextView startEmoji;
    private TextView startLabel;
    private TextView startHint;
    private TextView smallModeOption;
    private TextView largeModeOption;
    private TextView modelSummary;
    private TextView updateSummary;
    private TextView historySummary;
    private LinearLayout startButton;
    private final List<View> gatedViews = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        store = new AppStore(this);
        setContentView(buildMainScreen());
        refreshStatus();
        com.xuebutong.app.update.UpdateManager.check(this, false, result -> {
            if(updateSummary!=null && result.startsWith("发现新版本"))updateSummary.setText(result);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private TextView learningOption, screenOption, shotOption, captureHint;
    private String captureMode() { return AppStore.captureMode(this); }
    private boolean screenMode(){return !"learning".equals(captureMode());}
    private void chooseCaptureMode(String mode) {
        if (mode.equals(captureMode())) return;
        if (OverlayService.isRunning()) stopService(new Intent(this, OverlayService.class));
        getSharedPreferences("question_capture", MODE_PRIVATE).edit().putString("mode",mode).apply();
        updateCaptureModes();
        startButton.postDelayed(this::refreshStartButton, 250);
    }
    private void updateCaptureModes() {
        String mode=captureMode();
        styleCaptureOption(learningOption, "learning".equals(mode));
        styleCaptureOption(screenOption, "screen".equals(mode));
        styleCaptureOption(shotOption,"shot".equals(mode));
        if ("shot".equals(mode)) {
            JSONObject m=Providers.vision(store.models());
            captureHint.setText(m==null?"请在模型与 API 中启用支持图片的供应商。":
                "点球将当前整屏截图发给 "+m.optString("name")+" · "+Providers.visionId(m)+"，直接返回答案。");
        } else captureHint.setText(screenMode() ? "点球读取整屏文字，AI 先识题再答题。截图仅在本机处理。" : "通过无障碍服务读取文字题。");
    }
    private View buildMainScreen() {
        gatedViews.clear();
        LinearLayout shell=vertical(); shell.setBackgroundColor(BACKGROUND);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BACKGROUND);
        LinearLayout root=vertical(); root.setPadding(dp(16),0,dp(16),dp(20)); scroll.addView(root);
        LinearLayout top=horizontal(); top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView menu=iconImage(R.drawable.ui_menu,INK); menu.setOnClickListener(v->showHomeMenu());top.addView(menu,new LinearLayout.LayoutParams(dp(42),dp(48)));
        View spacer=new View(this); top.addView(spacer,new LinearLayout.LayoutParams(0,dp(48),1));
        ImageView updates=iconImage(R.drawable.ui_bell,INK);updates.setOnClickListener(v->startActivity(new Intent(this,com.xuebutong.app.update.UpdateActivity.class)));top.addView(updates,new LinearLayout.LayoutParams(dp(42),dp(48)));root.addView(top,matchWrap());
        LinearLayout brand=vertical();brand.setGravity(Gravity.CENTER);brand.setPadding(0,dp(24),0,0);
        ImageView icon=iconImage(R.drawable.book_mark,Color.TRANSPARENT);brand.addView(icon,new LinearLayout.LayoutParams(dp(88),dp(72)));
        TextView title=centered("学不通",32,INK);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);brand.addView(title,margins(matchWrap(),0,dp(8),0,0));
        TextView sub=centered("更 高 效 的 学 习 搜 题 助 手",13,MUTED);brand.addView(sub,margins(matchWrap(),0,dp(4),0,0));root.addView(brand,matchWrap());
        statusEmoji=text("",12,INK);statusTitle=text("",12,INK);statusText=text("",12,INK);
        startButton=horizontal();startButton.setGravity(Gravity.CENTER_VERTICAL);startButton.setPadding(dp(18),0,dp(14),0);startButton.setBackground(roundRect(RED,28));
        ImageView scan=iconImage(R.drawable.ui_scan,Color.WHITE);startButton.addView(scan,new LinearLayout.LayoutParams(dp(46),dp(52)));
        startLabel=centered("启动搜题",21,Color.WHITE);startLabel.setTypeface(Typeface.DEFAULT,Typeface.BOLD);startButton.addView(startLabel,new LinearLayout.LayoutParams(0,dp(52),1));
        startHint=text("",11,MUTED);startEmoji=centered("›",28,Color.WHITE);startButton.addView(startEmoji,new LinearLayout.LayoutParams(dp(40),dp(52)));
        startButton.setOnClickListener(v->toggleSearchService());root.addView(startButton,margins(new LinearLayout.LayoutParams(-1,dp(54)),dp(10),dp(70),dp(10),dp(30)));
        LinearLayout captureModes=horizontal();
        learningOption=captureOption("学习通模式","课程作业搜题",R.drawable.ui_cap);
        screenOption=captureOption("屏幕文字模式","识别屏幕题目",R.drawable.ui_text);
        shotOption=captureOption("截图搜题","从截图中搜题",R.drawable.ui_image);
        captureModes.addView(learningOption,margins(new LinearLayout.LayoutParams(0,dp(105),1),0,0,dp(4),0));captureModes.addView(screenOption,margins(new LinearLayout.LayoutParams(0,dp(105),1),dp(3),0,dp(3),0));captureModes.addView(shotOption,margins(new LinearLayout.LayoutParams(0,dp(105),1),dp(4),0,0,0));
        learningOption.setOnClickListener(v->chooseCaptureMode("learning"));screenOption.setOnClickListener(v->chooseCaptureMode("screen"));shotOption.setOnClickListener(v->chooseCaptureMode("shot"));root.addView(captureModes,matchWrap());captureHint=text("",12,MUTED);updateCaptureModes();
        smallModeOption=modeOption("小窗");largeModeOption=modeOption("大窗");smallModeOption.setOnClickListener(v->selectMode("small"));largeModeOption.setOnClickListener(v->selectMode("large"));updateModeLabels();
        LinearLayout tools=vertical();tools.setPadding(dp(14),dp(12),dp(14),dp(10));tools.setBackground(roundRect(SURFACE,14,LINE,1));
        TextView th=text("▦   常用工具                                      ›",16,INK);th.setTypeface(Typeface.DEFAULT,Typeface.BOLD);tools.addView(th,matchWrap());
        LinearLayout row=horizontal();row.addView(toolShortcut("搜题记录",R.drawable.ui_clock,this::showHistory),new LinearLayout.LayoutParams(0,dp(60),1));row.addView(toolShortcut("题库",R.drawable.ui_doc,()->startActivity(new Intent(this,com.xuebutong.app.bank.BankActivity.class))),new LinearLayout.LayoutParams(0,dp(60),1));row.addView(toolShortcut("悬浮外观",R.drawable.ui_image,this::showAppearance),new LinearLayout.LayoutParams(0,dp(60),1));row.addView(toolShortcut("应用设置",R.drawable.ui_settings,this::showHomeMenu),new LinearLayout.LayoutParams(0,dp(60),1));tools.addView(row,matchWrap());root.addView(tools,margins(matchWrap(),0,dp(18),0,0));
        shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout nav=horizontal();nav.setBackground(roundRect(SURFACE,0,LINE,1));
        nav.addView(navItem("首页",R.drawable.ui_home,()->scroll.smoothScrollTo(0,0)),new LinearLayout.LayoutParams(0,dp(58),1));nav.addView(navItem("搜题",R.drawable.ui_search,()->scroll.smoothScrollTo(0,captureModes.getTop())),new LinearLayout.LayoutParams(0,dp(58),1));nav.addView(navItem("模型",R.drawable.ui_layers,this::showModelManager),new LinearLayout.LayoutParams(0,dp(58),1));nav.addView(navItem("更新",R.drawable.ui_download,()->startActivity(new Intent(this,com.xuebutong.app.update.UpdateActivity.class))),new LinearLayout.LayoutParams(0,dp(58),1));shell.addView(nav,matchWrap());return shell;
    }
    private View toolShortcut(String label,int icon,Runnable action){TextView v=centered(label,12,INK);android.graphics.drawable.Drawable d=getDrawable(icon).mutate();d.setBounds(0,0,dp(23),dp(23));v.setCompoundDrawables(null,d,null,null);v.setCompoundDrawablePadding(dp(4));v.setOnClickListener(x->action.run());return v;}
    private void showHomeMenu(){String[] items={"模型与 API","答案窗口：小窗","答案窗口：大窗","搜题指令","搜题记录","悬浮外观","软件更新"};new AlertDialog.Builder(this).setTitle("应用设置").setItems(items,(d,i)->{switch(i){case 0:showModelManager();break;case 1:selectMode("small");break;case 2:selectMode("large");break;case 3:showPromptEditor();break;case 4:showHistory();break;case 5:showAppearance();break;case 6:startActivity(new Intent(this,com.xuebutong.app.update.UpdateActivity.class));break;}}).show();}

    private TextView captureOption(String title,String description,int iconId){
        TextView v=centered(title+"\n"+description,12,INK);
        v.setLineSpacing(dp(3),1f);
        v.setPadding(dp(3),dp(8),dp(3),dp(8));
        android.graphics.drawable.Drawable icon=getDrawable(iconId).mutate();
        icon.setTint(INK);
        icon.setBounds(0,0,dp(24),dp(24));
        v.setCompoundDrawables(null,icon,null,null);
        v.setCompoundDrawablePadding(dp(5));
        v.setContentDescription(title+"，"+description);
        return v;
    }
    private void styleCaptureOption(TextView option,boolean selected){
        option.setTextColor(selected?RED:INK);
        option.setBackground(roundRect(selected?Color.rgb(253,242,242):SURFACE,14,selected?Color.rgb(226,175,175):LINE,1));
        option.setSelected(selected);
    }
    private View navItem(String label,int iconId,Runnable action){
        TextView v=centered(label,11,MUTED);
        android.graphics.drawable.Drawable icon=getDrawable(iconId).mutate();
        icon.setTint(label.equals("首页")?RED:INK);icon.setBounds(0,0,dp(21),dp(21));
        v.setCompoundDrawables(null,icon,null,null);v.setCompoundDrawablePadding(dp(3));
        v.setOnClickListener(x->action.run());return v;
    }

    private View tile(int iconResource, String title, String hint, int color, Runnable action) {
        LinearLayout content = vertical();
        content.setGravity(Gravity.START);
        content.setMinimumHeight(dp(104));
        content.setPadding(dp(14), dp(13), dp(14), dp(13));
        content.setBackground(roundRect(SURFACE, 14, LINE, 1));
        ImageView icon = iconImage(iconResource, color);
        content.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView heading = text(title, 13, INK);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(heading, margins(matchWrap(), 0, dp(9), 0, 0));
        TextView sub = text(hint, 10, MUTED);
        sub.setTag("tool_hint");
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(sub, margins(matchWrap(), 0, dp(3), 0, 0));
        content.setContentDescription(title + "，" + hint);
        content.setOnClickListener(v -> {
            action.run();
        });
        return content;
    }

    private LinearLayout action(int iconResource, String label, String hint, int color, Runnable callback) {
        LinearLayout wrap = horizontal();
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.setMinimumHeight(dp(58));
        wrap.setPadding(dp(14), dp(8), dp(12), dp(8));
        ImageView icon = iconImage(iconResource, color);
        wrap.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        LinearLayout copy = vertical();
        TextView title = text(label, 13, INK);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        copy.addView(title, matchWrap());
        copy.addView(text(hint, 10, MUTED), margins(matchWrap(), 0, dp(2), 0, 0));
        wrap.addView(copy, margins(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f), dp(12), 0, 0, 0));
        TextView arrow = centered("›", 22, MUTED);
        wrap.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(36)));
        wrap.setContentDescription(label + "，" + hint);
        wrap.setOnClickListener(v -> callback.run());
        return wrap;
    }

    private void showModelManager(){
        JSONArray rows=store.models();
        String id=getSharedPreferences("provider_ui",MODE_PRIVATE).getString("last_provider","");
        if(id.isEmpty() || Providers.find(rows,id)==null){
            id="openai";
            for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(AppStore.isModelReady(row)){id=row.optString("id");break;}}
        }
        showModelManager(id);
    }
    private void showModelManager(String selected) { showModelManager(selected,-1); }
    private void showModelManager(String selected,int previousScroll) {
        JSONArray rows=store.models(); Providers preset=Providers.get(selected); JSONObject saved=Providers.find(rows,selected); JSONObject row=saved==null?new JSONObject():saved; boolean custom=preset.url.isEmpty(); final boolean[] formDirty={false};
        Dialog dialog=new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout shell=vertical();shell.setBackgroundColor(BACKGROUND);
        TextView header=text("‹    模型与 API",20,INK);header.setTypeface(Typeface.DEFAULT,Typeface.BOLD);header.setPadding(dp(18),dp(12),dp(18),dp(14));header.setOnClickListener(v->confirmDiscard(formDirty[0],dialog::dismiss));shell.addView(header,matchWrap());
        ScrollView scroll=new ScrollView(this);LinearLayout body=vertical();body.setPadding(dp(14),0,dp(14),dp(20));scroll.addView(body);
        LinearLayout card=vertical();card.setPadding(dp(14),dp(12),dp(14),dp(10));card.setBackground(roundRect(SURFACE,14,LINE,1));
        TextView heading=text("▱   模型供应商",16,INK);heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);card.addView(heading,matchWrap());card.addView(text("选择用于搜题与解析的 AI 模型供应商。",12,MUTED),margins(matchWrap(),0,dp(5),0,dp(7)));
        String[] order={"openai","doubao","qwen","zhipu","deepseek","moonshot","siliconflow","hunyuan"};
        final View[] selectedItem={null};
        java.util.Set<String> shown=new java.util.HashSet<>();
        for(String id:order){
            shown.add(id);Providers p=Providers.get(id);JSONObject configured=Providers.find(rows,id);
            View item=providerRow(p.name,configurationState(configured),providerIcon(id),id.equals(selected),()->{
                if(!id.equals(selected))confirmDiscard(formDirty[0],()->{dialog.dismiss();showModelManager(id);});
            });
            card.addView(item,matchWrap());if(id.equals(selected))selectedItem[0]=item;
        }
        for(int i=0;i<rows.length();i++){
            JSONObject customRow=rows.optJSONObject(i);if(customRow==null)continue;
            String id=customRow.optString("id");if(id.isEmpty() || shown.contains(id))continue;shown.add(id);
            View item=providerRow(nonempty(customRow.optString("name"),"自定义接口"),configurationState(customRow),R.drawable.ui_layers,id.equals(selected),()->{
                if(!id.equals(selected))confirmDiscard(formDirty[0],()->{dialog.dismiss();showModelManager(id);});
            });
            card.addView(item,matchWrap());if(id.equals(selected))selectedItem[0]=item;
        }
        TextView addCustom=text("＋  新增自定义接口",13,RED);addCustom.setPadding(dp(10),dp(12),dp(10),dp(8));
        addCustom.setOnClickListener(v->confirmDiscard(formDirty[0],()->{dialog.dismiss();editProvider("custom-"+java.util.UUID.randomUUID());}));card.addView(addCustom,matchWrap());
        body.addView(card,matchWrap());
        LinearLayout info=vertical();info.setPadding(dp(14),dp(12),dp(14),dp(12));info.setBackground(roundRect(SURFACE,14,LINE,1));TextView subtitle=text("⚿   API 密钥配置",16,INK);subtitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);info.addView(subtitle);info.addView(text("配置所选供应商的 API Key，用于调用模型服务。",12,MUTED),margins(matchWrap(),0,dp(4),0,dp(4)));
        TextView configStatus=text(configurationState(row),12,AppStore.isModelReady(row)?GREEN:MUTED);info.addView(configStatus,margins(matchWrap(),0,dp(4),0,dp(4)));
        EditText displayName=custom?compactField(info,"配置名称",row.optString("name"),false):null;
        EditText key=compactField(info,"API Key",row.optString("apiKey"),true);EditText endpoint=compactField(info,"接口地址",nonempty(row.optString("apiUrl"),preset.url),false);body.addView(info,margins(matchWrap(),0,dp(12),0,0));
        LinearLayout parameters=vertical();parameters.setPadding(dp(14),dp(12),dp(14),dp(12));parameters.setBackground(roundRect(SURFACE,14,LINE,1));TextView ph=text("☷   模型参数",16,INK);ph.setTypeface(Typeface.DEFAULT,Typeface.BOLD);parameters.addView(ph);EditText model=compactField(parameters,"使用模型",nonempty(row.optString("model"),preset.text),false);
        TextView testStatus=text(lastTestState(row),12,MUTED);
        LinearLayout tempLine=horizontal();tempLine.setGravity(Gravity.CENTER_VERTICAL);TextView tempLabel=text("温度（可选）",12,INK);tempLine.addView(tempLabel,new LinearLayout.LayoutParams(dp(88),dp(36)));SeekBar temp=new SeekBar(this);temp.setMax(20);temp.setProgress((int)Math.round(row.optDouble("temperature",0.3)*10));tempLine.addView(temp,new LinearLayout.LayoutParams(0,dp(36),1));TextView tempValue=centered(row.has("temperature")?String.format(Locale.CHINA,"%.1f",row.optDouble("temperature")):"默认",12,INK);tempLine.addView(tempValue,new LinearLayout.LayoutParams(dp(48),dp(36)));final boolean[] tempChanged={false};temp.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int value,boolean user){if(user){tempChanged[0]=true;formDirty[0]=true;tempValue.setText(String.format(Locale.CHINA,"%.1f",value/10.0));configStatus.setTextColor(ORANGE);configStatus.setText("当前有未保存修改");testStatus.setTextColor(MUTED);testStatus.setText("配置已修改，请测试连接后保存");}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});parameters.addView(tempLine,margins(matchWrap(),0,dp(5),0,0));TextView extra=text("更多设置与视觉模型   ›",13,RED);extra.setPadding(0,dp(12),0,dp(4));extra.setOnClickListener(v->confirmDiscard(formDirty[0],()->{dialog.dismiss();editProvider(selected);}));parameters.addView(extra);
        LinearLayout actions=vertical();actions.setPadding(dp(14),dp(6),dp(14),dp(6));actions.setBackground(roundRect(SURFACE,0,LINE,1));actions.addView(testStatus,matchWrap());
        LinearLayout actionButtons=horizontal();actions.addView(actionButtons,matchWrap());
        android.text.TextWatcher dirty=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence x,int st,int count,int after){}public void onTextChanged(CharSequence x,int st,int before,int count){formDirty[0]=true;configStatus.setTextColor(ORANGE);configStatus.setText("当前有未保存修改");testStatus.setTextColor(MUTED);testStatus.setText("配置已修改，请测试连接后保存");}public void afterTextChanged(android.text.Editable x){}};
        key.addTextChangedListener(dirty);endpoint.addTextChangedListener(dirty);model.addTextChangedListener(dirty);if(displayName!=null)displayName.addTextChangedListener(dirty);
        Button test=transparentButton("测试连接",RED);actionButtons.addView(test,new LinearLayout.LayoutParams(0,dp(46),1));
        test.setOnClickListener(v->testConnection(dialog,test,testStatus,selected,row,key,endpoint,model,temp,tempChanged,configStatus,formDirty,selectedItem[0]));
        Button save=transparentButton("保存配置",RED);save.setOnClickListener(v->{String url=providerUrl(endpoint.getText().toString()),token=key.getText().toString().trim(),m=model.getText().toString().trim();String label=custom?displayName.getText().toString().trim():preset.name;if(label.isEmpty()||url.isEmpty()||token.isEmpty()||m.isEmpty()){Toast.makeText(this,"请填写有效的 API Key、接口地址和模型",Toast.LENGTH_SHORT).show();return;}try{row.put("id",selected).put("name",label).put("apiUrl",url).put("apiKey",token).put("model",m).put("visionModel",row.optString("visionModel",preset.vision)).put("directConfig",true).put("enabled",true);if(tempChanged[0])row.put("temperature",temp.getProgress()/10.0);if(saved==null)rows.put(row);store.saveModels(rows);getSharedPreferences("provider_ui",MODE_PRIVATE).edit().putString("last_provider",selected).apply();refreshStatus();updateCaptureModes();int scrollY=scroll.getScrollY();dialog.dismiss();showModelManager(selected,scrollY);Toast.makeText(this,"配置已保存并启用",Toast.LENGTH_SHORT).show();}catch(JSONException ex){Toast.makeText(this,"保存失败",Toast.LENGTH_SHORT).show();}});actionButtons.addView(save,new LinearLayout.LayoutParams(0,dp(46),1));body.addView(parameters,margins(matchWrap(),0,dp(12),0,0));
        shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));shell.addView(actions,matchWrap());dialog.setContentView(shell);dialog.setOnKeyListener((d,k,event)->{if(k==android.view.KeyEvent.KEYCODE_BACK && event.getAction()==android.view.KeyEvent.ACTION_UP){confirmDiscard(formDirty[0],dialog::dismiss);return true;}return k==android.view.KeyEvent.KEYCODE_BACK;});dialog.show();Window window=dialog.getWindow();if(window!=null){window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);window.setStatusBarColor(BACKGROUND);window.setNavigationBarColor(BACKGROUND);window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);window.setBackgroundDrawableResource(android.R.color.transparent);window.setLayout(-1,-1);}
        scroll.post(()->{
            if(previousScroll>=0)scroll.scrollTo(0,previousScroll);
            else if(selectedItem[0]!=null && selectedItem[0].getTop()>dp(240))
                scroll.scrollTo(0,Math.max(0,card.getTop()+selectedItem[0].getTop()-dp(65)));
        });
    }

    private int providerIcon(String id){
        switch(id){
            case "openai":return R.drawable.provider_openai;
            case "doubao":return R.drawable.provider_doubao;
            case "qwen":return R.drawable.provider_qwen;
            case "zhipu":return R.drawable.provider_zhipu;
            case "deepseek":return R.drawable.provider_deepseek;
            case "moonshot":return R.drawable.provider_kimi;
            case "siliconflow":return R.drawable.provider_siliconflow;
            case "hunyuan":return R.drawable.provider_hunyuan;
            default:return R.drawable.ui_layers;
        }
    }
    private View providerRow(String title,String status,int icon,boolean selected,Runnable action){
        LinearLayout item=horizontal();item.setGravity(Gravity.CENTER_VERTICAL);item.setPadding(dp(8),dp(3),dp(8),dp(3));item.setMinimumHeight(dp(54));
        item.setBackground(roundRect(selected?0xfffcf0f0:Color.TRANSPARENT,8));
        ImageView mark=iconImage(icon,Color.TRANSPARENT);mark.setPadding(dp(5),dp(5),dp(5),dp(5));item.addView(mark,new LinearLayout.LayoutParams(dp(40),dp(38)));
        LinearLayout copy=vertical();TextView name=text(title,14,INK);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);copy.addView(name,matchWrap());
        TextView state=text(status,11,MUTED);state.setTag("provider_state");state.setMaxLines(2);state.setEllipsize(TextUtils.TruncateAt.END);copy.addView(state,matchWrap());item.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        TextView radio=centered(selected?"◉":"○",21,selected?RED:MUTED);item.addView(radio,new LinearLayout.LayoutParams(dp(32),dp(42)));
        item.setContentDescription(title+"，"+status+(selected?"，当前编辑":""));item.setOnClickListener(v->action.run());return item;
    }

    private void confirmDiscard(boolean dirty,Runnable action){
        if(!dirty){action.run();return;}
        new AlertDialog.Builder(this).setTitle("配置尚未保存").setMessage("切换或退出会丢失当前修改。请先保存，或确认放弃修改。")
            .setNegativeButton("继续编辑",null).setPositiveButton("放弃修改",(d,w)->action.run()).show();
    }

    private EditText compactField(LinearLayout parent,String label,String value,boolean secret){LinearLayout line=horizontal();line.setGravity(Gravity.CENTER_VERTICAL);TextView caption=text(label,12,INK);line.addView(caption,new LinearLayout.LayoutParams(dp(88),dp(40)));EditText input=input(label,secret);input.setSingleLine(true);input.setTextSize(12);input.setPadding(dp(10),0,dp(10),0);input.setBackground(roundRect(0xfffafafa,8,LINE,1));if(secret)input.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());input.setText(value);line.addView(input,new LinearLayout.LayoutParams(0,dp(36),1));if(secret){TextView eye=centered("◉",18,MUTED);eye.setContentDescription("显示或隐藏 API Key");eye.setOnClickListener(v->{boolean hidden=input.getTransformationMethod()!=null;input.setTransformationMethod(hidden?null:android.text.method.PasswordTransformationMethod.getInstance());input.setSelection(input.length());});line.addView(eye,new LinearLayout.LayoutParams(dp(32),dp(36)));}parent.addView(line,margins(matchWrap(),0,dp(5),0,0));return input;}

    private String nonempty(String value,String fallback){return value==null || value.trim().isEmpty()?fallback:value;}
    private String configurationState(JSONObject row){
        if(row==null || row.optString("apiKey").trim().isEmpty())return "未配置";
        if(!row.optBoolean("enabled"))return "已保存 · 未启用";
        if(!AppStore.isModelReady(row))return "配置不完整";
        return "配置完成 · 已启用"+(testMatches(row)?" · 上次测试通过":" · 连接未测试");
    }
    private boolean testMatches(JSONObject row){
        if(row==null || row.optString("id").isEmpty())return false;
        String tested=getSharedPreferences("provider_test",MODE_PRIVATE).getString(row.optString("id"),"");
        return !tested.isEmpty() && tested.equals(configFingerprint(row));
    }
    private String configFingerprint(JSONObject row){
        try{
            java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
            for(String key:new String[]{"id","apiKey","apiUrl","model","temperature","directConfig"}){
                byte[] b=row.optString(key).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(b.length).array());digest.update(b);
            }
            byte[] hash=digest.digest();StringBuilder out=new StringBuilder();for(byte b:hash)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
        }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private String lastTestState(JSONObject row){
        if(row==null || row.optString("apiKey").isEmpty())return "未测试连接。测试会发送一次简短请求，可能产生少量费用。";
        return testMatches(row)?"配置成功：上次模型连接测试通过":"配置已保存与连接验证是两回事。测试可能产生少量费用。";
    }
    private void refreshProviderState(TextView configStatus,View selectedItem,JSONObject row,String id){
        String state=configurationState(row);configStatus.setText(state);
        if(selectedItem!=null){TextView indicator=selectedItem.findViewWithTag("provider_state");if(indicator!=null)indicator.setText(state);selectedItem.setContentDescription(nonempty(row.optString("name"),Providers.get(id).name)+"，"+state+"，当前编辑");}
    }
    private void testConnection(Dialog dialog,Button button,TextView status,String id,JSONObject original,EditText key,EditText endpoint,EditText model,SeekBar temp,boolean[] tempChanged,TextView configStatus,boolean[] dirty,View selectedItem){
        String url=providerUrl(endpoint.getText().toString()),token=key.getText().toString().trim(),modelId=model.getText().toString().trim();
        if(url.isEmpty() || token.isEmpty() || modelId.isEmpty()){status.setTextColor(RED);status.setText("请先填写有效的 HTTPS 地址、API Key 和模型 ID");return;}
        final JSONObject request;
        try{request=new JSONObject(original.toString()).put("id",id).put("apiUrl",url).put("apiKey",token).put("model",modelId).put("directConfig",true);if(tempChanged!=null&&tempChanged[0])request.put("temperature",temp.getProgress()/10.0);}catch(JSONException e){status.setText("配置读取失败");return;}
        final String fingerprint=configFingerprint(request);button.setEnabled(false);status.setTextColor(MUTED);status.setText("正在测试连接…");
        executor.execute(()->{
            String result=new com.xuebutong.app.net.ModelClient().ask(request,"请只回复：连接成功","");
            runOnUiThread(()->{
                if(!dialog.isShowing() || isFinishing())return;
                button.setEnabled(true);
                if(!token.equals(key.getText().toString().trim())||!url.equals(providerUrl(endpoint.getText().toString()))||!modelId.equals(model.getText().toString().trim())||(tempChanged!=null&&tempChanged[0]&&request.optDouble("temperature",-1)!=temp.getProgress()/10.0)){status.setTextColor(MUTED);status.setText("配置已更改，请重新测试");return;}
                if(result==null || result.trim().isEmpty() || result.startsWith("HTTP ") || result.startsWith("错误:") || result.startsWith("解析失败")){
                    android.content.SharedPreferences tests=getSharedPreferences("provider_test",MODE_PRIVATE);
                    if(fingerprint.equals(tests.getString(id,"")))tests.edit().remove(id).apply();
                    if(configStatus!=null && !dirty[0])refreshProviderState(configStatus,selectedItem,original,id);
                    status.setTextColor(RED);String safe=result==null?"未收到有效回复":result.replace(token,"[已隐藏]");status.setText("连接测试失败："+safe);
                }else{
                    getSharedPreferences("provider_test",MODE_PRIVATE).edit().putString(id,fingerprint).apply();if(configStatus!=null && !dirty[0])refreshProviderState(configStatus,selectedItem,original,id);status.setTextColor(GREEN);status.setText((dirty==null || dirty[0])?"连接测试通过 · 请保存配置":"配置成功 · 模型连接已验证");
                }
            });
        });
    }

    private EditText providerField(LinearLayout parent,String label,String value,boolean secret) {
        parent.addView(text(label,12,MUTED),margins(matchWrap(),0,dp(10),0,dp(4)));
        EditText field=input(label,secret);field.setSingleLine(true);
        if(secret)field.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
        field.setText(value);
        parent.addView(field,matchWrap());return field;
    }

    private void editProvider(String id) {
        JSONArray rows=store.models();
        JSONObject existing=Providers.find(rows,id);
        JSONObject row=existing==null?new JSONObject():existing;
        Providers preset=Providers.get(id);
        boolean custom=preset.url.isEmpty();
        LinearLayout body=vertical();body.setPadding(dp(20),dp(4),dp(20),dp(12));
        EditText name=custom?providerField(body,"配置名称",row.optString("name"),false):null;
        EditText key=providerField(body,"API Key",row.optString("apiKey"),true);
        String current=row.optString("model");if(current.isEmpty()) current=preset.text;
        EditText model=providerField(body,"文字模型",current,false);
        Button more=transparentButton("更多设置",BLUE);body.addView(more,matchWrap());
        LinearLayout advanced=vertical();body.addView(advanced,matchWrap());advanced.setVisibility(custom?View.VISIBLE:View.GONE);
        more.setOnClickListener(v->advanced.setVisibility(advanced.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        EditText vision=providerField(advanced,"视觉模型（支持图片输入）",row.optString("visionModel",preset.vision),false);
        EditText endpoint=providerField(advanced,"API 地址",row.optString("apiUrl",preset.url),false);
        Button discover=transparentButton("读取可用文字模型",BLUE);advanced.addView(discover,matchWrap());
        Switch primary=new Switch(this);primary.setText("优先用于截图搜题");primary.setChecked(row.optBoolean("preferVision"));advanced.addView(primary,matchWrap());
        Switch enabled=new Switch(this);enabled.setText("启用此供应商");
        enabled.setChecked(row.optString("apiKey").isEmpty() || row.optBoolean("enabled"));body.addView(enabled,matchWrap());
        TextView status=text(configurationState(existing)+" · "+lastTestState(existing),12,MUTED);body.addView(status,margins(matchWrap(),0,dp(8),0,dp(4)));
        Button test=transparentButton("测试连接",RED);body.addView(test,matchWrap());
        TextView error=errorText();body.addView(error,matchWrap());
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle(custom?"自定义供应商":preset.name).setView(scroll)
            .setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        test.setOnClickListener(v->testConnection(dlg,test,status,id,row,key,endpoint,model,null,null,null,null,null));
        discover.setOnClickListener(v->{
            String url=providerUrl(endpoint.getText().toString());
            if(url.isEmpty() || key.getText().toString().trim().isEmpty()){showError(error,"请先填写有效的 HTTPS 地址和 API Key");return;}
            JSONObject config=json("apiUrl",url,"apiKey",key.getText().toString().trim(),"id",id);
            discover.setEnabled(false);discover.setText("正在读取…");
            executor.execute(()->{
                ModelDiscoveryClient.Result list=new ModelDiscoveryClient().discover(config);
                runOnUiThread(()->{
                    if(!dlg.isShowing() || isFinishing())return;
                    discover.setEnabled(true);discover.setText("读取可用文字模型");
                    if(!list.ok || list.models.isEmpty()){showError(error,list.message+"，也可手动填写模型 ID");return;}
                    String[] options=list.models.toArray(new String[0]);
                    new AlertDialog.Builder(this).setTitle("选择文字模型").setItems(options,(d,i)->model.setText(options[i])).show();
                });
            });
        });
        dlg.setOnShowListener(d->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String url=providerUrl(endpoint.getText().toString()),token=key.getText().toString().trim();
            String modelId=model.getText().toString().trim(),visual=vision.getText().toString().trim();
            String label=custom?name.getText().toString().trim():preset.name;
            if(label.isEmpty() || url.isEmpty() || (enabled.isChecked() && (token.isEmpty() || modelId.isEmpty()))){
                showError(error,"请检查名称、HTTPS 接口、API Key 和模型 ID");return;
            }
            if(primary.isChecked() && enabled.isChecked() && visual.isEmpty()){showError(error,"请启用此供应商并填写视觉模型 ID");return;}
            try{
                if(primary.isChecked() && enabled.isChecked())for(int i=0;i<rows.length();i++)if(rows.optJSONObject(i)!=null)rows.getJSONObject(i).put("preferVision",false);
                row.put("id",id).put("name",label).put("apiUrl",url).put("apiKey",token).put("model",modelId)
                    .put("visionModel",visual).put("preferVision",primary.isChecked() && enabled.isChecked()).put("directConfig",true).put("enabled",enabled.isChecked());
                if(existing==null)rows.put(row);
                store.saveModels(rows);getSharedPreferences("provider_ui",MODE_PRIVATE).edit().putString("last_provider",id).apply();refreshStatus();updateCaptureModes();dlg.dismiss();showModelManager(id);
            }catch(JSONException e){showError(error,"配置保存失败，请重试");}
        }));
        dlg.show();
    }

    private String providerUrl(String entry) {
        String s=entry.trim();
        try {
            java.net.URI u=new java.net.URI(s);
            if(!"https".equalsIgnoreCase(u.getScheme()) || u.getHost()==null || u.getUserInfo()!=null || u.getQuery()!=null || u.getFragment()!=null)return "";
            while(s.endsWith("/"))s=s.substring(0,s.length()-1);
            return s.endsWith("/responses") || s.endsWith("/chat/completions")?s:s+"/chat/completions";
        }catch(Exception e){return "";}
    }


    private void showPromptEditor() {
        EditText prompt = input("留空则不发送系统消息", false);
        prompt.setText(store.prompt());
        prompt.setGravity(Gravity.TOP | Gravity.START);
        prompt.setMinLines(7);
        new AlertDialog.Builder(this)
                .setTitle("搜题指令（System Prompt）")
                .setMessage("发给AI的角色和回答要求")
                .setView(wrapDialogInput(prompt))
                .setNeutralButton("恢复默认", (d, w) -> { store.savePrompt(AppStore.DEFAULT_PROMPT); toast("已恢复默认"); })
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> { store.savePrompt(prompt.getText().toString().trim()); toast("搜题指令已保存"); })
                .show();
    }

    private void selectMode(String mode) {
        if (mode.equals(store.windowMode())) return;
        store.saveWindowMode(mode);
        updateModeLabels();
        toast("已切换为" + ("small".equals(mode) ? "小窗模式" : "大窗模式"));
    }

    private void updateModeLabels() {
        if (smallModeOption == null || largeModeOption == null) return;
        boolean small = "small".equals(store.windowMode());
        styleModeOption(smallModeOption, small);
        styleModeOption(largeModeOption, !small);
    }

    private void showAppearance() {
        LinearLayout layout = vertical();
        layout.setPadding(dp(20), 0, dp(20), dp(8));
        SeekBar floatSize = seek(store.intSetting("floatSize", 28) - 20, 40);
        ColorControl floatColor = new ColorControl("悬浮球颜色与透明度",
                store.stringSetting("floatColor", "#D4AF37"),
                store.intSetting("floatAlpha", 100));
        ColorControl answerColor = new ColorControl("答案显示颜色与透明度",
                store.stringSetting("answerTextColor", "#D4AF37"),
                store.intSetting("answerAlpha", 85));
        SeekBar floatTransparency = seek(100 - floatColor.opacity, 100);
        SeekBar answerTransparency = seek(100 - answerColor.opacity, 100);
        TextView floatTransparencyLabel = text("", 12, Color.rgb(90, 90, 90));
        TextView answerTransparencyLabel = text("", 12, Color.rgb(90, 90, 90));
        bindTransparency(floatTransparency, floatTransparencyLabel, floatColor);
        bindTransparency(answerTransparency, answerTransparencyLabel, answerColor);
        floatColor.root.setOnClickListener(view -> showColorPicker(
                "自定义悬浮球颜色", floatColor.color, floatColor.opacity,
                selection -> {
                    floatColor.setSelection(selection);
                    floatTransparency.setProgress(100 - selection.opacity);
                }));
        answerColor.root.setOnClickListener(view -> showColorPicker(
                "自定义答案显示颜色", answerColor.color, answerColor.opacity,
                selection -> {
                    answerColor.setSelection(selection);
                    answerTransparency.setProgress(100 - selection.opacity);
                }));
        EditText duration = input("震动时长(ms)", false);
        duration.setInputType(InputType.TYPE_CLASS_NUMBER);
        duration.setText(String.valueOf(store.intSetting("vibrationDuration", 30)));
        CheckBox vibration = new CheckBox(this);
        vibration.setText("开启震动反馈");
        vibration.setChecked(store.boolSetting("vibrationEnabled", true));
        layout.addView(floatColor.root, margins(matchWrap(), 0, dp(8), 0, 0));
        layout.addView(floatTransparencyLabel, margins(matchWrap(), 0, dp(6), 0, 0));
        layout.addView(floatTransparency, matchWrap());
        addSetting(layout, "悬浮球大小（20-60dp）", floatSize);
        layout.addView(answerColor.root, margins(matchWrap(), 0, dp(8), 0, 0));
        layout.addView(answerTransparencyLabel, margins(matchWrap(), 0, dp(6), 0, 0));
        layout.addView(answerTransparency, matchWrap());
        layout.addView(vibration, margins(matchWrap(), 0, dp(10), 0, 0));
        layout.addView(duration, margins(matchWrap(), 0, dp(8), 0, 0));
        new AlertDialog.Builder(this).setTitle("自定义外观设置").setView(layout)
                .setNegativeButton("取消", null)
                .setNeutralButton("恢复默认", (d, w) -> {
                    store.saveAppearance(100, "#D4AF37", 28, 85, "#D4AF37", 30, true);
                    applyOverlayAppearance();
                    toast("已恢复默认外观");
                })
                .setPositiveButton("保存并应用", (d, w) -> {
                    int ms;
                    try { ms = Integer.parseInt(duration.getText().toString()); } catch (Exception e) { ms = 30; }
                    store.saveAppearance(floatColor.opacity, floatColor.color,
                            floatSize.getProgress() + 20, answerColor.opacity,
                            answerColor.color, ms, vibration.isChecked());
                    applyOverlayAppearance();
                    toast("外观设置已保存");
                }).show();
    }

    private void bindTransparency(SeekBar seekBar, TextView label, ColorControl control) {
        Runnable update = () -> {
            int transparency = seekBar.getProgress();
            label.setText("透明度  " + transparency + "%");
            control.setOpacity(100 - transparency);
        };
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { update.run(); }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        update.run();
    }

    private void showColorPicker(String title, String initial, int initialOpacity,
                                 ColorSelectionHandler callback) {
        String[] names = new String[]{
                "白色（透明）", "金色", "纯白", "黑色", "红色", "橙色", "黄色",
                "翠绿", "青色", "天蓝", "紫色", "粉红", "灰色"
        };
        String[] colors = new String[]{
                "#FFFFFF", "#D4AF37", "#FFFFFF", "#000000", "#F44336", "#FF9800", "#FFEB3B",
                "#4CAF50", "#00BCD4", "#2196F3", "#9C27B0", "#E91E63", "#9E9E9E"
        };
        int[] opacities = new int[]{0, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100};

        LinearLayout grid = vertical();
        grid.setPadding(dp(14), dp(4), dp(14), dp(8));
        AlertDialog[] holder = new AlertDialog[1];
        String normalized = normalizeHexColor(initial, "#D4AF37");
        for (int i = 0; i < names.length; i += 2) {
            LinearLayout row = horizontal();
            for (int j = i; j < Math.min(i + 2, names.length); j++) {
                int index = j;
                boolean selected = opacities[j] == initialOpacity
                        && colors[j].equalsIgnoreCase(normalized);
                row.addView(colorChoice(names[j], colors[j], opacities[j], selected, () -> {
                    callback.accept(new ColorSelection(colors[index], opacities[index]));
                    if (holder[0] != null) holder[0].dismiss();
                }), margins(weighted(), dp(3), dp(3), dp(3), dp(3)));
            }
            if (row.getChildCount() == 1) row.addView(new View(this), weighted());
            grid.addView(row, matchWrap());
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(grid);
        holder[0] = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(scroll)
                .setNegativeButton("取消", null)
                .create();
        holder[0].show();
    }

    private View colorChoice(String name, String color, int opacity,
                             boolean selected, Runnable onClick) {
        LinearLayout tile = horizontal();
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(10), dp(9), dp(10), dp(9));
        tile.setBackground(roundRect(Color.WHITE, 4,
                selected ? BLUE : Color.rgb(220, 220, 220), selected ? 2 : 1));
        View swatch = new View(this);
        int parsed = Color.parseColor(color);
        int display = opacity == 0 ? Color.WHITE : Color.argb(
                Math.round(opacity * 2.55f), Color.red(parsed), Color.green(parsed), Color.blue(parsed));
        swatch.setBackground(roundRect(display, 4, Color.rgb(185, 185, 185), 1));
        LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(dp(30), dp(30));
        swatchParams.rightMargin = dp(9);
        tile.addView(swatch, swatchParams);
        tile.addView(text(name, 12, Color.rgb(45, 45, 45)), weighted());
        tile.setOnClickListener(view -> onClick.run());
        tile.setContentDescription(name);
        return tile;
    }

    private void applyOverlayAppearance() {
        if (!OverlayService.isRunning()) return;
        Intent reload = new Intent(this, OverlayService.class);
        reload.setAction(OverlayService.ACTION_RELOAD_APPEARANCE);
        startService(reload);
    }

    private void showHistory() {
        LinearLayout shell = vertical();
        shell.setPadding(dp(18), dp(16), dp(18), dp(14));
        shell.setBackground(roundRect(SURFACE, 8));

        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout heading = vertical();
        TextView title = text("搜索历史", 20, INK);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView count = text("", 11, MUTED);
        heading.addView(title, matchWrap());
        heading.addView(count, margins(matchWrap(), 0, dp(2), 0, 0));
        header.addView(heading, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button clear = transparentButton("清空", RED);
        header.addView(clear, new LinearLayout.LayoutParams(dp(64), dp(44)));
        ImageButton close = iconButton(android.R.drawable.ic_menu_close_clear_cancel, MUTED, "关闭历史");
        header.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        shell.addView(header, matchWrap());
        shell.addView(divider(), margins(dividerParams(), 0, dp(10), 0, dp(8)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout list = vertical();
        scroll.addView(list, matchWrap());
        shell.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Dialog dialog = showTallDialog(shell, .82f);
        Runnable refresh = () -> renderHistoryList(list, count, clear, dialog);
        close.setOnClickListener(v -> dialog.dismiss());
        clear.setOnClickListener(v -> confirmClearHistory(refresh));
        refresh.run();
    }

    private void renderHistoryList(LinearLayout list, TextView count, Button clear, Dialog historyDialog) {
        list.removeAllViews();
        JSONArray history = store.history();
        count.setText(history.length() + " 条记录");
        clear.setEnabled(history.length() > 0);
        clear.setAlpha(history.length() > 0 ? 1f : .35f);
        updateToolSummaries();
        if (history.length() == 0) {
            LinearLayout empty = vertical();
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(72), dp(20), dp(72));
            ImageView emptyIcon = iconImage(android.R.drawable.ic_menu_recent_history, LINE);
            empty.addView(emptyIcon, centeredParams(42, 42));
            TextView emptyTitle = centered("暂无搜索记录", 15, MUTED);
            emptyTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            empty.addView(emptyTitle, margins(matchWrap(), 0, dp(14), 0, 0));
            list.addView(empty, matchWrap());
            return;
        }

        for (int visibleIndex = 0; visibleIndex < history.length(); visibleIndex++) {
            int sourceIndex = history.length() - 1 - visibleIndex;
            JSONObject item = history.optJSONObject(sourceIndex);
            if (item == null) continue;
            LinearLayout card = vertical();
            card.setPadding(dp(14), dp(12), dp(10), dp(12));
            card.setBackground(roundRect(BACKGROUND, 8));

            LinearLayout meta = horizontal();
            meta.setGravity(Gravity.CENTER_VERTICAL);
            TextView time = text(historyTime(item, false), 10, MUTED);
            meta.addView(time, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView answerCount = text(historyAnswerCount(item.optJSONObject("a")) + " 个答案", 10, GREEN);
            meta.addView(answerCount, wrapWrap());
            ImageButton delete = iconButton(android.R.drawable.ic_menu_delete, MUTED, "删除记录");
            meta.addView(delete, margins(new LinearLayout.LayoutParams(dp(38), dp(38)), dp(5), 0, 0, 0));
            card.addView(meta, matchWrap());

            TextView question = text(item.optString("q", "未知题目"), 13, INK);
            question.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            question.setMaxLines(2);
            question.setEllipsize(TextUtils.TruncateAt.END);
            question.setLineSpacing(0, 1.08f);
            card.addView(question, margins(matchWrap(), 0, dp(4), dp(4), 0));
            card.setContentDescription(historyTime(item, true) + "，" + question.getText());
            card.setOnClickListener(v -> showHistoryDetail(item, sourceIndex,
                    () -> renderHistoryList(list, count, clear, historyDialog)));
            delete.setOnClickListener(v -> confirmDeleteHistory(sourceIndex,
                    () -> renderHistoryList(list, count, clear, historyDialog)));
            list.addView(card, margins(matchWrap(), 0, 0, 0, dp(8)));
        }
    }

    private void showHistoryDetail(JSONObject item, int sourceIndex, Runnable refreshHistory) {
        LinearLayout shell = vertical();
        shell.setPadding(dp(18), dp(16), dp(18), dp(14));
        shell.setBackground(roundRect(SURFACE, 8));

        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout heading = vertical();
        TextView title = text("历史答案", 20, INK);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.addView(title, matchWrap());
        heading.addView(text(historyTime(item, true), 11, MUTED), margins(matchWrap(), 0, dp(2), 0, 0));
        header.addView(heading, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button copyAll = transparentButton("复制全部", BLUE);
        header.addView(copyAll, new LinearLayout.LayoutParams(dp(82), dp(44)));
        ImageButton close = iconButton(android.R.drawable.ic_menu_close_clear_cancel, MUTED, "关闭详情");
        header.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        shell.addView(header, matchWrap());
        shell.addView(divider(), margins(dividerParams(), 0, dp(10), 0, dp(10)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = vertical();
        TextView questionLabel = text("题目", 11, MUTED);
        questionLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        body.addView(questionLabel, matchWrap());
        TextView question = text(item.optString("q", "未知题目"), 14, INK);
        question.setTextIsSelectable(true);
        question.setLineSpacing(0, 1.12f);
        question.setPadding(dp(12), dp(10), dp(12), dp(10));
        question.setBackground(roundRect(BACKGROUND, 8));
        body.addView(question, margins(matchWrap(), 0, dp(6), 0, dp(16)));

        JSONObject answers = item.optJSONObject("a");
        List<String> ids = historyAnswerIds(answers);
        if (ids.isEmpty()) {
            body.addView(centered("未保存有效答案", 13, MUTED), margins(matchWrap(), 0, dp(24), 0, dp(24)));
        } else {
            for (String id : ids) {
                String answer = answers.optString(id);
                LinearLayout answerCard = vertical();
                answerCard.setPadding(dp(12), dp(10), dp(12), dp(12));
                answerCard.setBackground(roundRect(SURFACE, 8, LINE, 1));
                LinearLayout answerHeader = horizontal();
                answerHeader.setGravity(Gravity.CENTER_VERTICAL);
                TextView provider = text(providerName(id), 13, providerColor(id));
                provider.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                answerHeader.addView(provider, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                Button copy = transparentButton("复制", BLUE);
                answerHeader.addView(copy, new LinearLayout.LayoutParams(dp(58), dp(38)));
                answerCard.addView(answerHeader, matchWrap());
                TextView answerText = text(answer, 13, INK);
                answerText.setTextIsSelectable(true);
                answerText.setLineSpacing(0, 1.12f);
                answerCard.addView(answerText, margins(matchWrap(), 0, dp(5), 0, 0));
                copy.setOnClickListener(v -> copyText(providerName(id) + " 答案", answer));
                body.addView(answerCard, margins(matchWrap(), 0, 0, 0, dp(10)));
            }
        }

        Button deleteRecord = transparentButton("删除这条记录", RED);
        body.addView(deleteRecord, margins(matchWrap(), 0, dp(6), 0, dp(4)));
        scroll.addView(body, matchWrap());
        shell.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Dialog dialog = showTallDialog(shell, .86f);
        close.setOnClickListener(v -> dialog.dismiss());
        copyAll.setOnClickListener(v -> copyText("历史答案", formatHistory(item)));
        deleteRecord.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("删除记录")
                .setMessage("确定删除这条搜索记录吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> {
                    if (store.deleteHistoryAt(sourceIndex)) {
                        dialog.dismiss();
                        refreshHistory.run();
                        toast("记录已删除");
                    }
                }).show());
    }

    private void confirmDeleteHistory(int sourceIndex, Runnable onDeleted) {
        new AlertDialog.Builder(this).setTitle("删除记录").setMessage("确定删除这条搜索记录吗？")
                .setNegativeButton("取消", null).setPositiveButton("删除", (d, w) -> {
                    if (store.deleteHistoryAt(sourceIndex)) {
                        onDeleted.run();
                        toast("记录已删除");
                    }
                }).show();
    }

    private void confirmClearHistory(Runnable onCleared) {
        new AlertDialog.Builder(this).setTitle("确认清空").setMessage("确定要清空所有搜索历史吗？")
                .setNegativeButton("取消", null).setPositiveButton("清空", (d, w) -> {
                    store.clearHistory();
                    onCleared.run();
                    toast("搜索历史已清空");
                }).show();
    }

    private int historyAnswerCount(JSONObject answers) {
        return historyAnswerIds(answers).size();
    }

    private List<String> historyAnswerIds(JSONObject answers) {
        List<String> ids = new ArrayList<>();
        if (answers == null) return ids;
        for (String id : new String[]{"deepseek", "qwen", "zhipu", "doubao"}) {
            if (isUsableHistoryAnswer(answers.optString(id))) ids.add(id);
        }
        java.util.Iterator<String> keys = answers.keys();
        while (keys.hasNext()) {
            String id = keys.next();
            if (!ids.contains(id) && isUsableHistoryAnswer(answers.optString(id))) ids.add(id);
        }
        return ids;
    }

    private boolean isUsableHistoryAnswer(String answer) {
        return answer != null && !answer.trim().isEmpty() && !"?".equals(answer.trim())
                && !answer.startsWith("错误:") && !answer.startsWith("HTTP")
                && !answer.startsWith("解析失败");
    }

    private String historyTime(JSONObject item, boolean full) {
        long timestamp = item.optLong("timestamp", 0L);
        if (timestamp > 0L) {
            return new SimpleDateFormat(full ? "yyyy-MM-dd HH:mm" : "MM-dd HH:mm", Locale.CHINA)
                    .format(new Date(timestamp));
        }
        String legacy = item.optString("time", "");
        return legacy.isEmpty() ? "时间未知" : legacy;
    }

    private String providerName(String id) {
        return Providers.label(store.models(),id);
    }

    private int providerColor(String id) {
        if ("deepseek".equals(id)) return BLUE;
        if ("qwen".equals(id)) return ORANGE;
        if ("zhipu".equals(id)) return Color.rgb(117, 86, 153);
        if ("doubao".equals(id)) return GREEN;
        return MUTED;
    }

    private String formatHistory(JSONObject item) {
        if (item == null) return "";
        StringBuilder value = new StringBuilder("题目：")
                .append(item.optString("q", "未知题目")).append("\n\n");
        JSONObject answers = item.optJSONObject("a");
        for (String id : historyAnswerIds(answers)) {
            value.append(providerName(id)).append("：\n")
                    .append(answers.optString(id)).append("\n\n");
        }
        return value.toString().trim();
    }

    private void copyText(String label, String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
        toast("已复制" + label);
    }

    private void toggleSearchService() {
        if (OverlayService.isRunning()) {
            stopService(new Intent(this,OverlayService.class));
            startButton.postDelayed(this::refreshStartButton,200);
            return;
        }
        if (store.enabledModelCount() == 0) { toast("请先在模型与 API 中选择供应商，填写 API Key 并保存"); return; }
        if ("shot".equals(captureMode()) && Providers.vision(store.models())==null) {
            captureHint.setText("请先选择截图使用的视觉模型");showModelManager();return;
        }
        if (!screenMode() && !QuestionAccessibilityService.isEnabled(this)) {
            toast("请先开启无障碍服务");
            openAccessibilitySettings();
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("请先授予悬浮窗权限");
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }
        if (screenMode()) {
            android.media.projection.MediaProjectionManager mgr=getSystemService(android.media.projection.MediaProjectionManager.class);
            Intent request=Build.VERSION.SDK_INT>=34
                ? mgr.createScreenCaptureIntent(android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay())
                : mgr.createScreenCaptureIntent();
            startActivityForResult(request,7102);
        } else launchOverlay(null);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request!=7102 || !screenMode()) return;
        if (result==RESULT_OK && data!=null && store.enabledModelCount()>0 && Settings.canDrawOverlays(this))
            launchOverlay(data);
        else captureHint.setText("未启动屏幕文字模式，点击启动搜题可重新授权。");
    }
    private void launchOverlay(Intent grant) {
        Intent overlay=new Intent(this,OverlayService.class);
        if(grant!=null) overlay.putExtra("screenGrant",grant);
        if(Build.VERSION.SDK_INT>=26) startForegroundService(overlay); else startService(overlay);
        startButton.postDelayed(this::refreshStartButton,200);
    }

    private void openAccessibilitySettings() {
        ComponentName service = new ComponentName(this, QuestionAccessibilityService.class);
        Intent details = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS");
        details.putExtra(Intent.EXTRA_COMPONENT_NAME, service);
        details.setData(Uri.parse("package:" + getPackageName()));
        try {
            startActivity(details);
        } catch (Exception ignored) {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }
    }

    private void refreshStatus() {
        int count = store.enabledModelCount();
        statusEmoji.setTextColor(count > 0 ? GREEN : ORANGE);
        statusTitle.setText(count > 0 ? "搜题服务已就绪" : "等待模型配置");
        statusText.setText(count > 0 ? count + " 个模型可用" : "请选择供应商并启用一个模型");
        for (View view : gatedViews) { view.setAlpha(1f); view.setEnabled(true); }
        updateToolSummaries();
        refreshStartButton();
    }

    private void refreshStartButton() {
        boolean running = OverlayService.isRunning();
        startEmoji.setText(running ? "■" : "›");
        startLabel.setText(running ? "停止搜题" : "启动搜题");
        startHint.setText(running ? "悬浮搜题服务正在运行" : "开启悬浮搜题服务");
        startButton.setBackground(roundRect(running ? INK : RED, 28));
        startButton.setContentDescription(running ? "停止搜题服务" : "启动搜题服务");
    }

    private void updateToolSummaries() {
        if (modelSummary != null) {
            int count = store.enabledModelCount();
            modelSummary.setText(count > 0 ? "已启用 " + count + " 个模型" : "添加供应商与密钥");
        }
        if (historySummary != null) {
            int count = store.history().length();
            historySummary.setText(count > 0 ? count + " 条搜索记录" : "暂无搜索记录");
        }
    }

    private Dialog showCustomDialog(View view, boolean cancelable) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        dialog.setCancelable(cancelable);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(roundRect(Color.WHITE, 8));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
        if (window != null) window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * .90f), WindowManager.LayoutParams.WRAP_CONTENT);
        return dialog;
    }

    private Dialog showTallDialog(View view, float heightFraction) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dialog.setCancelable(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(roundRect(SURFACE, 8));
            int width = Math.round(getResources().getDisplayMetrics().widthPixels * .94f);
            int height = Math.round(getResources().getDisplayMetrics().heightPixels * heightFraction);
            window.setLayout(width, height);
        }
        return dialog;
    }

    private TextView sectionTitle(String value) {
        TextView title = text(value, 12, MUTED);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return title;
    }

    private TextView modeOption(String value) {
        TextView option = centered(value, 13, MUTED);
        option.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        option.setContentDescription(value + "答案窗口");
        return option;
    }

    private void styleModeOption(TextView option, boolean selected) {
        option.setTextColor(selected ? RED : MUTED);
        option.setBackground(roundRect(selected ? Color.rgb(253, 238, 238) : Color.TRANSPARENT, 10));
        option.setSelected(selected);
    }

    private ImageView iconImage(int resource, int tint) {
        ImageView icon = new ImageView(this);
        icon.setImageResource(resource);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        if (tint != Color.TRANSPARENT) icon.setColorFilter(tint);
        return icon;
    }

    private ImageButton iconButton(int resource, int tint, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(resource);
        button.setColorFilter(tint);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setContentDescription(description);
        return button;
    }

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        return divider;
    }

    private LinearLayout.LayoutParams dividerParams() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
    }

    private LinearLayout.LayoutParams centeredParams(int width, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(width), dp(height));
        params.gravity = Gravity.CENTER;
        return params;
    }

    private void replaceAuthDialog(View view) { closeAuthDialog(); authDialog = showCustomDialog(view, false); }
    private void closeAuthDialog() { if (authDialog != null) { authDialog.dismiss(); authDialog = null; } }
    private LinearLayout authLayout(int topPadding) { LinearLayout l = vertical(); l.setGravity(Gravity.CENTER); l.setPadding(dp(28), topPadding, dp(28), dp(32)); l.setBackgroundColor(SURFACE); return l; }
    private EditText input(String hint, boolean password) { EditText e = new EditText(this); e.setHint(hint); e.setTextSize(15); e.setTextColor(INK); e.setHintTextColor(MUTED); e.setMinimumHeight(dp(50)); e.setPadding(dp(14), dp(12), dp(14), dp(12)); e.setBackground(roundRect(Color.rgb(244,246,247), 8, LINE, 1)); if(password)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD); return e; }
    private Button primaryButton(String label, int color) { Button b = new Button(this); b.setText(label); b.setTextSize(15); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setMinHeight(dp(48)); b.setPadding(dp(22), dp(10), dp(22), dp(10)); b.setBackground(roundRect(color, 8)); return b; }
    private Button transparentButton(String label, int color) { Button b = new Button(this); b.setText(label); b.setTextColor(color); b.setTextSize(12); b.setAllCaps(false); b.setMinHeight(dp(40)); b.setBackgroundColor(Color.TRANSPARENT); return b; }
    private TextView errorText() { TextView t = centered("", 12, Color.rgb(244,67,54)); t.setVisibility(View.GONE); return t; }
    private void showError(TextView t, String value) { t.setText(value); t.setVisibility(View.VISIBLE); }
    private void setBusy(Button primary, Button other, String text) { primary.setEnabled(false); other.setEnabled(false); primary.setText(text); }
    private void clearBusy(Button primary, Button other, String text) { primary.setEnabled(true); other.setEnabled(true); primary.setText(text); }
    private LinearLayout wrapDialogInput(View view) { LinearLayout l=vertical(); l.setPadding(dp(20),0,dp(20),0); l.addView(view,matchWrap()); return l; }
    private TextView centered(String value,int size,int color){TextView t=text(value,size,color);t.setGravity(Gravity.CENTER);return t;}
    private TextView centeredBold(String value,int size,int color){TextView t=centered(value,size,color);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private android.graphics.drawable.GradientDrawable roundRect(int color,int radius){return roundRect(color,radius,Color.TRANSPARENT,0);}
    private android.graphics.drawable.GradientDrawable roundRect(int color,int radius,int strokeColor,int stroke){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke>0)d.setStroke(dp(stroke),strokeColor);return d;}
    private LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);}
    private LinearLayout.LayoutParams wrapWrap(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);}
    private LinearLayout.LayoutParams weighted(){return new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);}
    private LinearLayout.LayoutParams margins(LinearLayout.LayoutParams p,int l,int t,int r,int b){p.setMargins(l,t,r,b);return p;}
    private LinearLayout.LayoutParams fixedWidth(int width,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(width),ViewGroup.LayoutParams.WRAP_CONTENT);p.gravity=Gravity.CENTER;p.bottomMargin=bottom;return p;}
    private SeekBar seek(int value,int max){SeekBar s=new SeekBar(this);s.setMax(max);s.setProgress(Math.max(0,Math.min(max,value)));return s;}
    private void addSetting(LinearLayout l,String label,SeekBar value){l.addView(text(label,12,MUTED),margins(matchWrap(),0,dp(8),0,0));l.addView(value,matchWrap());}
    private JSONObject json(Object... pairs){JSONObject o=new JSONObject();for(int i=0;i+1<pairs.length;i+=2){try{o.put(String.valueOf(pairs[i]),pairs[i+1]);}catch(JSONException ignored){}}return o;}
    private int integer(EditText e,int fallback){try{return Integer.parseInt(e.getText().toString());}catch(Exception ignored){return fallback;}}
    private String normalizeHexColor(String value,String fallback){try{int color=Color.parseColor(value.trim());return String.format(Locale.US,"#%02X%02X%02X",Color.red(color),Color.green(color),Color.blue(color));}catch(Exception ignored){return normalizeHexColor(fallback,"#D4AF37");}}
    private String formatDate(String value){if(value==null||value.isEmpty()||"null".equals(value))return "无";return value.length()>=10?value.substring(0,10):value;}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_SHORT).show();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}

    private final class ColorControl {
        final LinearLayout root;
        final View swatch;
        final TextView value;
        final String label;
        String color;
        int opacity;

        ColorControl(String label, String initial, int initialOpacity) {
            this.label = label;
            root = vertical();
            root.addView(text(label, 12, Color.rgb(90, 90, 90)), matchWrap());
            LinearLayout row = horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(8), dp(12), dp(8));
            row.setBackground(roundRect(Color.WHITE, 4, Color.rgb(215, 215, 215), 1));
            swatch = new View(MainActivity.this);
            LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(dp(32), dp(32));
            swatchParams.rightMargin = dp(12);
            row.addView(swatch, swatchParams);
            value = text("", 14, Color.rgb(45, 45, 45));
            value.setTypeface(Typeface.MONOSPACE);
            row.addView(value, weighted());
            TextView action = text("选择颜色", 12, BLUE);
            row.addView(action, wrapWrap());
            root.addView(row, margins(matchWrap(), 0, dp(4), 0, 0));
            root.setFocusable(true);
            color = normalizeHexColor(initial, "#D4AF37");
            opacity = Math.max(0, Math.min(100, initialOpacity));
            render();
        }

        void setSelection(ColorSelection selection) {
            color = normalizeHexColor(selection.color, "#D4AF37");
            opacity = Math.max(0, Math.min(100, selection.opacity));
            render();
        }

        void setOpacity(int selectedOpacity) {
            opacity = Math.max(0, Math.min(100, selectedOpacity));
            render();
        }

        private void render() {
            int transparency = 100 - opacity;
            value.setText(opacity == 0 ? "白色（透明）" : color);
            int parsed = Color.parseColor(color);
            int display = opacity == 0 ? Color.WHITE : Color.argb(
                    Math.round(opacity * 2.55f), Color.red(parsed), Color.green(parsed), Color.blue(parsed));
            swatch.setBackground(roundRect(display, 4, Color.rgb(190, 190, 190), 1));
            root.setContentDescription(label + " " + value.getText() + " 透明度 " + transparency + "%");
        }
    }

    private static final class ColorSelection {
        final String color;
        final int opacity;

        ColorSelection(String color, int opacity) {
            this.color = color;
            this.opacity = opacity;
        }
    }

    private interface ColorSelectionHandler { void accept(ColorSelection selection); }
}
