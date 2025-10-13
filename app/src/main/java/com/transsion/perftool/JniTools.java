package com.transsion.perftool;

public class JniTools {
    static {
        System.loadLibrary("tools");
    }

    public static native int getCpuFreq(int cpu);

    public static native int getAdrenoFreq();

    public static native int getAdrenoLoad();

    public static native int getMtkMaliFreq();

    public static native int getMtkMaliLoad();

    public static native int getMinCpuBw();

    public static native int getCpuBw();

    public static native int getLlccBw();

    public static native int getGpuBw();

    public static native int getM4m();

    public static native int getCpuLoad(int cpu);

    public static native boolean checkCpuLoad();

    public static native int getCpuOnlineStatus(int cpu);

    public static native float getCpuMaxTemp();

    public static native float getPcbTemp();

    public static native float getQcomPcbTemp();

    public static native int getMemUsage();

    public static native int getCurrent();

    public static native int getCpuNum();

    public static native float getQcomDisplayFps();

    public static native float getMtkDisplayFps();
}
