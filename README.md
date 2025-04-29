# <img src="https://raw.githubusercontent.com/xzr467706992/PerfMon-Plus/master/app/src/main/res/drawable/icon.png" width="70" height="70" /> PerfMon+ 
A simple performance monitor for Android.
### Features:
* cpufreq monitoring
* cpuload monitoring
* gpufreq monitoring
* gpuload monitoring
* cpubw freq monitoring
* mincpubw monitoring
* m4m cache freq monitoring
* system cache (llc) freq monitoring
* memory usage monitoring
* current monitoring

### Screenshots
<img src="https://raw.githubusercontent.com/xzr467706992/PerfMon-Plus/master/screenshots/1.jpg" width="360" height="640" /> <img src="https://raw.githubusercontent.com/xzr467706992/PerfMon-Plus/master/screenshots/2.jpg" width="360" height="640" />
<img src="https://raw.githubusercontent.com/xzr467706992/PerfMon-Plus/master/screenshots/3.jpg" width="360" height="640" /> <img src="https://raw.githubusercontent.com/xzr467706992/PerfMon-Plus/master/screenshots/4.jpg" width="360" height="640" />

### Any star, fork, PR is welcome


## 修改过程
### 20250424
1. CPU节点无法读取，因为online节点没有修改为664，暂时取消对online的读取判断。
2. 增加对GPU读取

### 20250425
1. 增加平台的判断，支持qcom mediatek unisoc
2. 对gpu 支持qcom和mediatek
3. gpu load读取出错Fix
4. 增加了对pcb板温的读取

### 20250427
1. 修改了application id
2. 把max temp修改为cpu max temp，去读thermal_zone*/type中包含cpu的节点的temp，找出最大值
3. 固定apk的名字
4. 修改README.md

### 20250429
1. 从`/sys/kernel/fpsgo/fstb/fpsgo_status`读取fps信息的方法不可行
   1. 需要根据tid去判断top-app，从/proc/$tid/cgroup读取需要root权限，普通应用无法获得
2. 修改使用Java方法，在Choreographer.FrameCallback回调中计算帧率，同时配置间隔时间采用sp数据

## 预计修改

## 权限记录
权限修改已经合入主干分支，支持vendor s/u/v