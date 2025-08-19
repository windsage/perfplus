package com.transsion.perftool;

import android.content.Context;


public class RefreshingDateThread extends Thread {
    static int cpunum;
    static int[] cpufreq;
    static int[] cpuload;
    static int gpuload;
    static int gpufreq;
    static int mincpubw;
    static int cpubw;
    static int m4m;
    static float maxtemp;
    static float pcbtemp;
    static int memusage;
    static int current;
    static int gpubw;
    static int llcbw;
    static String fps;

    static int delay;
    static boolean reverseCurrentNow;
    private DataLogger dataLogger;
    private boolean isDataLoggingEnabled;
    PlatformUtil platformUtil = PlatformUtil.getInstance();

    public RefreshingDateThread(Context context) {
        dataLogger = DataLogger.getInstance(context, cpunum);
        isDataLoggingEnabled = SharedPreferencesUtil.sharedPreferences.getBoolean(
                SharedPreferencesUtil.DATA_LOGGING_ENABLED, SharedPreferencesUtil.DATA_LOGGING_DEFAULT);
        dataLogger.setLoggingEnabled(isDataLoggingEnabled);
    }

    public void run() {
        delay = SharedPreferencesUtil.sharedPreferences.getInt(SharedPreferencesUtil.REFRESHING_DELAY, SharedPreferencesUtil.DEFAULT_DELAY);
        reverseCurrentNow = SharedPreferencesUtil.sharedPreferences.getBoolean(SharedPreferencesUtil.REVERSE_CURRENT, SharedPreferencesUtil.REVERSE_CURRENT_DEFAULT);
        cpufreq = new int[cpunum];
        cpuload = new int[cpunum];
        while (!FloatingWindow.doExit) {
            for (int i = 0; i < cpunum; i++) {
                if (FloatingWindow.showCpufreqNow)
                    cpufreq[i] = JniTools.getCpuFreq(i);
            }
            if (FloatingWindow.showGpufreqNow && Support.support_gpufreq) {
                if (platformUtil.isQualcomm()) {
                    gpufreq = JniTools.getAdrenoFreq();
                } else if (platformUtil.isMediaTek()) {
                    gpufreq = JniTools.getMtkMaliFreq();
                }
            }
            if (FloatingWindow.showGpuloadNow && Support.support_gpufreq) {
                if (platformUtil.isQualcomm()) {
                    gpuload = JniTools.getAdrenoLoad();
                } else if (platformUtil.isMediaTek()) {
                    gpuload = JniTools.getMtkMaliLoad();
                }
            }
            if (FloatingWindow.showMincpubwNow && Support.support_mincpubw)
                mincpubw = JniTools.getMinCpuBw();
            if (FloatingWindow.showCpubwNow && Support.support_cpubw)
                cpubw = JniTools.getCpuBw();
            if (FloatingWindow.showM4MNow && Support.support_m4m)
                m4m = JniTools.getM4m();
            if (FloatingWindow.showThermalNow && Support.support_temp) {
                maxtemp = JniTools.getCpuMaxTemp();
                if (platformUtil.isQualcomm()) {
                    pcbtemp = JniTools.getQcomPcbTemp();
                } else {
                    pcbtemp = JniTools.getPcbTemp();
                }
            }
            if (FloatingWindow.showMemNow && Support.support_mem)
                memusage = JniTools.getMemUsage();
            if (FloatingWindow.showCurrentNow && Support.support_current)
                current = JniTools.getCurrent();
            if (FloatingWindow.showGpubwNow && Support.support_gpubw)
                gpubw = JniTools.getGpuBw();
            if (FloatingWindow.showLlcbwNow && Support.support_llcbw)
                llcbw = JniTools.getLlccBw();
            if (FloatingWindow.showFpsNow && Support.support_fps) {
                fps = FpsMonitor.getInstance().getFpsgoInfo();
            }
            if (reverseCurrentNow)
                current = -current;

            // 记录数据
            if (dataLogger.isLoggingEnabled()) {
                // 解析FPS数据
                String targetFps = "0";
                String realFps = "0";
                if (fps != null && !fps.isEmpty()) {
                    String[] fpsParts = fps.split("_");
                    if (fpsParts.length >= 2) {
                        targetFps = fpsParts[0].trim();
                        realFps = fpsParts[1].trim();
                    }
                }

                dataLogger.logPerformanceData(cpufreq, cpuload, gpufreq, gpuload,
                        maxtemp, pcbtemp, targetFps, realFps);
            }
            FloatingWindow.uiRefresher.sendEmptyMessage(0);
            for (int i = 0; i < cpunum; i++) {
                if (FloatingWindow.showCpuloadNow && Support.support_cpuload) {
                    final int ii = i;
                    new Thread() {
                        public void run() {
                            cpuload[ii] = JniTools.getCpuLoad(ii);
                        }
                    }.start();
                }
            }
            try {
                Thread.sleep(delay);
            } catch (Exception e) {

            }
        }
    }

    public void setDataLoggingEnabled(boolean enabled) {
        isDataLoggingEnabled = enabled;
        dataLogger.setLoggingEnabled(enabled);
    }
}
