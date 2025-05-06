package xzr.perfmon;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 性能数据记录器，用于将性能数据保存到CSV文件
 * 特性：
 * 1. 内存缓冲区减少I/O操作
 * 2. 批量写入
 * 3. 自动刷新
 * 4. 按天分割文件
 * 5. 异常处理和恢复机制
 * 6. 旧文件清理
 */
public class DataLogger {
    private static final String TAG = "DataLogger";
    private static final String CSV_HEADER = "Timestamp,";

    // 配置参数
    private static final int BUFFER_FLUSH_THRESHOLD = 180; // 缓存180条数据再写入文件（约3分钟）
    private static final long AUTO_FLUSH_INTERVAL = 6000; // 1分钟自动刷新一次
    private static final int MIN_RECORDS_TO_FLUSH = 30; // 最少30条记录才刷新
    private static final int MAX_RETRY_COUNT = 3; // 写入失败最大重试次数

    private static DataLogger instance;
    private final ConcurrentLinkedQueue<String> dataBuffer;
    private final Object bufferLock = new Object();
    private String currentDate;
    private String filePath;
    private Context context;
    private boolean isLoggingEnabled = false;
    private int cpuCores;
    private boolean isForceFlush = false;
    private long lastFlushTime;

    /**
     * 构造函数
     * @param context 应用上下文
     * @param cpuCores CPU核心数
     */
    private DataLogger(Context context, int cpuCores) {
        this.context = context.getApplicationContext();
        this.cpuCores = cpuCores;
        this.dataBuffer = new ConcurrentLinkedQueue<>();
        this.currentDate = getCurrentDate();
        this.lastFlushTime = System.currentTimeMillis();

        // 初始化文件
        initializeFile(buildHeader());

        // 启动自动刷新线程
        startAutoFlushThread();

        // 清理过期日志（保留30天）
        cleanupOldFiles(30);
    }

    /**
     * 获取单例实例
     * @param context 应用上下文
     * @param cpuCores CPU核心数
     * @return DataLogger实例
     */
    public static synchronized DataLogger getInstance(Context context, int cpuCores) {
        if (instance == null) {
            instance = new DataLogger(context, cpuCores);
        }
        return instance;
    }

    /**
     * 设置日志记录开关
     * @param enabled 是否启用日志记录
     */
    public void setLoggingEnabled(boolean enabled) {
        isLoggingEnabled = enabled;
        if (enabled) {
            Log.d(TAG, "Data logging enabled");
        } else {
            Log.d(TAG, "Data logging disabled");
            isForceFlush = true;
            flushBufferToFile(); // 停用时刷新缓存
            isForceFlush = false;
        }
    }

    /**
     * 获取日志记录状态
     * @return 是否启用日志记录
     */
    public boolean isLoggingEnabled() {
        return isLoggingEnabled;
    }

