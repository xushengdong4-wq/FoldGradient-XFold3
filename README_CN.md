# Fold Gradient · vivo X Fold3

这是一个**不更换当前壁纸**的折叠屏开屏渐变原型。

## 目标效果

- 外屏 → 展开内屏时，在当前桌面/壁纸/应用画面上方叠加一层半透明蓝紫 + 粉色渐变。
- 如果系统开放 `TYPE_HINGE_ANGLE`，渐变会跟随真实开合角度连续变化。
- 越接近 180°，渐变越淡；完全展开后叠层完全消失。
- 合上屏幕时不播放。
- 渐变层不接收触摸，不影响桌面操作。
- 不截图、不读取壁纸、不使用无障碍服务、没有网络权限。

## vivo 兼容策略

1. **优先：铰链角度传感器**
   - Android 公开的 `Sensor.TYPE_HINGE_ANGLE`。
   - App 首页会直接显示“已检测到 / 未检测到”和实时角度。

2. **兜底：内外屏切换检测**
   - 如果 vivo 没有向第三方 App 暴露连续铰链角度，检测从手机比例的外屏切到接近方形的内屏。
   - 触发一次约 680ms 的渐变淡出动画。

## 安装后的设置

1. 打开 `Fold Gradient`。
2. 点 **允许显示在其他应用上**，开启悬浮层权限。
3. 点 **启动开屏渐变**。
4. 点 **预览一次渐变效果**，确认视觉效果。
5. 回到首页看“铰链角度传感器”一栏，然后慢慢开合手机：
   - 如果角度会连续变化：使用精确角度模式。
   - 如果显示未检测到：自动使用内屏切换模式。

### OriginOS / vivo 后台注意事项

如果系统一段时间后把服务杀掉，请在 vivo 的电池/后台管理中允许本 App：
- 后台运行
- 自启动（若系统提供）
- 不受高耗电后台限制

具体菜单名称会因 OriginOS 版本和系统语言不同而变化。

## 构建

这是标准 Android Studio Java 工程：

- `minSdk 30`
- `targetSdk 35`
- `compileSdk 35`
- App ID: `com.lucas.foldgradient`

用 Android Studio 打开项目目录，等待 Gradle 同步后执行：

`Build > Build APK(s)`

Debug APK 通常位于：

`app/build/outputs/apk/debug/app-debug.apk`

## 当前版本

v0.1.0

视觉参数当前冻结为：
- 蓝 / 紫 / 粉双色中心渐变
- 内屏打开后约 680ms 兜底淡出
- 180° 附近完全透明

后续可以在真机上根据 X Fold3 的实际角度范围调整触发点、速度和渐变强度。

## 无电脑云端编译
工程已包含 `.github/workflows/build-apk.yml`。
推送到 GitHub 后，GitHub Actions 会自动使用 Java 17 + Android SDK + Gradle 8.9 构建：

`app/build/outputs/apk/debug/app-debug.apk`

该 debug APK 会由 Android 构建工具自动签名，可直接侧载测试。
