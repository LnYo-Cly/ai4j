---
sidebar_position: 31
---

# Audio 接口

音频能力在 AI4J 当前是一条 **仅由 OpenAI 路径实现的独立 service 面**，而不是所有 provider 共享的通用能力。

这一页真正要讲清的是：它目前支持什么、实现有多薄、哪些校验发生在请求对象层、以及哪些资源管理责任仍在调用方手里。

## 1. 当前支持矩阵

从 `AiService.createAudioService(...)` 的实际分发看，当前音频能力只支持：

- `OPENAI`

也就是说，Audio 和 Chat/Embedding 不同，它目前不是多 provider 的统一能力面，而是单 provider 的正式 service 面。

## 2. 统一契约长什么样

统一入口是：

- `IAudioService`

它当前暴露三类能力：

- `textToSpeech(...)`
- `transcription(...)`
- `translation(...)`

每一类都同时提供：

- 显式传 `baseUrl` / `apiKey` 的重载
- 使用默认配置回退的重载

所以音频层和 embedding 一样，也支持单次调用覆盖配置。

## 3. `OpenAiAudioService` 的真实行为

`OpenAiAudioService` 本质上是一个比较薄的 HTTP 包装层，但它有几个关键细节值得明确写出来。

### 文本转语音

`textToSpeech(...)` 会：

- 把 `TextToSpeech` 直接序列化成 JSON
- POST 到 `speechUrl`
- 成功时返回 `InputStream`

这里最重要的实现细节不是“能返回流”，而是它使用了内部的 `ResponseInputStream` 包装，这样调用方在方法返回后仍然可以继续读取响应流，直到关闭流时才真正关闭 HTTP response。

测试 `OpenAiAudioServiceTest` 已经专门验证了这件事。

### 转录与翻译

`transcription(...)` 和 `translation(...)` 都走 multipart/form-data：

- file
- model
- temperature
- 以及若干可选字段

成功时解析成：

- `TranscriptionResponse`
- `TranslationResponse`

失败时当前实现不会抛出结构化业务异常，而是打印异常并返回 `null`。

## 4. 请求对象层已经做了什么校验

### `TextToSpeech`

请求对象 `TextToSpeech` 直接承载了几个真实约束：

- 默认 `model = "tts-1"`
- 默认 `voice = alloy`
- 默认 `responseFormat = mp3`
- 默认 `speed = 1.0`
- `input` 必填

它更像一个“带默认值的 provider 请求对象”，而不是一个完全抽象掉 provider 差异的统一 DSL。

### `Transcription` / `Translation`

这两个对象在 builder 和 setter 层都做了文件格式白名单校验。  
当前允许的后缀包括：

- `flac`
- `mp3`
- `mp4`
- `mpeg`
- `mpga`
- `m4a`
- `ogg`
- `wav`
- `webm`

不在白名单会直接抛 `IllegalArgumentException`，也就是说有一部分输入合法性约束是前移到请求对象构造阶段的，而不是等 HTTP 请求发出去才失败。

## 5. 当前实现的资源与失败语义

### TTS 流由调用方负责关闭

因为 `textToSpeech(...)` 返回的是可继续读取的 `InputStream`，所以谁消费这个流，谁就应该关闭它。  
否则底层 HTTP response 会保持占用状态。

### 非成功响应通常返回 `null`

不论是 TTS、Transcription 还是 Translation，当前实现都没有构造统一错误对象。  
这意味着业务层需要自己决定：

- 是否把 `null` 转成异常
- 是否做重试
- 是否记录 provider 原始失败信息

### 大文件处理责任不在 SDK 内

当前实现只是把 `File` 放进 multipart request，不会自动帮你做：

- 文件大小限制
- 分段上传
- 临时文件清理
- 存储脱敏

这些都仍然是业务接入层的责任。

## 6. 为什么这一层目前还比较“OpenAI 原生”

虽然 Audio 被挂在统一 `IAudioService` 下，但它当前的请求对象和 URL 结构都明显贴近 OpenAI：

- `speechUrl`
- `transcriptionUrl`
- `translationUrl`
- `whisper-1`
- `tts-1`

这说明这条能力面目前更像“正式收口到 service 接口的 OpenAI 能力”，而不是已经被多个 provider 充分验证过的跨平台抽象。

## 7. 什么时候只看这页不够

如果你开始关心：

- 文件上传网关设计
- 大音频分片
- 存储生命周期
- 前后端流式回放

那说明你已经超出 Core SDK 音频 service 本身，问题会进入应用层接口设计，而不是 SDK 这层能直接解决的范围。

## 8. 这一页的结论

> AI4J 当前的 Audio 能力是一条由 OpenAI 路径独占实现的正式 service 面。它已经把 TTS、转录和翻译统一进 `IAudioService`，并在请求对象层做了一部分输入校验，但错误治理、文件生命周期和大文件处理仍然主要属于业务接入层责任。