    /**
     * 获取当前日期，格式：YYYYMMDD
     * @return 当前日期字符串
     */
    private String getCurrentDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        return sdf.format(new Date());
    }

    /**
     * 获取当前时间戳，格式：YYYY-MM-DD HH:MM:SS.SSS
     * @return 当前时间戳字符串
     */
    private String getTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
        return sdf.format(new Date());
    }

    /**
     * 构建CSV头部
     * @return CSV头部字符串
     */
    private String buildHeader() {
        StringBuilder headerBuilder = new StringBuilder(CSV_HEADER);
        for (int i = 0; i < cpuCores; i++) {
            headerBuilder.append("CPU").append(i).append("_freq,");
            headerBuilder.append("CPU").append(i).append("_load,");
        }
        headerBuilder.append("GPU_freq,GPU_load,CPU_temp,Board_temp,Target_FPS,Real_FPS");
        return headerBuilder.toString();
    }

    /**
     * 初始化日志文件
     * @param header CSV头部
     */
    private void initializeFile(String header) {
        try {
            filePath = getFilePath();
            File file = new File(filePath);

            // 检查文件目录是否存在，不存在则创建
            File parentDir = file.getParentFile();
            if (!parentDir.exists() && !parentDir.mkdirs()) {
                Log.e(TAG, "Failed to create directories: " + parentDir.getAbsolutePath());
                return;
            }

            // 检查存储空间
            if (parentDir.getUsableSpace() < 1024 * 1024) { // 1MB as minimum
                Log.e(TAG, "Not enough storage space available");
                return;
            }

            // 如果文件不存在，创建并写入头
            if (!file.exists()) {
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                    writer.write(header);
                    writer.newLine();
                }
                Log.d(TAG, "Created new log file: " + file.getAbsolutePath());
            } else {
                // 文件存在，检查是否可写
                if (!file.canWrite()) {
                    Log.e(TAG, "File exists but is not writable: " + file.getAbsolutePath());
                    return;
                }

                // 检查文件内容是否有效
                boolean isValidFile = checkFileValidity(file, header);
                if (!isValidFile) {
                    Log.w(TAG, "Existing file appears invalid, backing up and creating new one");
                    backupInvalidFile(file);
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                        writer.write(header);
                        writer.newLine();
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error initializing log file", e);
        }
    }

    /**
     * 获取日志文件路径
     * @return 文件路径字符串
     */
    private String getFilePath() {
        File storageDir = new File(context.getExternalFilesDir(null), "PerfMonLogs");
        return new File(storageDir, "cpu_" + currentDate + ".csv").getAbsolutePath();
    }

    /**
     * 检查文件是否有效
     * @param file 文件对象
     * @param expectedHeader 期望的CSV头部
     * @return 文件是否有效
     */
    private boolean checkFileValidity(File file, String expectedHeader) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            // 如果文件为空，则视为无效
            if (file.length() == 0) {
                return false;
            }

            // 文件有内容，检查第一行是否匹配预期的头部
            String firstLine = reader.readLine();
            return firstLine != null && firstLine.trim().startsWith(expectedHeader.trim().split(",")[0]);
        } catch (IOException e) {
            Log.e(TAG, "Error checking file validity", e);
            return false;
        }
    }

    /**
     * 备份无效文件
     * @param file 无效的文件对象
     */
    private void backupInvalidFile(File file) {
        try {
            String backupName = file.getAbsolutePath() + ".bak." + System.currentTimeMillis();
            File backupFile = new File(backupName);
            if (file.renameTo(backupFile)) {
                Log.d(TAG, "Invalid file backed up to: " + backupName);
            } else {
                Log.e(TAG, "Failed to backup invalid file");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error backing up invalid file", e);
        }
    }

    /**
     * 记录性能数据
     * @param cpuFreq CPU频率数组
     * @param cpuLoad CPU负载数组
     * @param gpuFreq GPU频率
     * @param gpuLoad GPU负载
     * @param cpuTemp CPU温度
     * @param boardTemp 主板温度
     * @param targetFps 目标帧率
     * @param realFps 实际帧率
     */
    public void logPerformanceData(int[] cpuFreq, int[] cpuLoad, int gpuFreq, int gpuLoad,
                                   float cpuTemp, float boardTemp, String targetFps, String realFps) {
        if (!isLoggingEnabled) {
            return;
        }

        // 检查日期是否变化，如果变化则创建新文件
        String newDate = getCurrentDate();
        if (!newDate.equals(currentDate)) {
            synchronized (bufferLock) {
                flushBufferToFile(); // 先将旧数据刷入旧文件
                currentDate = newDate;
                initializeFile(buildHeader()); // 初始化新文件
            }
        }

        StringBuilder dataBuilder = new StringBuilder();
        dataBuilder.append(getTimestamp()).append(",");

        // 添加CPU数据
        for (int i = 0; i < cpuCores; i++) {
            if (i < cpuFreq.length) {
                dataBuilder.append(cpuFreq[i]);
            } else {
                dataBuilder.append("0");
            }
            dataBuilder.append(",");

            if (i < cpuLoad.length) {
                dataBuilder.append(cpuLoad[i]);
            } else {
                dataBuilder.append("0");
            }
            dataBuilder.append(",");
        }

        // 添加其他数据
        dataBuilder.append(gpuFreq).append(",");
        dataBuilder.append(gpuLoad).append(",");
        dataBuilder.append(cpuTemp).append(",");
        dataBuilder.append(boardTemp).append(",");
        dataBuilder.append(targetFps).append(",");
        dataBuilder.append(realFps);

        // 添加到缓冲区
        dataBuffer.add(dataBuilder.toString());

        // 检查是否需要刷新
        long currentTime = System.currentTimeMillis();
        boolean timeCondition = (currentTime - lastFlushTime) > AUTO_FLUSH_INTERVAL;
        boolean sizeCondition = dataBuffer.size() >= BUFFER_FLUSH_THRESHOLD;

        // 如果满足任一条件则刷新
        if (sizeCondition || timeCondition) {
            flushBufferToFile();
        }
    }

    /**
     * 将缓冲区数据刷新到文件
     */
    public void flushBufferToFile() {
        synchronized (bufferLock) {
            if (dataBuffer.isEmpty()) {
                return;
            }

            // 批量获取当前所有数据
            List<String> dataToWrite = new ArrayList<>();
            while (!dataBuffer.isEmpty()) {
                dataToWrite.add(dataBuffer.poll());
            }

            // 如果数据量太小且不是强制刷新，可以考虑不写入
            if (dataToWrite.size() < MIN_RECORDS_TO_FLUSH && !isForceFlush) {
                // 将数据放回缓冲区
                dataBuffer.addAll(dataToWrite);
                return;
            }

            try {
                File file = new File(filePath);

                // 检查文件是否存在，如果不存在则重新初始化
                if (!file.exists()) {
                    Log.w(TAG, "Log file missing, reinitializing");
                    initializeFile(buildHeader());
                }

                // 检查文件是否可写
                if (!file.canWrite()) {
                    Log.e(TAG, "File is not writable: " + file.getAbsolutePath());
                    // 将数据放回缓冲区
                    dataBuffer.addAll(dataToWrite);
                    return;
                }

                // 检查存储空间
                if (file.getParentFile().getUsableSpace() < dataToWrite.size() * 100) { // 假设每行大约100字节
                    Log.e(TAG, "Not enough storage space available");
                    // 将数据放回缓冲区
                    dataBuffer.addAll(dataToWrite);
                    return;
                }

                // 尝试写入数据，使用重试机制
                boolean writeSuccess = false;
                int retryCount = 0;
                while (!writeSuccess && retryCount < MAX_RETRY_COUNT) {
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
                        for (String data : dataToWrite) {
                            writer.write(data);
                            writer.newLine();
                        }
                        writeSuccess = true;
                    } catch (IOException e) {
                        retryCount++;
                        if (retryCount >= MAX_RETRY_COUNT) {
                            throw e; // 重试3次后仍失败，抛出异常
                        }
                        // 等待一会儿再重试，可能文件被其他进程暂时锁定
                        try {
                            Thread.sleep(100 * retryCount);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }

                if (writeSuccess) {
                    Log.d(TAG, "Flushed " + dataToWrite.size() + " records to file");
                    lastFlushTime = System.currentTimeMillis();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error writing to log file", e);
                // 写入失败时，将数据放回缓冲区
                dataBuffer.addAll(dataToWrite);

                // 如果是存储卡被移除等严重错误，尝试更新文件路径
                if (isExternalStorageError(e)) {
                    try {
                        // 尝试更新存储路径到内部存储
                        updateStoragePathToInternal();
                    } catch (Exception ex) {
                        Log.e(TAG, "Failed to switch to internal storage", ex);
                    }
                }
            }
        }
    }

    /**
     * 检查是否是外部存储错误
     * @param e IOException异常
     * @return 是否是外部存储错误
     */
    private boolean isExternalStorageError(IOException e) {
        String message = e.getMessage();
        if (message == null) return false;
        return message.contains("External storage") ||
                message.contains("Permission denied") ||
                message.contains("No such file or directory");
    }

    /**
     * 将存储路径切换到内部存储
     */
    private void updateStoragePathToInternal() {
        File internalDir = new File(context.getFilesDir(), "PerfMonLogs");
        if (!internalDir.exists()) {
            internalDir.mkdirs();
        }
        filePath = new File(internalDir, "cpu_" + currentDate + ".csv").getAbsolutePath();
        Log.d(TAG, "Switched to internal storage: " + filePath);
        initializeFile(buildHeader());
    }

    /**
     * 启动自动刷新线程
     */
    private void startAutoFlushThread() {
        Thread autoFlushThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(AUTO_FLUSH_INTERVAL);
                    if (isLoggingEnabled && !dataBuffer.isEmpty()) {
                        // 设置强制刷新标志
                        isForceFlush = true;
                        flushBufferToFile();
                        isForceFlush = false;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        autoFlushThread.setDaemon(true);
        autoFlushThread.start();
    }

    /**
     * 清理旧文件
     * @param maxDaysToKeep 保留的最大天数
     */
    public void cleanupOldFiles(int maxDaysToKeep) {
        try {
            File storageDir = new File(context.getExternalFilesDir(null), "PerfMonLogs");
            if (!storageDir.exists()) return;

            File[] files = storageDir.listFiles((dir, name) -> name.startsWith("cpu_") && name.endsWith(".csv"));
            if (files == null) return;

            long cutoffTime = System.currentTimeMillis() - (maxDaysToKeep * 24 * 60 * 60 * 1000L);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());

            for (File file : files) {
                try {
                    // 从文件名解析日期
                    String dateStr = file.getName().substring(4, 12); // cpu_YYYYMMDD.csv
                    Date fileDate = sdf.parse(dateStr);

                    if (fileDate != null && fileDate.getTime() < cutoffTime) {
                        if (file.delete()) {
                            Log.d(TAG, "Deleted old log file: " + file.getName());
                        } else {
                            Log.w(TAG, "Failed to delete old log file: " + file.getName());
                        }
                    }
                } catch (ParseException e) {
                    Log.e(TAG, "Error parsing date from filename: " + file.getName(), e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up old files", e);
        }
    }

    /**
     * 应用停止时调用，确保数据刷新到文件
     */
    public void onAppStop() {
        if (isLoggingEnabled) {
            isForceFlush = true;
            flushBufferToFile();
            isForceFlush = false;
        }
    }

    /**
     * 获取所有日志文件列表
     * @return 日志文件列表
     */
    public List<File> getLogFiles() {
        List<File> logFiles = new ArrayList<>();
        try {
            File storageDir = new File(context.getExternalFilesDir(null), "PerfMonLogs");
            if (!storageDir.exists()) return logFiles;

            File[] files = storageDir.listFiles((dir, name) -> name.startsWith("cpu_") && name.endsWith(".csv"));
            if (files != null) {
                for (File file : files) {
                    logFiles.add(file);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting log files", e);
        }
        return logFiles;
    }
}