# VoiceNotes — Android 语音速记 MVP

对齐 iOS [hljghost/VoiceNotes](https://github.com/hljghost/VoiceNotes)：录音 → **sherpa-onnx SenseVoice** ASR → **端侧整理**（MediaPipe Gemma / 启发式降级）→ Room。

工程：`/Users/xw/Documents/VoiceNotes-Android`  
包名：`com.lingyou.voicenotes`（Debug：`com.lingyou.voicenotes.debug`）

## 约束

- 中文短速记 · **禁止云端 LLM**（`Enricher` 接口预留后期）
- 模型下载 **只走 ModelScope（modelscope.cn）**

## 环境

- JDK **17**（推荐 `/opt/homebrew/opt/openjdk@17`）
- Android SDK（`ANDROID_HOME`，需 `platforms;android-35` + `build-tools;35.0.0`）

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
```

## 编译 Debug APK

```bash
cd /Users/xw/Documents/VoiceNotes-Android
./gradlew :app:assembleDebug
# 产物：
# app/build/outputs/apk/debug/app-debug.apk
```

## 安装到设备 / 模拟器

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
# 或
./gradlew :app:installDebug
```

首次打开：设置页下载 SenseVoice（必选）与 Gemma（可选；未下则启发式整理）。

侧载 SenseVoice（可选）：

```bash
./scripts/download-sensevoice.sh
# 再用 adb push 到：
# /data/data/com.lingyou.voicenotes.debug/files/VoiceNotes/Models/ASR/sensevoice-small/
```

## 栈

| 层 | 选型 |
|----|------|
| UI | Kotlin + Jetpack Compose |
| 录音 | MediaRecorder · 16 kHz mono M4A |
| ASR | sherpa-onnx SenseVoice Small int8（JitPack） |
| LLM | MediaPipe Tasks GenAI（Gemma `.task`）· 无权重则启发式 |
| 存储 | Room · 音频在 app files `VoiceNotes/Audio` |

## 降级

| 情况 | 行为 |
|------|------|
| 未下 Gemma | 启发式，`llmModelID=…-heuristic` |
| MediaPipe 初始化失败 | 启发式，`…-mediapipe-fallback-heuristic` |
| ASR 未就绪 | `failedASR`，保留音频 |

## 不做（MVP）

云端 ASR / 云端 LLM · 账号同步 · 会议长录音
