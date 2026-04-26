---
sidebar_position: 31
---

# Audio 接口

音频能力统一在 `IAudioService`，当前由 OpenAI 路径实现。

## 1. 能力范围

- 文本转语音：`textToSpeech(...)`
- 语音转文本：`transcription(...)`
- 音频翻译：`translation(...)`

## 2. 文本转语音（TTS）

```java
IAudioService audioService = aiService.getAudioService(PlatformType.OPENAI);

TextToSpeech req = TextToSpeech.builder()
        .model("tts-1")
        .voice("alloy")
        .input("欢迎使用 ai4j")
        .responseFormat("mp3")
        .speed(1.0)
        .build();

InputStream stream = audioService.textToSpeech(req);
// 自行写文件或返回给前端
```

## 3. 语音转文本（Transcription）

```java
Transcription req = Transcription.builder()
        .file(new File("D:/audio/demo.mp3"))
        .model("whisper-1")
        .language("zh")
        .responseFormat("json")
        .build();

TranscriptionResponse res = audioService.transcription(req);
System.out.println(res.getText());
```

## 4. 音频翻译（Translation）

```java
Translation req = Translation.builder()
        .file(new File("D:/audio/jp.wav"))
        .model("whisper-1")
        .responseFormat("json")
        .build();

TranslationResponse res = audioService.translation(req);
System.out.println(res.getText());
```

## 5. 文件格式限制

`Transcription` / `Translation` 对文件后缀有校验，允许：

- `flac`
- `mp3`
- `mp4`
- `mpeg`
- `mpga`
- `m4a`
- `ogg`
- `wav`
- `webm`

不在白名单会直接抛 `IllegalArgumentException`。

## 6. 生产建议

- 语音文件先做大小限制
- 上传后做临时文件清理
- 长音频建议分段处理
- 对敏感音频内容做脱敏存储

## 7. 常见问题

### 7.1 返回流为空

- 检查 TTS 请求参数是否完整
- 检查上游响应是否成功

### 7.2 转录乱码

- 显式指定 `language`
- 检查原音频质量与采样率

### 7.3 接口超时

- 大文件建议提高 read timeout
- 并发上传建议限流

## 8. 推荐集成模式

- API 层只负责文件接收
- Service 层统一封装音频参数
- 对外只暴露最终文本/文件 URL，不透传底层对象
