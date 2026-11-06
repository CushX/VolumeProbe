package com.test.volumeprobe;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
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

                // 🌟 拿最外层的白色面板容器
                int dialogRootId = context.getResources().getIdentifier("volume_dialog", "id", "com.android.systemui");
                // 🌟 拿原生静音按钮，作为位置参考
                int muteBtnId = context.getResources().getIdentifier("volume_dialog_top_section_container", "id", "com.android.systemui");

                if (dialogRootId != 0 && muteBtnId != 0) {
                    View dialogRoot = rootContainer.findViewById(dialogRootId);
                    View muteContainer = rootContainer.findViewById(muteBtnId);
                    
                    if (dialogRoot instanceof ViewGroup && muteContainer != null) {
                        ViewGroup parent = (ViewGroup) dialogRoot;
                        
                        if (parent.findViewWithTag(TAG_BUTTON) != null) return result; 

                        // 创建图标
                        ImageView myButton = new ImageView(context);
                        myButton.setTag(TAG_BUTTON); 
                        myButton.setImageResource(android.R.drawable.ic_menu_manage);
                        myButton.setColorFilter(Color.parseColor("#6750A4")); 
                        myButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                        
                        // 圆形背景，防止点击穿透
                        GradientDrawable shape = new GradientDrawable();
                        shape.setShape(GradientDrawable.OVAL);
                        shape.setColor(Color.parseColor("#EADDFF"));
                        myButton.setBackground(shape);
                        
                        // 🌟 暴力防穿透：拦截所有触摸事件，让系统认为这里有一堵墙
                        myButton.setOnTouchListener((v, event) -> {
                            if (event.getAction() == MotionEvent.ACTION_UP) {
                                v.performClick();
                            }
                            return true; // 吃掉所有事件，绝不传出去
                        });

                        myButton.setOnClickListener(v -> {
                            Log.d(TAG, "👆 点击了分应用图标！");
                            try {
                                dialogInstance.getClass().getMethod("dismiss").invoke(dialogInstance);
                            } catch (Throwable t) {}
                            PanelUI.show(context);
                        });

                        // 🌟 手动定位：找静音按钮底部的位置
                        // 获取静音容器底部的 Y 坐标
                        int muteBottomY = muteContainer.getBottom();
                        
                        // 使用绝对布局参数放进去
                        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                                (int) (36 * context.getResources().getDisplayMetrics().density), 
                                (int) (36 * context.getResources().getDisplayMetrics().density)
                        );
                        
                        // 居中，并在静音键正下方
                        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                        
                        // 因为在垂直面板中，muteContainer 往往是在最顶部。
                        // 我们可以通过 marginTop 把它往下推。先设定一个基础值，如果位置不对，可以微调这里
                        lp.topMargin = (int) (80 * context.getResources().getDisplayMetrics().density); 
                        
                        myButton.setLayoutParams(lp);

                        // 确保它被加到最顶层
                        parent.addView(myButton);
                        parent.bringChildToFront(myButton);
                        
                        Log.d(TAG, "🔥【注入成功】带防穿透功能的图标已悬浮！");
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "【异常】注入 UI 失败: " + t);
            }
            return result;
        }
    }
}
