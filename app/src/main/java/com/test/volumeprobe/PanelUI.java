package com.test.volumeprobe;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PanelUI {
    private static final String TAG = "VolumeProbe";

    public static void show(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return;

        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int maxPanelWidth = (int) (screenWidth * 0.5f);

        MaxWidthLinearLayout mainLayout = new MaxWidthLinearLayout(context, maxPanelWidth);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.WHITE);
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
        mainLayout.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(context);
        title.setText("🎛️ 应用独立音量");
        title.setTextSize(16);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.START);
        title.setPadding(0, 0, 0, pad);
        mainLayout.addView(title);

        HorizontalScrollView scrollView = new HorizontalScrollView(context);
        scrollView.setHorizontalScrollBarEnabled(false);

        LinearLayout trackLayout = new LinearLayout(context);
        trackLayout.setOrientation(LinearLayout.HORIZONTAL);
        trackLayout.setGravity(Gravity.BOTTOM);

        int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        // 最左侧：系统总音量
        trackLayout.addView(createVolumeColumn(
                context, "系统总音量", null, curVol, maxVol, true, (progress) -> {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
                }
        ));

        View divider = new View(context);
        divider.setBackgroundColor(Color.parseColor("#E0E0E0"));
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                (int) (1 * context.getResources().getDisplayMetrics().density),
                (int) (180 * context.getResources().getDisplayMetrics().density));
        dividerLp.setMargins(pad, 0, pad, 0);
        divider.setLayoutParams(dividerLp);
        trackLayout.addView(divider);

        // 右侧：活跃应用
        try {
            List<AudioPlaybackConfiguration> configs = audioManager.getActivePlaybackConfigurations();
            Set<Integer> activeUids = new HashSet<>();
            for (AudioPlaybackConfiguration config : configs) {
                if (config.isActive()) activeUids.add(config.getClientUid());
            }

            PackageManager pm = context.getPackageManager();
            for (int uid : activeUids) {
                String[] packages = pm.getPackagesForUid(uid);
                if (packages != null && packages.length > 0) {
                    String pkgName = packages[0];
                    if (pkgName.equals("com.android.systemui") || pkgName.equals("android")) continue;

                    try {
                        ApplicationInfo appInfo = pm.getApplicationInfo(pkgName, 0);
                        String appName = pm.getApplicationLabel(appInfo).toString();
                        Drawable icon = pm.getApplicationIcon(appInfo);

                        int appVol = VolumeData.getVolume(pkgName, curVol);
                        
                        trackLayout.addView(createVolumeColumn(
                                context, appName, icon, appVol, maxVol, false, (progress) -> {
                                    VolumeData.setVolume(pkgName, progress);
                                    Log.d(TAG, "记录 " + appName + " 音量: " + progress);
                                }
                        ));
                    } catch (PackageManager.NameNotFoundException ignored) {}
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "读取活跃应用失败: " + t);
        }

        scrollView.addView(trackLayout);
        mainLayout.addView(scrollView);

        AlertDialog.Builder builder = new AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setView(mainLayout);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        dialog.show();
    }

    private static View createVolumeColumn(Context context, String title, Drawable icon, int progress, int max, boolean isSystem, java.util.function.Consumer<Integer> onProgressChanged) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                (int) (56 * context.getResources().getDisplayMetrics().density),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        colLp.setMargins(8, 0, 8, 0);
        column.setLayoutParams(colLp);

        VerticalSeekBar verticalSeekBar = new VerticalSeekBar(context);
        verticalSeekBar.setMax(max);
        verticalSeekBar.setProgress(progress);
        LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                (int) (150 * context.getResources().getDisplayMetrics().density)
        );
        verticalSeekBar.setLayoutParams(seekLp);
        
        verticalSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) { 
                if (fromUser && onProgressChanged != null) onProgressChanged.accept(p); 
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        column.addView(verticalSeekBar);

        // 2. 下方图标
        ImageView iv = new ImageView(context);
        if (icon != null) {
            iv.setImageDrawable(icon);
        } else {
            iv.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
            iv.setColorFilter(Color.GRAY);
        }
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(
                (int) (32 * context.getResources().getDisplayMetrics().density),
                (int) (32 * context.getResources().getDisplayMetrics().density)
        );
        ivLp.topMargin = (int) (12 * context.getResources().getDisplayMetrics().density);
        iv.setLayoutParams(ivLp);
        column.addView(iv);

        // 3. 下方文字
        TextView tv = new TextView(context);
        tv.setText(isSystem ? "系统" : title); 
        tv.setTextSize(10);
        tv.setTextColor(Color.GRAY);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tvLp.topMargin = (int) (4 * context.getResources().getDisplayMetrics().density);
        tv.setLayoutParams(tvLp);
        column.addView(tv);

        return column;
    }

    public static class MaxWidthLinearLayout extends LinearLayout {
        private int maxWidth;

        public MaxWidthLinearLayout(Context context, int maxWidth) {
            super(context);
            this.maxWidth = maxWidth;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int widthMode = MeasureSpec.getMode(widthMeasureSpec);
            int widthSize = MeasureSpec.getSize(widthMeasureSpec);
            if (widthSize > maxWidth) {
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, widthMode);
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    public static class VerticalSeekBar extends SeekBar {
        public VerticalSeekBar(Context context) {
            super(context);
        }

        @Override
        protected synchronized void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(heightMeasureSpec, widthMeasureSpec);
            setMeasuredDimension(getMeasuredHeight(), getMeasuredWidth());
        }

        @Override
        protected void onDraw(Canvas c) {
            c.rotate(-90);
            c.translate(-getHeight(), 0);
            super.onDraw(c);
        }

        private OnSeekBarChangeListener mOnSeekBarChangeListener;

        @Override
        public void setOnSeekBarChangeListener(OnSeekBarChangeListener l) {
            super.setOnSeekBarChangeListener(l);
            mOnSeekBarChangeListener = l;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!isEnabled()) return false;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                case MotionEvent.ACTION_UP:
                    float scale = getMax() / (float) getHeight();
                    int progress = (int) (getMax() - (event.getY() * scale));
                    if (progress < 0) progress = 0;
                    if (progress > getMax()) progress = getMax();
                    setProgress(progress);
                    if (mOnSeekBarChangeListener != null) {
                        mOnSeekBarChangeListener.onProgressChanged(this, progress, true);
                    }
                    return true;
            }
            return true;
        }
    }
}
