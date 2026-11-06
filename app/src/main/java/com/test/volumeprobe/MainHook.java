package com.test.volumeprobe;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
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

                        ImageView myButton = new ImageView(context);
                        myButton.setTag(TAG_BUTTON); 
                        myButton.setImageResource(android.R.drawable.ic_menu_manage);
                        myButton.setColorFilter(Color.parseColor("#6750A4")); 
                        myButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                        
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

                        myButton.setOnClickListener(v -> {
                            // 🌟 探针：先弹个 Toast，证明点击确实生效了
                            Toast.makeText(context, "点击成功，准备弹面板", Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "👆 点击了分应用图标！");

                            // 关闭原生面板
                            try {
                                dialogInstance.getClass().getMethod("dismiss").invoke(dialogInstance);
                            } catch (Throwable t) {
                                Log.e(TAG, "关闭原生面板失败", t);
                            }

                            // 调用面板 UI
                            try {
                                PanelUI.show(context);
                            } catch (Throwable t) {
                                Log.e(TAG, "❌ PanelUI 弹窗崩溃: " + t);
                                Toast.makeText(context, "弹窗失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });

                        parent.addView(myButton, 0);
                        Log.d(TAG, "🔥【注入成功】图标已稳稳塞入底部容器最上方！");
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "【异常】注入 UI 失败: " + t);
            }
            return result;
        }
    }
}
