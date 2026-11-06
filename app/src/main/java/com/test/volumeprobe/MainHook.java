package com.test.volumeprobe;

import android.content.Context;
import android.graphics.Color;
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

                int topContainerId = context.getResources().getIdentifier("volume_dialog_top_section_container", "id", "com.android.systemui");
                int nativeSettingsId = context.getResources().getIdentifier("volume_dialog_settings", "id", "com.android.systemui");
                
                if (topContainerId != 0 && nativeSettingsId != 0) {
                    View topContainer = rootContainer.findViewById(topContainerId);
                    View nativeSettingsBtn = rootContainer.findViewById(nativeSettingsId);
                    
                    if (topContainer instanceof ViewGroup && nativeSettingsBtn != null) {
                        ViewGroup parent = (ViewGroup) topContainer;
                        if (parent.findViewWithTag(TAG_BUTTON) != null) return result; 

                        ImageView myButton = new ImageView(context);
                        myButton.setTag(TAG_BUTTON); 
                        myButton.setImageResource(android.R.drawable.ic_menu_manage);
                        myButton.setColorFilter(Color.parseColor("#6750A4")); 
                        myButton.setBackgroundResource(android.R.drawable.list_selector_background);

                        ViewGroup.LayoutParams nativeLp = nativeSettingsBtn.getLayoutParams();
                        int targetWidth = nativeLp != null ? nativeLp.width : (int) (48 * context.getResources().getDisplayMetrics().density);
                        int targetHeight = nativeLp != null ? nativeLp.height : (int) (48 * context.getResources().getDisplayMetrics().density);

                        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(targetWidth, targetHeight);
                        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                        lp.bottomMargin = (int) (4 * context.getResources().getDisplayMetrics().density);
                        myButton.setLayoutParams(lp);

                        myButton.setOnClickListener(v -> {
                            try {
                                dialogInstance.getClass().getMethod("dismiss").invoke(dialogInstance);
                            } catch (Throwable t) {}
                            // 🌟 调用拆出去的面板 UI
                            PanelUI.show(context);
                        });

                        parent.addView(myButton);
                        Log.d(TAG, "🔥【注入成功】图标已注入！");
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "【异常】注入 UI 失败: " + t);
            }
            return result;
        }
    }
}
