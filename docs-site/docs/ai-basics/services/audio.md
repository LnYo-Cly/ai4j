---
sidebar_position: 31
---

# Audio 接口

音频能力统一由 `IAudioService` 提供，当前实现主线是 OpenAI 路径。

本页重点回答：

1. `TTS`、`Transcription`、`Translation` 分别怎么调用
2. 返回值应该怎么接，不同能力的结果类型有什么差异
3. 音频接口上线时最容易踩哪些坑

---

## 1. 能力边界

当前 `IAudioService` 主要覆盖三类能力：

- 文本转语音：`textToSpeech(...)`
- 语音转文本：`transcription(...)`
- 音频翻译：`translation(...)`

这三类能力虽然都在同一服务接口下，但输入输出模型并不相同：

- `TTS` 更像“文本 -> 二进制音频流”
- `Transcription` 更像“音频文件 -> 文本结果”
- `Translation` 更像“外语音频 -> 英文文本结果”

---

## 2. 获取服务入口

```java
IAudioService audioService = aiService.getAudioService(PlatformType.OPENAI);
```

如果当前平台不支持音频能力，`AiService` 会直接抛异常，而不是静默回退到其他服务。

---

## 3. 文本转语音（TTS）

### 3.1 最小示例

```java
IAudioService audioService = aiService.getAudioService(PlatformType.OPENAI);

TextToSpeech request = TextToSpeech.builder()
        .model("tts-1")
        .voice("alloy")
        .input("欢迎使用 AI4J")
        .responseFormat("mp3")
        .speed(1.0)
        .build();

InputStream stream = audioService.textToSpeech(request);
```

### 3.2 关键请求字段

- `model`：TTS 模型名
- `voice`：音色
- `input`：待合成文本
- `responseFormat`：例如 `mp3`
- `speed`：语速

### 3.3 返回值怎么处理

`textToSpeech(...)` 返回的是 `InputStream`，不是字符串，也不是 DTO。

常见接法有三种：

- 直接写到本地文件
- 转发给 HTTP 响应输出流
- 上传到对象存储后返回 URL

示例：

```java
try (InputStream stream = audioService.textToSpeech(request);
     OutputStream out = new FileOutputStream("D:/audio/demo.mp3")) {
    byte[] buffer = new byte[8192];
    int len;
    while ((len = stream.read(buffer)) != -1) {
        out.write(buffer, 0, len);
    }
}
```

### 3.4 适合的业务场景

- 文本朗读
- 语音播报
- 智能客服语音回放
- 内容生成后的音频化分发

---

## 4. 语音转文本（Transcription）

### 4.1 最小示例

```java
Transcription request = Transcription.builder()
        .file(new File("D:/audio/demo.mp3"))
        .model("whisper-1")
        .language("zh")
        .responseFormat("json")
        .build();

TranscriptionResponse response = audioService.transcription(request);
System.out.println(response.getText());
```

### 4.2 关键请求字段

- `file`：待识别音频文件
- `model`：转录模型
- `language`：语言提示，建议显式传
- `responseFormat`：通常为 `json`

### 4.3 主结果字段

业务最常关心的是：

```java
response.getText()
```

推荐把底层 `TranscriptionResponse` 封装成业务 DTO，不要在 Controller 中直接展开全部字段。

### 4.4 适合的业务场景

- 会议录音转写
- 语音输入
- 呼叫中心质检
- 音频归档与检索

---

## 5. 音频翻译（Translation）

### 5.1 最小示例

```java
Translation request = Translation.builder()
        .file(new File("D:/audio/jp.wav"))
        .model("whisper-1")
        .responseFormat("json")
        .build();

TranslationResponse response = audioService.translation(request);
System.out.println(response.getText());
```

### 5.2 主结果字段

最常用读取方式同样是：

```java
response.getText()
```

### 5.3 适合的业务场景

- 跨语种客服内容归档
- 海外音频素材理解
- 多语会议内容统一转英文处理

---

## 6. 文件格式与输入边界

`Transcription` 和 `Translation` 对文件后缀有明确校验。

允许格式包括：

- `flac`
- `mp3`
- `mp4`
- `mpeg`
- `mpga`
- `m4a`
- `ogg`
- `wav`
- `webm`

如果文件后缀不在白名单，调用前就会抛 `IllegalArgumentException`。

因此在业务接入层建议先做两层校验：

1. 文件类型白名单
2. 文件大小限制

---

## 7. 生产环境推荐接法

### 7.1 API 层职责

API 层建议只负责：

- 接收上传文件
- 做大小与格式校验
- 生成临时文件或流对象

### 7.2 Service 层职责

Service 层负责：

- 构造 `TextToSpeech` / `Transcription` / `Translation`
- 统一超时、重试、日志、异常处理
- 统一返回业务结果

### 7.3 存储层职责

如果返回的是音频流，建议统一落到：

- 本地临时目录
- 对象存储
- CDN 静态资源地址

不要把大音频二进制长期直接塞进数据库。

---

## 8. 常见问题

### 8.1 返回流为空

通常优先检查：

- TTS 请求参数是否完整
- 上游接口是否真正返回了音频流
- `InputStream` 是否被提前关闭

### 8.2 转录结果乱码或语言不准

优先处理：

- 显式传入 `language`
- 检查原始音频质量
- 降低背景噪音

### 8.3 大文件超时

建议：

- 提高 `readTimeout`
- 对长音频分段处理
- 并发控制不要过高

### 8.4 临时文件泄露

上传类接口上线后，很容易出现临时文件忘删的问题。

建议统一在：

- finally 块
- 异步清理任务
- 对象存储成功回调

中做删除。

---

## 9. 推荐阅读

1. [统一服务入口与调用方式](/docs/ai-basics/unified-service-entry)
2. [统一请求与返回读取约定](/docs/ai-basics/request-and-response-conventions)
3. [Image 接口（生成与流式）](/docs/ai-basics/services/image-generation)
