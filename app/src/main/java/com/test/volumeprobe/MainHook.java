package com.test.volumeprobe;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
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

                // 🌟 核心：回到顶部的容器！这个容器横跨整个白色面板，不会再跑偏。
                int topContainerId = context.getResources().getIdentifier("volume_dialog_top_section_container", "id", "com.android.systemui");
                
                if (topContainerId != 0) {
                    View topContainer = rootContainer.findViewById(topContainerId);
                    
                    if (topContainer instanceof FrameLayout) {
                        FrameLayout parent = (FrameLayout) topContainer;
                        
                        if (parent.findViewWithTag(TAG_BUTTON) != null) return result; 

                        // 创建图标按钮
                        ImageView myButton = new ImageView(context);
                        myButton.setTag(TAG_BUTTON); 
                        myButton.setImageResource(android.R.drawable.ic_menu_manage);
                        myButton.setColorFilter(Color.parseColor("#6750A4")); 
                        myButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                        
                        // 圆形背景，扩大点击范围，防止手指点偏导致点穿
                        GradientDrawable shape = new GradientDrawable();
                        shape.setShape(GradientDrawable.OVAL);
                        shape.setColor(Color.parseColor("#EADDFF"));
                        myButton.setBackground(shape);
                        
                        int pad = (int) (4 * context.getResources().getDisplayMetrics().density);
                        myButton.setPadding(pad, pad, pad, pad);
                        
                        // 🌟 极其关键：阻断触摸事件，防止点穿导致面板消失
                        myButton.setClickable(true);
                        myButton.setFocusable(true);
                        myButton.setOnTouchListener((v, event) -> false); // 消费掉事件

                        // 🌟 对齐到顶部容器的【最底部居中】
                        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                                (int) (32 * context.getResources().getDisplayMetrics().density), 
                                (int) (32 * context.getResources().getDisplayMetrics().density)
                        );
                        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                        // 留出一点 margin，免得和下方的滑条贴在一起
                        lp.bottomMargin = (int) (8 * context.getResources().getDisplayMetrics().density);
                        
                        myButton.setLayoutParams(lp);

                        // 点击事件
                        myButton.setOnClickListener(v -> {
                            Log.d(TAG, "👆 点击了分应用图标！");
                            try {
                                dialogInstance.getClass().getMethod("dismiss").invoke(dialogInstance);
                            } catch (Throwable t) {}
                            PanelUI.show(context);
                        });

                        parent.addView(myButton);
                        Log.d(TAG, "🔥【注入成功】图标已放在顶部容器底部居中！");
                    }
                } else {
                    Log.e(TAG, "❌ 找不到 volume_dialog_top_section_container");
                }
            } catch (Throwable t) {
                Log.e(TAG, "【异常】注入 UI 失败: " + t);
            }
            return result;
        }
    }
}
