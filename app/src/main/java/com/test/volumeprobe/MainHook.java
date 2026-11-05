package com.test.volumeprobe;

import android.util.Log;
import androidx.annotation.NonNull;
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
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        super.onSystemServerStarting(param);
        log(Log.INFO, TAG, "=== 准备 Hook AudioService (API 102) ===");
        try {
            Class<?> audioServiceClass = param.getClassLoader()
                    .loadClass("com.android.server.audio.AudioService");
            Method setStreamVolumeMethod = audioServiceClass.getDeclaredMethod(
                    "setStreamVolume", int.class, int.class, int.class, String.class
            );
            hook(setStreamVolumeMethod).intercept(new SetStreamVolumeHooker());
            log(Log.INFO, TAG, "=== setStreamVolume Hook 安装成功 ===");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Hook 失败: " + t.toString());
        }
    }

    // 注意：Hooker 接口没有泛型
    private static class SetStreamVolumeHooker implements XposedInterface.Hooker {
        @Override
        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
            // getArg(index) 是获取参数更安全的方式
            int streamType = (int) chain.getArg(0);
            int index = (int) chain.getArg(1);

            if (streamType == 3) { // AudioManager.STREAM_MUSIC
                Log.d(TAG, "【拦截成功】滑动媒体音量，目标值: " + index);
            }
            // 必须调用 proceed() 放行
            return chain.proceed();
        }
    }
}
