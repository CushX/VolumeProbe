package com.test.volumeprobe;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;

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

                // 1. 找容器并注入按钮
                int bottomContainerId = context.getResources().getIdentifier("volume_dialog_bottom_section_container", "id", "com.android.systemui");
                if (bottomContainerId == 0) return result;
                
                View bottomContainer = rootContainer.findViewById(bottomContainerId);
                if (!(bottomContainer instanceof LinearLayout)) return result;
                
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

                myButton.setOnClickListener(v -> {
                    Log.d(TAG, "👆 点击了分应用图标！");
                    try {
                        dialogInstance.getClass().getMethod("dismiss").invoke(dialogInstance);
                    } catch (Throwable t) {}
                    PanelUI.show(context);
                });

                parent.addView(myButton, 0);
                Log.d(TAG, "🔥【注入成功】图标已塞入底部容器！");

                // =========================================================
                // 🌟 2. 终极杀招：把我们的按钮加进底层的触摸白名单集合！
                // =========================================================
                // 根据你发的 Smali 代码，白名单存放在 ViewModel 的 touchableBoundsViews 字段中
                Object viewModel = findObjectByClassName(dialogInstance, "com.android.systemui.volume.dialog.ui.viewmodel.VolumeDialogViewModel");
                
                if (viewModel != null) {
                    Field touchableBoundsViewsField = viewModel.getClass().getDeclaredField("touchableBoundsViews");
                    touchableBoundsViewsField.setAccessible(true);
                    Object collectionObj = touchableBoundsViewsField.get(viewModel);
                    
                    if (collectionObj instanceof Collection) {
                        @SuppressWarnings("unchecked")
                        Collection<View> touchableViews = (Collection<View>) collectionObj;
                        touchableViews.add(myButton);
                        Log.d(TAG, "🔥🔥【白名单注入成功】按钮已加入合法触摸区域集合！");
                    }
                } else {
                    Log.e(TAG, "❌ 没找到 ViewModel，无法注入白名单");
                }

            } catch (Throwable t) {
                Log.e(TAG, "【异常】注入 UI 失败: " + t);
            }
            return result;
        }
    }

    // 🌟 递归寻找特定类名的成员变量（用来在深层的嵌套里揪出 ViewModel）
    private static Object findObjectByClassName(Object obj, String targetClassName) {
        if (obj == null) return null;
        if (obj.getClass().getName().equals(targetClassName)) return obj;

        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    // 跳过基本数据类型和常见类，防止死循环
                    if (field.getType().isPrimitive() || field.getType().getName().startsWith("java.")) continue;
                    
                    field.setAccessible(true);
                    Object fieldValue = field.get(obj);
                    if (fieldValue != null) {
                        if (fieldValue.getClass().getName().equals(targetClassName)) {
                            return fieldValue;
                        }
                        // 如果没找到，继续往下一层找
                        Object result = findObjectByClassName(fieldValue, targetClassName);
                        if (result != null) return result;
                    }
                } catch (Throwable ignored) {}
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }
}
