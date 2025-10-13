//
// Created by xzr on 2019/6/28.
//

#ifndef PERFMON_PERFMON_H
#define PERFMON_PERFMON_H

#include <jni.h>

#define DEFAULT_PATH_SIZE 60
#define UNSUPPORTED (-1)
#define NULLTEMP (-233)

int read_file_int(const char *from, int *to);

int read_gpu_file_int(const char *path, int *result);

int read_file_str(const char *from, char *to);

int get_cpu_time(int cpu, int *full_time, int *idle_time);

int get_sensor_max_temp(int *temp, const char *target_sensor);

int get_sensor_temp(int *temp, const char *target_sensor);

int get_mem_info(char name[], int *data);

int read_process_int(const char *cmd, int *result);

int read_process_str(const char *cmd, char *result);

/**
 * Get real FPS from Qualcomm DRM node
 * @param fps Output parameter for FPS value
 * @return 0 on success, UNSUPPORTED on failure
 */
int get_qcom_display_fps(float *fps);

/**
 * Get real FPS from MediaTek fpsgo node
 * @param fps Output parameter for FPS value
 * @return 0 on success, UNSUPPORTED on failure
 */
int get_mtk_display_fps(float *fps);
#endif //PERFMON_PERFMON_H
