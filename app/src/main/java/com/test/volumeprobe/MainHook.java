package com.test.volumeprobe;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
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
                    log(Log.INFO, TAG, "== 成功挂载 onCreate，准备注入 ==");
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
                // 根据你提供的 smali，这是根布局 ID
                Object containerView = requireViewByIdMethod.invoke(dialogInstance, 0x7f0a09cc);
                
                if (!(containerView instanceof ViewGroup)) return result;
                ViewGroup rootContainer = (ViewGroup) containerView;
                Context context = rootContainer.getContext();

                // 1. 创建一个好看的测试按钮
                TextView myButton = new TextView(context);
                myButton.setText("⚙️ 分应用控制");
                myButton.setTextColor(Color.WHITE);
                myButton.setBackgroundColor(Color.parseColor("#6200EE")); // 紫色，比红色好看点
                myButton.setTextSize(14);
                myButton.setGravity(android.view.Gravity.CENTER);
                int pad = (int) (8 * context.getResources().getDisplayMetrics().density);
                myButton.setPadding(pad, pad, pad, pad);

                // 2. 🌟 核心逻辑：去找原生的音量条容器
                // 我们在根布局里寻找包含 SeekBar(音量条) 的父容器
                ViewGroup targetContainer = findVolumePanelContainer(rootContainer);
                
                if (targetContainer != null) {
                    Log.d(TAG, "【定位成功】找到原生的排版容器: " + targetContainer.getClass().getName());
                    
                    if (targetContainer instanceof LinearLayout) {
                        // 如果原生的容器是垂直排列的线性布局，我们直接把按钮加在它最下面
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        );
                        params.topMargin = pad; // 给点顶部间距
                        myButton.setLayoutParams(params);
                    } else {
                        // 其他类型的布局，给个默认参数
                        myButton.setLayoutParams(new ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        ));
                    }
                    targetContainer.addView(myButton);
                    Log.d(TAG, "🔥【注入成功】按钮已融入原生容器！");
                } else {
                    Log.e(TAG, "❌ 没找到包含 SeekBar 的容器，只能塞在根节点了");
                    rootContainer.addView(myButton);
                }

            } catch (Throwable t) {
                Log.e(TAG, "注入 UI 失败: " + t);
            }

            return result;
        }
    }

    // 递归寻找包含 SeekBar 的父容器
    private static ViewGroup findVolumePanelContainer(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ViewGroup) {
                ViewGroup childGroup = (ViewGroup) child;
                // 如果这个容器里面直接包含了 SeekBar，那它就是我们要找的卡片！
                for (int j = 0; j < childGroup.getChildCount(); j++) {
                    if (childGroup.getChildAt(j) instanceof SeekBar) {
                        return childGroup;
                    }
                }
                // 如果没找到，继续往深处找
                ViewGroup result = findVolumePanelContainer(childGroup);
                if (result != null) return result;
            }
        }
        return null;
    }

    public MainHook() {}
}
