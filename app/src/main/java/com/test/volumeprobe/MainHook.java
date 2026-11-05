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
        log(Log.INFO, TAG, "=== 模块已加载到进程: " + param.getProcessName() + " ===");
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        if (!param.getPackageName().equals("com.android.systemui")) return;

        log(Log.INFO, TAG, "=== 准备拦截真正的音量面板 ===");

        try {
            ClassLoader cl = param.getDefaultClassLoader();
            String targetClassName = "com.android.systemui.volume.dialog.VolumeDialog";
            Class<?> clazz = cl.loadClass(targetClassName);
            log(Log.INFO, TAG, "✅ 成功找到类: " + targetClassName);

            // 🌟 这次我们只盯准 onCreate，因为界面的根容器是在这里加载的
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals("onCreate")) {
                    hook(method).intercept(new UIInjectorHooker());
                    log(Log.INFO, TAG, "== 成功挂载 onCreate 方法，准备注入 UI ==");
                }
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "挂载异常: " + t);
        }
    }

    private static class UIInjectorHooker implements XposedInterface.Hooker {
        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            // 1. 先让原生的 onCreate 跑完，保证原来的音量面板已经画好
            Object result = chain.proceed();
            
            try {
                Object dialogInstance = chain.getThisObject(); 
                if (dialogInstance == null) return result;

                // 2. 用反射调用 requireViewById(0x7f0a09cc) 拿到那个装音量条的白色卡片
                Method requireViewByIdMethod = dialogInstance.getClass().getMethod("requireViewById", int.class);
                Object containerView = requireViewByIdMethod.invoke(dialogInstance, 0x7f0a09cc);
                
                if (!(containerView instanceof ViewGroup)) {
                    Log.e(TAG, "获取到的容器不是 ViewGroup，无法注入");
                    return result;
                }

                ViewGroup rootContainer = (ViewGroup) containerView;
                Context context = rootContainer.getContext();

                // 3. 创建一个超显眼的红色按钮用来测试
                TextView myButton = new TextView(context);
                myButton.setText("⚙️ 分应用面板");
                myButton.setTextColor(Color.WHITE);
                myButton.setBackgroundColor(Color.RED);
                myButton.setTextSize(16);
                myButton.setGravity(android.view.Gravity.CENTER);
                
                // 设置一点点内边距
                int pad = (int) (12 * context.getResources().getDisplayMetrics().density);
                myButton.setPadding(pad, pad, pad, pad);

                // 4. 暴力塞进卡片里！
                rootContainer.addView(myButton);

                Log.d(TAG, "🔥【注入成功】测试按钮已经塞进音量卡片！");
                
            } catch (Throwable t) {
                // 就算注入失败，也绝对不能抛出异常，否则 SystemUI 会崩溃
                Log.e(TAG, "注入 UI 失败: " + t);
            }

            return result;
        }
    }

    public MainHook() {}
}
