package com.test.volumeprobe;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Method;

public class MainHook extends XposedModule {
    private static final String TAG = "VolumeProbe";
    private static final String TAG_BUTTON = "MyCustomVolumeButton";

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        super.onModuleLoaded(param);
        log(Log.INFO, TAG, "=== 模块已加载 ===");
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        if (!param.getPackageName().equals("com.android.systemui")) return;

        try {
            ClassLoader cl = param.getDefaultClassLoader();
            Class<?> clazz = cl.loadClass("com.android.systemui.volume.dialog.VolumeDialog");
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals("onStart")) {
                    hook(method).intercept(new UIInjectorHooker());
                    log(Log.INFO, TAG, "== 成功挂载 onStart ==");
                }
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "挂载异常: " + t);
        }
    }

    private static class UIInjectorHooker implements XposedInterface.Hooker {
        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            Object result = chain.proceed();
            try {
                Object dialogInstance = chain.getThisObject(); 
                Context context = (Context) dialogInstance.getClass().getMethod("getContext").invoke(dialogInstance);
                Object window = dialogInstance.getClass().getMethod("getWindow").invoke(dialogInstance);
                if (window == null) return result;
                Object decorView = window.getClass().getMethod("getDecorView").invoke(window);
                if (!(decorView instanceof ViewGroup)) return result;
                ViewGroup rootContainer = (ViewGroup) decorView;

                int bottomContainerId = context.getResources().getIdentifier("volume_dialog_bottom_section_container", "id", "com.android.systemui");
                
                if (bottomContainerId != 0) {
                    View bottomContainer = rootContainer.findViewById(bottomContainerId);
                    
                    if (bottomContainer instanceof LinearLayout) {
                        LinearLayout parent = (LinearLayout) bottomContainer;
                        if (parent.findViewWithTag(TAG_BUTTON) != null) return result; 

                        ImageButton myButton = new ImageButton(context);
                        myButton.setTag(TAG_BUTTON); 
                        myButton.setImageResource(android.R.drawable.ic_menu_manage);
                        myButton.setColorFilter(Color.parseColor("#6750A4")); 
                        myButton.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
                        
                        GradientDrawable shape = new GradientDrawable();
                        shape.setShape(GradientDrawable.OVAL);
                        shape.setColor(Color.parseColor("#EADDFF"));
                        myButton.setBackground(shape);

                        int btnSize = (int) (36 * context.getResources().getDisplayMetrics().density);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(btnSize, btnSize);
                        lp.gravity = Gravity.CENTER_HORIZONTAL;
                        lp.topMargin = (int) (8 * context.getResources().getDisplayMetrics().density);
                        lp.bottomMargin = (int) (4 * context.getResources().getDisplayMetrics().density);
                        myButton.setLayoutParams(lp);

                        // 🌟 探针 1：监控触摸事件
                        myButton.setOnTouchListener((v, event) -> {
                            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                                Log.d(TAG, "[触摸探针] 按下了图标！(ACTION_DOWN)");
                                return true; 
                            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                                Log.d(TAG, "[触摸探针] 松开了图标！(ACTION_UP)");
                                v.performClick(); 
                                return true;
                            }
                            return false;
                        });

                        // 🌟 探针 2：监控点击逻辑
                        myButton.setOnClickListener(v -> {
                            Log.d(TAG, "[点击探针] 成功进入 onClick 内部！");
                            
                            try {
                                Log.d(TAG, "[点击探针] 准备调用原生 dismiss() 关闭面板...");
                                dialogInstance.getClass().getMethod("dismiss").invoke(dialogInstance);
                                Log.d(TAG, "[点击探针] 原生面板已关闭。");
                            } catch (Throwable t) {
                                Log.e(TAG, "[点击探针] 原生面板关闭失败: " + t);
                            }

                            // 延迟弹窗
                            Log.d(TAG, "[点击探针] 启动 300ms 延迟任务，准备弹窗...");
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                Log.d(TAG, "[弹窗探针] 延迟时间到，开始执行弹窗代码！");
                                
                                Context appCtx = context.getApplicationContext();
                                WindowManager wm = (WindowManager) appCtx.getSystemService(Context.WINDOW_SERVICE);
                                if (wm == null) {
                                    Log.e(TAG, "[弹窗探针] 失败：WindowManager 获取为空！");
                                    return;
                                }

                                TextView tv = new TextView(appCtx);
                                tv.setText("测试弹窗\n\n点击我关闭");
                                tv.setTextSize(30);
                                tv.setTextColor(Color.WHITE);
                                tv.setBackgroundColor(Color.BLUE);
                                tv.setGravity(Gravity.CENTER);
                                
                                // 为了防止应用层权限报错，我们用 TYPE_SYSTEM_ERROR，这是系统级最暴力的窗口
                                WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                                        (int) (300 * appCtx.getResources().getDisplayMetrics().density), 
                                        (int) (300 * appCtx.getResources().getDisplayMetrics().density),
                                        WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                                        PixelFormat.TRANSLUCENT
                                );
                                p.gravity = Gravity.CENTER;
                                
                                tv.setOnClickListener(view -> {
                                    Log.d(TAG, "[弹窗探针] 用户点击了测试弹窗，准备移除。");
                                    try {
                                        wm.removeView(tv);
                                    } catch (Throwable ignored) {}
                                });

                                try {
                                    Log.d(TAG, "[弹窗探针] 准备调用 wm.addView()...");
                                    wm.addView(tv, p);
                                    Log.d(TAG, "[弹窗探针] 🎉 弹窗添加成功！你应该能看到蓝色方块了！");
                                } catch (Throwable t) {
                                    Log.e(TAG, "[弹窗探针] ❌ 弹窗添加失败: " + t);
                                }
                            }, 300); 
                        });

                        parent.addView(myButton, 0);
                        Log.d(TAG, "🔥【注入成功】图标已塞入底部容器！");
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "【异常】注入 UI 失败: " + t);
            }
            return result;
        }
    }
}
