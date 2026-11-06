package com.test.volumeprobe;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Method;

public class MainHook extends XposedModule {
    private static final String TAG = "VolumeProbe";

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

                // 🌟 直接去拿原生设置按钮！这是你之前反编译找到的 ID
                View nativeSettingsBtn = rootContainer.findViewById(0x7f0a09d6);
                
                if (nativeSettingsBtn != null) {
                    Log.d(TAG, "【成功找到】原生的设置按钮，准备劫持它！");
                    
                    // 覆盖它原本的点击事件
                    nativeSettingsBtn.setOnClickListener(v -> {
                        Log.d(TAG, "👆 点击了原生设置按钮，准备弹面板！");
                        
                        // 1. 关闭原生音量面板
                        try {
                            dialogInstance.getClass().getMethod("dismiss").invoke(dialogInstance);
                        } catch (Throwable t) {}

                        // 2. 弹出我们写的分应用面板
                        PanelUI.show(context);
                    });
                } else {
                    Log.e(TAG, "❌ 没找到 0x7f0a09d6，可能面板还没加载完");
                }

            } catch (Throwable t) {
                Log.e(TAG, "【异常】劫持 UI 失败: " + t);
            }
            return result;
        }
    }
}
