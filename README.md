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

## 预计修改
1. 温度目前是CPU温度，需要增加板温。
2. GPU可以读取，但是需要增加SELinux权限。
3. FPS相关的节点没有获取到。
4. 如果后续有节点需要从660->664，可以考虑对CPU online也打开，然后打开原来的online判断
5. GPU负载读取有问题