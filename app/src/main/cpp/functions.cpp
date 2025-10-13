//
// Created by xzr on 2019/6/28.
//
#include "perfmon.h"
#include <cstdio>
#include <cstring>
#include <dirent.h>
#include <cstdlib>
#include <cctype>

#define SYS_THERMAL_PATH "/sys/class/thermal"
#define TYPE_FILE "type"
#define TEMP_FILE "temp"
#define NULLTEMP -9999
#define MAX_PATH_LEN   256
#define TEMP_BUFFER_LEN 32
#define UNSUPPORTED    -1
#define NOT_FOUND      -2
#define SUCCESS         0


int read_file_int(const char *path, int *result) {
    FILE *freq_file = fopen(path, "r");
    if (freq_file == NULL)
        return UNSUPPORTED;
    fscanf(freq_file, "%d", result);
    fclose(freq_file);
    return 0;
}

int read_gpu_file_int(const char *path, int *result) {
    FILE *file = fopen(path, "r");
    if (!file) {
        return UNSUPPORTED;
    }

    char line[256];
    const char *target_prefix = "[GPU   OPP]";
    int found = 0;

    // 逐行读取文件
    while (fgets(line, sizeof(line), file)) {
        if (strstr(line, target_prefix) && strstr(line, "Freq:")) {
            char *freq_start = strstr(line, "Freq:");
            if (!freq_start) break;

            // 跳过 "Freq:" 和可能的空格
            freq_start += strlen("Freq:");
            while (*freq_start == ' ' || *freq_start == ',') freq_start++;

            if (sscanf(freq_start, "%d", result) == 1) {
                found = 1;
                break;
            }
        }
    }

    fclose(file);
    return found ? 0 : UNSUPPORTED;
}

int read_file_str(const char *path, char *result) {
    FILE *freq_file = fopen(path, "r");
    if (freq_file == NULL)
        return UNSUPPORTED;
    fscanf(freq_file, "%s", result);
    fclose(freq_file);
    return 0;
}

int read_process_int(const char *cmd, int *result) {
    FILE *process = popen(cmd, "r");
    if (process == NULL)
        return UNSUPPORTED;
    fscanf(process, "%d", result);
    pclose(process);
    return 0;
}

int read_process_str(const char *cmd, char *result) {
    FILE *process = popen(cmd, "r");
    if (process == NULL)
        return UNSUPPORTED;
    fscanf(process, "%s", result);
    pclose(process);
    return 0;
}

int get_cpu_time(int cpu, int *full_time, int *idle_time) {
    FILE *file;
    char cpu_str[5];
    char cache[50];
    int num = 0, c;

    sprintf(cpu_str, "cpu%d", cpu);
    file = fopen("/proc/stat", "r");
    if (file == NULL)
        return UNSUPPORTED;

    //Get to the target point
    while (1) {
        if (fscanf(file, "%s", cache) == EOF) {
            fclose(file);
            return UNSUPPORTED;
        }
        if (!strcmp(cpu_str, cache)) {
            break;
        }
    }

    //Now the next cache[] should be cputimes
    while (1) {
        fscanf(file, "%s", cache);
        c = atoi(cache);
        if (strcmp(cache, "0") && !c) {     //Start of next line
            break;
        }
        //Count cputime
        if (++num == 4) {
            *idle_time = c;
        }
        *full_time = *full_time + c;
    }
    fclose(file);
    return 0;

}



void to_lowercase(const char *src, char *dst, size_t size) {
    size_t i = 0;
    for (; i < size - 1 && src[i]; i++) {
        dst[i] = tolower((unsigned char)src[i]);
    }
    dst[i] = '\0';
}

int strcasestr_in(const char *haystack, const char *needle) {
    char h_lower[512], n_lower[64];
    to_lowercase(haystack, h_lower, sizeof(h_lower));
    to_lowercase(needle, n_lower, sizeof(n_lower));
    return strstr(h_lower, n_lower) != NULL;
}

int get_sensor_max_temp(int *temp, const char *target_sensor) {
    DIR *dir = NULL;
    struct dirent *entry = NULL;
    char type_path[256];
    char temp_path[256];
    char type_buf[128];
    int max_temp = NULLTEMP;

    dir = opendir(SYS_THERMAL_PATH);
    if (!dir)
        return -1;

    while ((entry = readdir(dir)) != NULL) {
        if (strncmp(entry->d_name, "thermal_zone", 12) != 0)
            continue;

        snprintf(type_path, sizeof(type_path), "%s/%s/%s",
                 SYS_THERMAL_PATH, entry->d_name, TYPE_FILE);

        FILE *type_file = fopen(type_path, "r");
        if (!type_file)
            continue;

        if (!fgets(type_buf, sizeof(type_buf), type_file)) {
            fclose(type_file);
            continue;
        }
        fclose(type_file);

        type_buf[strcspn(type_buf, "\n")] = '\0';
        if (strlen(type_buf) == 0)
            continue;

        if (!strcasestr_in(type_buf, target_sensor))
            continue;

        snprintf(temp_path, sizeof(temp_path), "%s/%s/%s",
                 SYS_THERMAL_PATH, entry->d_name, TEMP_FILE);

        FILE *temp_file = fopen(temp_path, "r");
        if (!temp_file)
            continue;

        int cur_temp;
        if (fscanf(temp_file, "%d", &cur_temp) == 1) {
            if (cur_temp > max_temp)
                max_temp = cur_temp;
        }
        fclose(temp_file);
    }

    closedir(dir);

    if (max_temp == NULLTEMP)
        return -1;
    *temp = max_temp;
    return 0;
}

