package com.test.volumeprobe;

import java.util.HashMap;
import java.util.Map;

public class VolumeData {
    // 记录每个 App 的独立音量 (包名 -> 音量)
    public static final Map<String, Integer> appVolumes = new HashMap<>();
    // 记录当前正在发声的媒体 App
    public static String lastPlayingPkg = null;
    
    // 获取某个 App 的音量，如果没有记录，返回默认值
    public static int getVolume(String pkgName, int defaultVol) {
        if (appVolumes.containsKey(pkgName)) {
            return appVolumes.get(pkgName);
        }
        return defaultVol;
    }

    public static void setVolume(String pkgName, int volume) {
        appVolumes.put(pkgName, volume);
    }
}
