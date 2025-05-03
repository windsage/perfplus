package xzr.perfmon;

import android.content.Context;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DataLogger {
    private static final String TAG = "DataLogger";
    private static final String CSV_HEADER = "Timestamp,";
    private static final int BUFFER_FLUSH_THRESHOLD = 180; // 缓存180条数据再写入文件
    private static final long AUTO_FLUSH_INTERVAL = 60000; // 60秒自动刷新一次

    private static DataLogger instance;
    private final ConcurrentLinkedQueue<String> dataBuffer;
    private final Object bufferLock = new Object();
    private String currentDate;
    private String filePath;
    private Context context;
    private boolean isLoggingEnabled = false;
    private int cpuCores;

    // 新增一个强制刷新的标志
    private boolean isForceFlush = false;

    private DataLogger(Context context, int cpuCores) {
        this.context = context.getApplicationContext();
        this.cpuCores = cpuCores;
        this.dataBuffer = new ConcurrentLinkedQueue<>();
        this.currentDate = getCurrentDate();

        // 创建CSV头
        StringBuilder headerBuilder = new StringBuilder(CSV_HEADER);
        for (int i = 0; i < cpuCores; i++) {
            headerBuilder.append("CPU").append(i).append("_freq,");
            headerBuilder.append("CPU").append(i).append("_load,");
        }
        headerBuilder.append("GPU_freq,GPU_load,CPU_temp,Board_temp,Target_FPS,Real_FPS");
        initializeFile(headerBuilder.toString());

        // 启动自动刷新线程
        startAutoFlushThread();
    }

    public static synchronized DataLogger getInstance(Context context, int cpuCores) {
        if (instance == null) {
            instance = new DataLogger(context, cpuCores);
        }
        return instance;
    }

    public void setLoggingEnabled(boolean enabled) {
        isLoggingEnabled = enabled;
        if (enabled) {
            Log.d(TAG, "Data logging enabled");
        } else {
            Log.d(TAG, "Data logging disabled");
            flushBufferToFile(); // 停用时刷新缓存
        }
    }

    public boolean isLoggingEnabled() {
        return isLoggingEnabled;
    }

    private String getCurrentDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        return sdf.format(new Date());
    }

    private String getTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
        return sdf.format(new Date());
    }

    private void initializeFile(String header) {
        try {
            filePath = getFilePath();
            File file = new File(filePath);

            // 如果文件不存在，创建文件并写入头
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                    writer.write(header);
                    writer.newLine();
                }
                Log.d(TAG, "Created new log file: " + file.getAbsolutePath());
            }
        } catch (IOException e) {
            Log.e(TAG, "Error initializing log file", e);
        }
    }

    private String getFilePath() {
        File storageDir = new File(context.getExternalFilesDir(null), "PerfMonLogs");
        return new File(storageDir, "cpu_" + currentDate + ".csv").getAbsolutePath();
    }

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
                initializeFile(CSV_HEADER); // 初始化新文件
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

        // 如果缓冲区达到阈值，刷新到文件
        if (dataBuffer.size() >= BUFFER_FLUSH_THRESHOLD) {
            flushBufferToFile();
        }
    }

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

            // 如果数据量太小且不是应用退出时的刷新，可以考虑不写入
            if (dataToWrite.size() < 10 && !isForceFlush) {
                // 将数据放回缓冲区
                dataBuffer.addAll(dataToWrite);
                return;
            }

            try {
                File file = new File(filePath);
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
                    for (String data : dataToWrite) {
                        writer.write(data);
                        writer.newLine();
                    }
                }
                Log.d(TAG, "Flushed " + dataToWrite.size() + " records to file");
            } catch (IOException e) {
                Log.e(TAG, "Error writing to log file", e);
                // 写入失败时，将数据放回缓冲区
                dataBuffer.addAll(dataToWrite);
            }
        }
    }

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

    public void onAppStop() {
        if (isLoggingEnabled) {
            isForceFlush = true;
            flushBufferToFile();
            isForceFlush = false;
        }
    }
}