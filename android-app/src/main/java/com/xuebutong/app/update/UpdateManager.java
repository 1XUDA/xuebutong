package com.xuebutong.app.update;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UpdateManager {
    public static final String FEED="https://xudaco.online/xuebutong-site/releases/latest.json";
    private static final ExecutorService WORKERS=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    public interface Listener { void done(String message); }
    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences("app_update",Context.MODE_PRIVATE);
    }
    public static void check(Context c, boolean manual, Listener listener) {
        Context context=c.getApplicationContext();
        SharedPreferences state=prefs(context);
        if (!manual && (!state.getBoolean("auto_check",true)
                || System.currentTimeMillis()-state.getLong("last_check",0)<12*60*60*1000L)) return;
        WORKERS.execute(()->{
            String message;
            try {
                HttpURLConnection con=(HttpURLConnection)new URL(FEED).openConnection();
                con.setConnectTimeout(12000);con.setReadTimeout(12000);
                con.setRequestProperty("Cache-Control","no-cache");con.setInstanceFollowRedirects(false);
                JSONObject json;
                try {
                    if(con.getResponseCode()!=200)throw new IllegalStateException("更新服务暂不可用");
                    try(InputStream in=con.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                        byte[] bytes=new byte[4096];int n;
                        while((n=in.read(bytes))!=-1){out.write(bytes,0,n);if(out.size()>65536)throw new IllegalStateException("版本信息异常");}
                        json=new JSONObject(out.toString("UTF-8"));
                    }
                } finally {con.disconnect();}
                int version=json.getInt("versionCode");
                String url=json.getString("apkUrl"),hash=json.getString("sha256"),name=json.getString("versionName");
                long size=json.getLong("size");
                Uri uri=Uri.parse(url);
                if(!"https".equalsIgnoreCase(uri.getScheme()) || !"xudaco.online".equalsIgnoreCase(uri.getHost())
                        || uri.getUserInfo()!=null || uri.getPort()!=-1 || !uri.getPath().startsWith("/xuebutong-site/")
                        || !hash.matches("[a-fA-F0-9]{64}") || size<=0 || size>500000000L || name.length()>40)
                    throw new IllegalStateException("版本信息异常");
                long installed=Build.VERSION.SDK_INT>=28?context.getPackageManager().getPackageInfo(context.getPackageName(),0).getLongVersionCode():context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionCode;
                state.edit().putLong("last_check",System.currentTimeMillis()).apply();
                if(version<=installed) {
                    message="当前已是最新版本";
                    state.edit().remove("available").apply();
                } else {
                    state.edit().putString("available",json.toString()).apply();
                    message="发现新版本 "+name;
                    if(state.getBoolean("auto_download",false))startDownload(context,json);
                }
            } catch(Exception e) { message=e instanceof IllegalStateException?e.getMessage():"检查更新失败，请检查网络"; }
            final String result=message;
            if(listener!=null)MAIN.post(()->listener.done(result));
        });
    }
    public static synchronized void startDownload(Context c, JSONObject info) throws Exception {
        Context context=c.getApplicationContext();
        SharedPreferences state=prefs(context);
        int version=info.getInt("versionCode");
        if(state.getLong("download_id",-1)!=-1 && state.getInt("download_version",-1)==version) return;
        DownloadManager dm=(DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE);
        String filename="update-"+version+".apk";
        File directory=context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if(directory==null)throw new IllegalStateException("存储空间不可用");
        File target=new File(directory,filename);
        if(target.exists() && !target.delete())throw new IllegalStateException("无法准备下载");
        DownloadManager.Request req=new DownloadManager.Request(Uri.parse(info.getString("apkUrl")));
        req.setTitle("学不通更新 "+info.getString("versionName"));
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        req.setMimeType("application/vnd.android.package-archive");
        if(state.getBoolean("wifi_only",true))req.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);
        req.setAllowedOverMetered(!state.getBoolean("wifi_only",true));
        req.setAllowedOverRoaming(false);
        req.setDestinationInExternalFilesDir(context,Environment.DIRECTORY_DOWNLOADS,filename);
        long old=state.getLong("download_id",-1);
        long id=dm.enqueue(req);
        state.edit().putLong("download_id",id).putInt("download_version",version).putString("download_hash",info.getString("sha256"))
            .putLong("download_size",info.getLong("size")).commit();
        if(old!=-1 && old!=id)dm.remove(old);
    }
    public static File verifiedFile(Context c) throws Exception {
        SharedPreferences state=prefs(c);
        long id=state.getLong("download_id",-1);
        if(id<0)throw new IllegalStateException("请先下载更新");
        DownloadManager dm=(DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE);
        try(android.database.Cursor cur=dm.query(new DownloadManager.Query().setFilterById(id))){
            if(cur==null || !cur.moveToFirst())throw new IllegalStateException("下载记录不存在，请重新下载");
            int status=cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if(status!=DownloadManager.STATUS_SUCCESSFUL)throw new IllegalStateException(status==DownloadManager.STATUS_FAILED?"下载失败，请重新下载":"更新正在下载中");
        }
        int version=state.getInt("download_version",-1);
        File file=new File(c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),"update-"+version+".apk");
        if(!file.isFile() || file.length()!=state.getLong("download_size",-1))throw new IllegalStateException("安装包不完整");
        MessageDigest sha=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)sha.update(b,0,n);}
        StringBuilder hex=new StringBuilder();for(byte b:sha.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        if(!hex.toString().equalsIgnoreCase(state.getString("download_hash","")))throw new IllegalStateException("安装包校验失败");
        PackageManager pm=c.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo candidate=pm.getPackageArchiveInfo(file.getAbsolutePath(),flags),current=pm.getPackageInfo(c.getPackageName(),flags);
        if(candidate==null || !c.getPackageName().equals(candidate.packageName))throw new IllegalStateException("安装包名称不一致");
        long received=Build.VERSION.SDK_INT>=28?candidate.getLongVersionCode():candidate.versionCode;
        long installed=Build.VERSION.SDK_INT>=28?current.getLongVersionCode():current.versionCode;
        if(received!=version || received<=installed)throw new IllegalStateException("安装包版本不匹配");
        Signature[] a=Build.VERSION.SDK_INT>=28?candidate.signingInfo.getApkContentsSigners():candidate.signatures;
        Signature[] b=Build.VERSION.SDK_INT>=28?current.signingInfo.getApkContentsSigners():current.signatures;
        if(a==null || b==null || a.length!=b.length || !Arrays.asList(a).containsAll(Arrays.asList(b)))throw new IllegalStateException("安装包签名不匹配");
        return file;
    }
}