int get_sensor_temp(int *temp, const char *target_sensor) {
    DIR *thermal_dir;
    struct dirent *entry;
    char type_path[MAX_PATH_LEN], temp_path[MAX_PATH_LEN];
    FILE *fp;
    int found = 0;

    *temp = 0;

    if (!(thermal_dir = opendir(SYS_THERMAL_PATH))) {
        return UNSUPPORTED;
    }

    while ((entry = readdir(thermal_dir)) != NULL) {
        if (strncmp(entry->d_name, "thermal_zone", 12) != 0) continue;
        snprintf(type_path, sizeof(type_path), "%s/%s/%s",
                 SYS_THERMAL_PATH, entry->d_name, TYPE_FILE);

        if (!(fp = fopen(type_path, "r"))) continue;

        char sensor_type[TEMP_BUFFER_LEN];
        if (!fgets(sensor_type, sizeof(sensor_type), fp)) {
            fclose(fp);
            continue;
        }
        fclose(fp);

        sensor_type[strcspn(sensor_type, "\n")] = '\0';
        if (strcmp(sensor_type, target_sensor) != 0) continue;

        snprintf(temp_path, sizeof(temp_path), "%s/%s/%s",
                 SYS_THERMAL_PATH, entry->d_name, TEMP_FILE);
        if (!(fp = fopen(temp_path, "r"))) continue;

        char temp_str[TEMP_BUFFER_LEN];
        if (!fgets(temp_str, sizeof(temp_str), fp)) {
            fclose(fp);
            continue;
        }
        fclose(fp);

        *temp = atoi(temp_str);
        found = 1;
        break;
    }

    closedir(thermal_dir);
    return found ? SUCCESS : NOT_FOUND;
}

int get_mem_info(char name[], int *data) {
    FILE *mem_info;
    char cache[20] = "";

    mem_info = fopen("/proc/mem_info", "r");
    if (mem_info == NULL)
        return UNSUPPORTED;

    //Get target line
    while (fscanf(mem_info, "%s", cache) != EOF) {
        if (!strncmp(cache, name, strlen(name))) {
            //Locked target line
            //Read target data
            if (fscanf(mem_info, "%s", cache) == EOF)
                return UNSUPPORTED;
            *data = atoi(cache);
        }
    }
    fclose(mem_info);

    return 0;
}

/**
 * Read display FPS from Qualcomm DRM node
 * Path: /sys/class/drm/sde-crtc-0/measured_fps
 * Format: "fps: 80.8 duration:500000 frame_count:41"
 */
int get_qcom_display_fps(float *fps) {
    if (!fps) return UNSUPPORTED;

    *fps = 0.0f;

    const char *qcom_path = "/sys/class/drm/sde-crtc-0/measured_fps";

    FILE *fp = fopen(qcom_path, "r");
    if (!fp) {
        return UNSUPPORTED;
    }

    char line[128];
    char last_line[128] = {0};

    // Read all lines and keep the last one (most recent measurement)
    while (fgets(line, sizeof(line), fp)) {
        strncpy(last_line, line, sizeof(last_line) - 1);
    }
    fclose(fp);

    // Parse format: "fps: 80.8 duration:500000 frame_count:41"
    if (strlen(last_line) > 0) {
        float parsed_fps = 0.0f;
        if (sscanf(last_line, "fps: %f", &parsed_fps) == 1) {
            // Sanity check: FPS should be between 1 and 240
            if (parsed_fps >= 1.0f && parsed_fps <= 240.0f) {
                *fps = parsed_fps;
                return 0;
            }
        }
    }

    return UNSUPPORTED;
}

/**
 * Read display FPS from MediaTek fpsgo node
 * Path: /sys/kernel/fpsgo/fstb/fpsgo_status
 * Format: tid bufID name currentFPS targetFPS ...
 */
int get_mtk_display_fps(float *fps) {
    if (!fps) return UNSUPPORTED;

    *fps = 0.0f;

    const char *mtk_path = "/sys/kernel/fpsgo/fstb/fpsgo_status";

    FILE *fp = fopen(mtk_path, "r");
    if (!fp) {
        return UNSUPPORTED;
    }

    char line[256];
    float max_current_fps = 0.0f;
    float dfps_ceiling = 0.0f;

    // Skip header line (tid bufID name currentFPS targetFPS ...)
    if (!fgets(line, sizeof(line), fp)) {
        fclose(fp);
        return UNSUPPORTED;
    }

    while (fgets(line, sizeof(line), fp)) {
        // Parse dfps_ceiling line
        if (strncmp(line, "dfps_ceiling:", 13) == 0) {
            sscanf(line, "dfps_ceiling:%f", &dfps_ceiling);
            break;
        }

        // Parse data line: tid bufID name currentFPS targetFPS ...
        int tid;
        char bufID[32], name[32];
        float current_fps, target_fps;

        int parsed = sscanf(line, "%d %s %s %f %f",
                            &tid, bufID, name, &current_fps, &target_fps);

        if (parsed >= 5) {
            // Filter conditions:
            // 1. Exclude ADPF
            // 2. Exclude currentFPS > targetFPS (abnormal data)
            // 3. Exclude currentFPS <= 0 (no rendering)
            if (strstr(name, "ADPF") != NULL) {
                continue;
            }

            if (current_fps <= 0 || current_fps > target_fps) {
                continue;
            }

            // Find maximum currentFPS
            if (current_fps > max_current_fps) {
                max_current_fps = current_fps;
            }
        }
    }
    fclose(fp);

    // Return max currentFPS if available, otherwise dfps_ceiling
    if (max_current_fps > 0.0f) {
        *fps = max_current_fps;
    } else if (dfps_ceiling > 0.0f) {
        *fps = dfps_ceiling;
    } else {
        return UNSUPPORTED;
    }

    return 0;
}