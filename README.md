# 🦐 虾米位置追踪

> Kotlin + MVVM + Compose + Room/SQLCipher  
> Target: Android 16 (API 36)  |  Min: Android 8 (API 26)

---

## 架构

```
UI (Compose)  →  ViewModel (Hilt)  →  Repository
                                        ├─ Room (SQLCipher + Keystore)
                                        ├─ LocationProvider (Fused)
                                        └─ ActivityWatcher (Activity Recognition)
                                          
LocationService (Foreground) ← 开机自启 / 手动启停
  ├─ startForeground() + 常驻通知
  ├─ START_STICKY 崩溃恢复
  ├─ WakeLock 防 CPU 休眠
  ├─ 5min 无定位 → 中断标记
  └─ 自适应间隔: 静止 5min / 驾车 10s / 步行 5s
```

## 六关覆盖

| # | 关卡 | 实现 |
|---|------|------|
| ① | 后台保活 | ForegroundService + START_STICKY + BootReceiver + MIUI~EMUI 跳转 |
| ② | 智能节能 | Activity Recognition → 静止3分钟→间隔5分钟 + setSmallestDisplacement(10f) |
| ③ | 权限适配 | Android 9/10/11+ 三套分支 + BACKGROUND 分离申请 + 设置引导 |
| ④ | 轨迹中断 | 5分钟检测 + 中断标记 + TrackSplitter 虚线分段渲染 |
| ⑤ | 数据库 | date+timestamp 索引 + Paging 3 + Douglas-Peucker 抽稀 |
| ⑥ | 数据安全 | SQLCipher 整库加密 + Android Keystore 密钥 + 首次隐私弹窗 |

## 一票否决全避

- ❌ WorkManager → 没用
- ❌ Geofencing → 没用
- ❌ 权限不区分版本 → 三套分支
- ❌ 硬编码密钥 → Keystore
- ❌ R-Tree → 标准 B-Tree
- ❌ lastKnownLocation 未判空 → 处处安全
