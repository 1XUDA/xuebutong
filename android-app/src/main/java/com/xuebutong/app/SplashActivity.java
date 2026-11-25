package com.xuebutong.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

public final class SplashActivity extends Activity {
    private static final long SPLASH_DURATION_MS = 1400;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable openMain = () -> {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
        overridePendingTransition(0, 0);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (!isTaskRoot()
                && Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            finish();
            overridePendingTransition(0, 0);
            return;
        }
        getWindow().setStatusBarColor(Color.rgb(250, 250, 250));
        getWindow().setNavigationBarColor(Color.rgb(250, 250, 250));
        setContentView(buildSplash());
        handler.postDelayed(openMain, SPLASH_DURATION_MS);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(openMain);
        super.onDestroy();
    }

    private FrameLayout buildSplash() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(250, 250, 250));

        ImageView artwork = new ImageView(this);
        artwork.setImageResource(com.xuebutong.app.R.drawable.splash);
        artwork.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(artwork, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        TextView slogan = new TextView(this);
        slogan.setText("今天学习通了吗？\n依旧学不通！！！\n打开学不通，知识全放空\n翻看千百遍，依旧看不懂");
        slogan.setTextColor(Color.rgb(32, 33, 36));
        slogan.setTextSize(16);
        slogan.setGravity(Gravity.START);
        slogan.setLineSpacing(0, 1.08f);

        FrameLayout.LayoutParams sloganParams = new FrameLayout.LayoutParams(
                dp(310), FrameLayout.LayoutParams.WRAP_CONTENT);
        sloganParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        sloganParams.bottomMargin = dp(60);
        root.addView(slogan, sloganParams);
        return root;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
