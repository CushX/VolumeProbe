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
                if (method.getName().equals("onCreate")) {
                    hook(method).intercept(new UIInjectorHooker());
                    log(Log.INFO, TAG, "== 成功挂载 onCreate ==");
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
                Method requireViewByIdMethod = dialogInstance.getClass().getMethod("requireViewById", int.class);
                
                // 获取我们在 smali 里发现的根视图 ID
                Object containerView = requireViewByIdMethod.invoke(dialogInstance, 0x7f0a09cc);
                
                if (!(containerView instanceof ViewGroup)) return result;
                ViewGroup rootContainer = (ViewGroup) containerView;

                // 🌟 1. 打印整棵 UI 树的层级和 ID
                Log.d(TAG, "========== [UI树] 开始打印 ==========");
                dumpViewHierarchy(rootContainer, 0);
                Log.d(TAG, "========== [UI树] 打印结束 ==========");

                // 🌟 2. 依然先塞一个测试按钮（方便你视觉上确认）
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

            } catch (Throwable t) {
                Log.e(TAG, "注入 UI 失败: " + t);
            }

            return result;
        }
    }

    // 🌟 递归打印 View 树
    private static void dumpViewHierarchy(View view, int depth) {
        if (view == null) return;

        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) indent.append("  "); // 缩进，体现层级

        String viewName = view.getClass().getSimpleName();
        String idName = "NO_ID";
        try {
            int id = view.getId();
            if (id != View.NO_ID) {
                // 把数字 ID 转换成真正的名字，比如 settings_button
                idName = view.getResources().getResourceEntryName(id);
            }
        } catch (Throwable ignored) {}

        // 带有 [UI树] 标签，方便你在日志里搜索
        Log.d(TAG, "[UI树] " + indent + "-> " + viewName + " (id: " + idName + ")");

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                dumpViewHierarchy(group.getChildAt(i), depth + 1); // 深入下一层
            }
        }
    }

    public MainHook() {}
}
