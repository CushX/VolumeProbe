package com.test.volumeprobe;

import android.util.Log;
import androidx.annotation.NonNull;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Method;
import java.util.List;

public class MainHook extends XposedModule {
    private static final String TAG = "VolumeProbe";

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        super.onModuleLoaded(param);
        log(Log.INFO, TAG, "=== 模块已加载到进程: " + param.getProcessName() + " ===");
    }

    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        super.onSystemServerStarting(param);
        log(Log.INFO, TAG, "=== 准备 Hook AudioService (API 102) ===");
        try {
            Class<?> audioServiceClass = param.getClassLoader().loadClass("com.android.server.audio.AudioService");
            Method setStreamVolumeMethod = audioServiceClass.getDeclaredMethod(
                    "setStreamVolume", int.class, int.class, int.class, String.class
            );
            // 注意：hook 返回 HookBuilder，通过 .intercept() 传入 Hooker 实例
            hook(setStreamVolumeMethod).intercept(new SetStreamVolumeHooker());
            log(Log.INFO, TAG, "=== setStreamVolume Hook 安装成功 ===");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Hook 失败: " + t.toString());
        }
    }

    // 指定泛型 <Method>，确保方法签名匹配
    private static class SetStreamVolumeHooker implements XposedInterface.Hooker<Method> {
        @Override
        public Object intercept(@NonNull XposedInterface.Chain<Method> chain) throws Throwable {
            // 使用 List 获取参数，避免类型转换问题
            List<Object> args = chain.getArgs();
            int streamType = (int) args.get(0);
            int index = (int) args.get(1);
            
            if (streamType == 3) { // STREAM_MUSIC
                Log.d(TAG, "【拦截成功】滑动媒体音量，目标值: " + index);
            }
            
            // 放行原始方法调用
            return chain.proceed();
        }
    }
}
