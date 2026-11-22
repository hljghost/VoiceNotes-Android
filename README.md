# VoiceNotes — Android 语音速记 MVP

对齐 iOS [hljghost/VoiceNotes](https://github.com/hljghost/VoiceNotes)：录音 → **sherpa-onnx SenseVoice** ASR → **端侧整理**（llama.cpp · **Qwen3.5-2B Q4_K_M** / 启发式降级）→ Room。

工程：`/Users/xw/Documents/VoiceNotes-Android`  
包名：`com.lingyou.voicenotes`（Debug：`com.lingyou.voicenotes.debug`）  
仓：https://github.com/hljghost/VoiceNotes-Android

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
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 安装

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

首次打开：设置页下载 SenseVoice（必选）与 **Qwen3.5-2B**（可选；未下则启发式整理）。

## 栈

| 层 | 选型 |
|----|------|
| UI | Kotlin + Jetpack Compose |
| 录音 | MediaRecorder · 16 kHz mono M4A |
| ASR | sherpa-onnx SenseVoice Small int8（JitPack） |
| LLM | **llama.cpp**（`dev.ffmpegkit-maintained:llama-android:0.1.1`）· **Qwen3.5-2B Q4_K_M GGUF**（~1.28 GB） |
| 存储 | Room · 音频在 app files `VoiceNotes/Audio` |

### LLM 模型

| 项 | 值 |
|----|-----|
| App modelID | `qwen3.5-2b-q4-k-m` |
| 文件 | `Qwen3.5-2B-Q4_K_M.gguf` |
| 体积 | ~1.28 GB（1 280 835 840 B） |
| ModelScope 主仓 | `unsloth/Qwen3.5-2B-GGUF` |
| 备仓 | `diodel/Qwen3.5-2B-Q4_K_M-GGUF` |
| 运行时 | llama.cpp CPU/NEON（arm64-v8a）；x86 模拟器无 native → 启发式 |

## 降级

| 情况 | 行为 |
|------|------|
| 未下 Qwen | 启发式，`llmModelID=…-heuristic` |
| llama.cpp 加载/推理失败 | 启发式，`…-llamacpp-fallback-heuristic` |
| ASR 未就绪 | `failedASR`，保留音频 |

## 不做（MVP）

云端 ASR / 云端 LLM · 账号同步 · 会议长录音
