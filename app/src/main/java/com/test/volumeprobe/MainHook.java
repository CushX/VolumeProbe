package com.test.volumeprobe;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Method;

public class MainHook extends XposedModule {
    private static final String TAG = "VolumeProbe";
    
    // 静态开关，确保 UI 树只打印一次，按钮只注入一次
    private static boolean hasDumped = false;
    private static boolean hasInjected = false;

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

            // 🌟 这次挂载 onStart，保证每次弹面板必定能抓到
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
            Object result = chain.proceed(); // 先让系统正常显示面板
            
            try {
                if (hasInjected && hasDumped) return result; // 如果已经做过，直接返回

                Object dialogInstance = chain.getThisObject(); 
                Method requireViewByIdMethod = dialogInstance.getClass().getMethod("requireViewById", int.class);
                
                // 获取我们在 smali 里发现的根视图 ID
                Object containerView = requireViewByIdMethod.invoke(dialogInstance, 0x7f0a09cc);
                
                if (!(containerView instanceof ViewGroup)) return result;
                ViewGroup rootContainer = (ViewGroup) containerView;

                // 🌟 1. 打印整棵 UI 树
                if (!hasDumped) {
                    Log.d(TAG, "========== [UI树] 开始打印 ==========");
                    dumpViewHierarchy(rootContainer, 0);
                    Log.d(TAG, "========== [UI树] 打印结束 ==========");
                    hasDumped = true;
                }

                // 🌟 2. 注入按钮
                if (!hasInjected) {
                    Context context = rootContainer.getContext();
                    TextView myButton = new TextView(context);
                    myButton.setText("⚙️ 测试注入");
                    myButton.setTextColor(Color.WHITE);
                    myButton.setBackgroundColor(Color.parseColor("#2196F3")); // 蓝色
                    myButton.setTextSize(12);
                    int pad = (int) (8 * context.getResources().getDisplayMetrics().density);
                    myButton.setPadding(pad, pad, pad, pad);

                    rootContainer.addView(myButton);
                    Log.d(TAG, "🔥 按钮已塞入根容器！");
                    hasInjected = true;
                }

            } catch (Throwable t) {
                Log.e(TAG, "注入 UI 失败: " + t);
            }

            return result;
        }
    }

    // 递归打印 View 树
    private static void dumpViewHierarchy(View view, int depth) {
        if (view == null) return;

        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) indent.append("  ");

        String viewName = view.getClass().getSimpleName();
        String idName = "NO_ID";
        try {
            int id = view.getId();
            if (id != View.NO_ID) {
                idName = view.getResources().getResourceEntryName(id);
            }
        } catch (Throwable ignored) {}

        Log.d(TAG, "[UI树] " + indent + "-> " + viewName + " (id: " + idName + ")");

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                dumpViewHierarchy(group.getChildAt(i), depth + 1);
            }
        }
    }

    public MainHook() {}
}
