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

        try {
            ClassLoader cl = param.getDefaultClassLoader();
            String targetClassName = "com.android.systemui.volume.dialog.VolumeDialog";
            Class<?> clazz = cl.loadClass(targetClassName);
            
            // 这次我们挂载 onCreate，因为布局是在这里初始化的
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals("onCreate")) {
                    hook(method).intercept(new UIInjectorHooker());
                    log(Log.INFO, TAG, "== 成功挂载 onCreate 方法 ==");
                }
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "挂载异常: " + t);
        }
    }

    private static class UIInjectorHooker implements XposedInterface.Hooker {
        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            // 1. 先让原生面板正常加载完 XML 布局
            Object result = chain.proceed();
            
            try {
                Object dialogInstance = chain.getThisObject(); 
                if (dialogInstance == null) return result;

                // 2. 使用反射调用 requireViewById(0x7f0a09cc) 获取那个核心容器
                // 注意：在代码里，0x7f0a09cc 是一个整数
                Method requireViewByIdMethod = dialogInstance.getClass().getMethod("requireViewById", int.class);
                Object containerView = requireViewByIdMethod.invoke(dialogInstance, 0x7f0a09cc);
                
                if (!(containerView instanceof ViewGroup)) {
                    Log.e(TAG, "获取到的容器不是 ViewGroup");
                    return result;
                }

                ViewGroup rootContainer = (ViewGroup) containerView;
                Context context = rootContainer.getContext();

                // 3. 创建我们要的按钮
                TextView myButton = new TextView(context);
                myButton.setText("分应用面板");
                myButton.setTextColor(Color.WHITE);
                myButton.setBackgroundColor(Color.RED);
                myButton.setTextSize(14);
                
                int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
                myButton.setPadding(pad, pad, pad, pad);

                // 4. 把按钮塞进这个白色卡片里！
                rootContainer.addView(myButton);

                Log.d(TAG, "🔥【注入成功】测试按钮已经塞进音量卡片！");
                
            } catch (Throwable t) {
                Log.e(TAG, "注入 UI 失败: " + t);
            }

            return result;
        }
    }

    public MainHook() {}
}
