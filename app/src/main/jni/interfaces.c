//
// Created by xzr on 2019/6/23.
//

#include "xzr_perfmon_JniTools.h"
#include "perfmon.h"
#include <stdio.h>
#include <unistd.h>
#include <math.h>

JNIEXPORT jint JNICALL Java_xzr_perfmon_JniTools_getCpuFreq
        (JNIEnv *env, jclass jclass1, jint cpu) {
    char path[DEFAULT_PATH_SIZE];
    int freq;
    sprintf(path, "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_cur_freq", cpu);
    if (read_file_int(path, &freq))
        return UNSUPPORTED;

    return freq / 1000;
}

JNIEXPORT jint JNICALL Java_xzr_perfmon_JniTools_getAdrenoFreq
        (JNIEnv *env, jclass jclass1) {
    int freq;
    if (read_file_int("/sys/kernel/gpu/gpu_clock", &freq))
        return UNSUPPORTED;

    return freq;
}

JNIEXPORT jint JNICALL Java_xzr_perfmon_JniTools_getAdrenoLoad
        (JNIEnv *env, jclass jclass1) {
    int freq;
    if (read_file_int("/sys/kernel/gpu/gpu_busy", &freq))
        return UNSUPPORTED;

    return freq;
}

/**
 * 获取MTK平台GPU频率
 * @param env
 * @param clazz
 * @return
 */
JNIEXPORT jint JNICALL Java_xzr_perfmon_JniTools_getMtkMaliFreq
        (JNIEnv *env, jclass clazz) {
    int freq;
    if (read_gpu_file_int("/proc/gpufreqv2/gpufreq_status", &freq))
        return UNSUPPORTED;

    return freq / 1000;
}

/**
 * 获取MTK平台GPU load
 * @param env
 * @param jclass1
 * @return
 */
JNIEXPORT jint JNICALL Java_xzr_perfmon_JniTools_getMtkMaliLoad
        (JNIEnv *env, jclass jclass1) {
    int freq;
    if (read_file_int("/sys/module/ged/parameters/gpu_loading", &freq))
        return UNSUPPORTED;

    return freq;
}

JNIEXPORT jint JNICALL Java_xzr_perfmon_JniTools_getMinCpuBw
        (JNIEnv *env, jclass jclass1) {
    int freq;

    if (read_file_int("/sys/class/devfreq/soc:qcom,mincpubw/cur_freq", &freq))
        return UNSUPPORTED;

    return freq;
}

JNIEXPORT jint JNICALL Java_xzr_perfmon_JniTools_getCpuBw
        (JNIEnv *env, jclass jclass1) {
    int freq;

    if (!read_file_int("/sys/class/devfreq/soc:qcom,cpubw/cur_freq", &freq))
        return freq;

    if (!read_file_int("/sys/class/devfreq/soc:qcom,cpu-cpu-llcc-bw/cur_freq", &freq))
        return freq;

    return UNSUPPORTED;
}

JNIEXPORT jint JNICALL Java_xzr_perfmon_JniTools_getM4m
        (JNIEnv *env, jclass jclass1) {
    int freq;

    if (read_file_int("/sys/class/devfreq/soc:qcom,m4m/cur_freq", &freq))
        return UNSUPPORTED;

    return freq / 1000;
}

JNIEXPORT jint JNICALL Java_xzr_perfmon_JniTools_getCpuLoad
        (JNIEnv *env, jclass jclass1, jint cpu) {
    int time1 = 0, time2 = 0, idle1 = 0, idle2 = 0;
    if (get_cpu_time(cpu, &time1, &idle1))
        return UNSUPPORTED;
    usleep(pow(1 * 10, 6));
    if (get_cpu_time(cpu, &time2, &idle2))
        return UNSUPPORTED;
    return (int) ((1 - (((float) idle2 - idle1) / (time2 - time1))) * 100);
}

JNIEXPORT jint JNICALL Java_xzr_perfmon_JniTools_getCpuOnlineStatus
        (JNIEnv *env, jclass jclass1, jint cpu) {
    int status;
    char path[DEFAULT_PATH_SIZE];

    sprintf(path, "/sys/devices/system/cpu/cpu%d/online", cpu);
    if (read_file_int(path, &status))
        return UNSUPPORTED;

    return status;
}

JNIEXPORT jint JNICALL Java_xzr_perfmon_JniTools_getMaxTemp
        (JNIEnv *env, jclass jclass1) {
    int temp;
    if (get_sensor_max_temp(&temp, "cpu") || temp == NULLTEMP)
        return UNSUPPORTED;

    return temp;
}

JNIEXPORT jfloat JNICALL Java_xzr_perfmon_JniTools_getPcbTemp
        (JNIEnv *env, jclass jclass1) {
    int temp;
    if (get_sensor_temp(&temp, "ap_ntc") || temp == NULLTEMP)
        return UNSUPPORTED;

    return temp / 1000.0;
}

JNIEXPORT jboolean JNICALL Java_xzr_perfmon_JniTools_checkCpuLoad
        (JNIEnv *env, jclass jclass1) {
    FILE *file = fopen("/proc/stat", "r");
    if (file == NULL)
        return 0;
    return 1;
}

JNIEXPORT jint JNICALL Java_xzr_perfmon_JniTools_getMemUsage
        (JNIEnv *env, jclass jclass1) {
    int total = -1;
    int available = -1;

    if (get_mem_info("MemTotal", &total))
        return UNSUPPORTED;

    if (get_mem_info("MemAvailable", &available))
        return UNSUPPORTED;

    return (int) ((1 - (float) available / total) * 100);
}

JNIEXPORT jint JNICALL Java_xzr_perfmon_JniTools_getCurrent
        (JNIEnv *env, jclass jclass1) {
    int current;

    if (read_file_int("/sys/class/power_supply/battery/current_now", &current))
        return UNSUPPORTED;

    return -current / 1000;
}

JNIEXPORT jint JNICALL Java_xzr_perfmon_JniTools_getCpuNum
        (JNIEnv *env, jclass jclass1) {
    int cpunum;
    if (read_process_int("ls /sys/devices/system/cpu | grep -o \"cpu[0-9]*$\" | wc -l", &cpunum))
        return 0;
    return cpunum;
}

JNIEXPORT jint JNICALL Java_xzr_perfmon_JniTools_getGpuBw
        (JNIEnv *env, jclass jclass1) {
    int freq;

    if (read_file_int("/sys/class/devfreq/soc:qcom,gpubw/cur_freq", &freq))
        return UNSUPPORTED;

    return freq;
}

JNIEXPORT jint JNICALL Java_xzr_perfmon_JniTools_getLlccBw
        (JNIEnv *env, jclass jclass1) {
    int freq;

    if (!read_file_int("/sys/class/devfreq/soc:qcom,cpu-llcc-ddr-bw/cur_freq", &freq))
        return freq;

    if (!read_file_int("/sys/class/devfreq/soc:qcom,llccbw/cur_freq", &freq))
        return freq;

    return UNSUPPORTED;
}

JNIEXPORT jstring JNICALL Java_xzr_perfmon_JniTools_getFps
        (JNIEnv *env, jclass jclass1) {
    char fps[5] = "";

    if (!read_file_str("/sys/devices/virtual/graphics/fb0/measured_fps", &fps))
        return (*env)->NewStringUTF(env, &fps);

    if (!read_process_str("cat /sys/class/drm/sde-crtc-0/measured_fps | awk '{print $2}'", &fps))
        return (*env)->NewStringUTF(env, &fps);

    return (*env)->NewStringUTF(env, "");
}
