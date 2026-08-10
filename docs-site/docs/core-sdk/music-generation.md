---
sidebar_position: 36
title: Music 接口（生成与歌词）
description: 讲解 IMusicService 音乐生成用法：submitMusic/submitLyrics 提交任务、fetch 轮询任务结果，Suno 适配、任务生命周期、字段模型与轮询要点。
tags: [how-to]
---

# Music 接口（生成与歌词）

音乐生成能力统一在 `IMusicService`，当前支持：

- `SUNO`（经 ChatFire 的 Suno 原生网关）

和视频生成一样，音乐生成是 **异步提交 → 轮询** 模型：`submitMusic` / `submitLyrics` 只提交任务并立即返回 task id，真正拿到曲目需要 `fetch` 轮询任务结果。

## 1. 统一入口

```java
AiService aiService = new AiService(configuration);

IMusicService musicService = aiService.getMusicService(PlatformType.SUNO);
```

`IMusicService` 暴露三类能力，每类都提供显式传 `baseUrl` / `apiKey` 的重载与回退默认配置的重载：

- `submitMusic(...)` —— 提交音乐生成任务
- `submitLyrics(...)` —— 提交歌词生成任务
- `fetch(...)` —— 轮询任务结果

## 2. 提交音乐生成（`submitMusic`）

```java
SunoMusicRequest request = SunoMusicRequest.builder()
        .prompt("[Verse] city lights")
        .tags("emotional punk")
        .mv("chirp-v4")
        .title("City Lights")
        .makeInstrumental(Boolean.FALSE)
        .gptDescriptionPrompt("write a song")
        .build();

SunoSubmitResponse response = musicService.submitMusic(request);
// response.isSuccess() 判断是否提交成功
// response.getData() 就是后续 fetch 用的 taskId
```

`submitMusic` POST 到 Suno 网关的音乐提交端点（默认 `suno/submit/music`），返回 `SunoSubmitResponse`：

- `code` —— `success` 表示成功
- `message` —— 提示信息
- `data` —— **task id**（字符串），是 `fetch` 的入参
- `raw` —— provider 原始 JSON

## 3. `SunoMusicRequest` 主要字段

`SunoMusicRequest` 用 `@JsonAnyGetter` / `@JsonAnySetter` 预留了 `extraFields` 透传，核心字段贴近 Suno 原生语义：

- `prompt` —— 歌词/描述正文
- `tags` —— 风格标签
- `mv` —— 模型版本（如 `chirp-v4`）
- `title` —— 曲目标题
- `gptDescriptionPrompt` —— GPT 描述提示
- `makeInstrumental` —— 是否纯音乐
- `generationType` —— 生成类型
- `negativeTags` —— 负向风格标签
- `continueAt` / `continueClipId` / `continuedAlignedPrompt` —— 续写相关
- `infillStartS` / `infillEndS` —— 局部填充（秒）
- `task` —— 任务类型
- `coverClipId` —— 封面 clip id

## 4. 提交歌词生成（`submitLyrics`）

`submitLyrics` 单独走歌词端点（默认 `suno/submit/lyrics`），只接受 `prompt`：

```java
Map<String, Object> extra = new LinkedHashMap<String, Object>();
extra.put("language", "zh");

SunoSubmitResponse response = musicService.submitLyrics(SunoLyricsRequest.builder()
        .prompt("chat fire")
        .extraFields(extra)
        .build());
```

返回结构与 `submitMusic` 一致，`data` 仍是 task id。

## 5. 轮询任务结果（`fetch`）

```java
SunoFetchResponse result = musicService.fetch(response.getData());
// result.isSuccess()            : 是否成功
// result.getData()              : SunoTask
// result.getData().getStatus()  : 任务状态（如 SUCCESS / IN_PROGRESS）
// result.getData().getData()    : 曲目数组（JsonNode）
```

`fetch` 会对 task id 做 URL 编码，因此 id 中带 `/` `:` 空格等字符也能正确请求。`fetchUrl` 还支持 `{task_id}` 占位符形式（如 `suno/fetch/{task_id}`），由 `SunoConfig` 配置。

## 6. 任务对象（`SunoTask` / `SunoSong`）

`SunoFetchResponse.data` 是 `SunoTask`：

- `taskId`
- `action` —— 任务动作（如 `MUSIC`）
- `status` —— 状态（`IN_PROGRESS` / `SUCCESS` / 失败）
- `failReason`
- `submitTime` / `startTime` / `finishTime`
- `progress`
- `data` —— 结果载荷（`JsonNode`）。`MUSIC` 动作下通常是一组 `SunoSong`

`SunoTask.data` 是松类型 `JsonNode`，因为 Suno 不同 action 的结果结构并不一致。`MUSIC` 的数组元素可反序列化成 `SunoSong`：

```java
JsonNode data = result.getData().getData();
if (data != null && data.isArray()) {
    for (JsonNode node : data) {
        SunoSong song = new ObjectMapper().treeToValue(node, SunoSong.class);
        // song.getAudioUrl() / song.getImageUrl() / song.getVideoUrl() ...
    }
}
```

`SunoSong` 已建模字段：

- `id` / `clipId` / `title` / `handle`
- `tags` / `prompt` / `state` / `status`
- `duration` / `metadata`
- `audioUrl` —— 音频直链
- `imageUrl` / `imageLargeUrl` —— 封面图
- `videoUrl` —— 可视化视频直链
- `modelName`

## 7. 一条完整的提交 → 轮询 → 取曲链路

```java
IMusicService musicService = aiService.getMusicService(PlatformType.SUNO);

// 1. 提交
SunoSubmitResponse submitted = musicService.submitMusic(SunoMusicRequest.builder()
        .prompt("[Verse] city lights")
        .tags("emotional punk")
        .mv("chirp-v4")
        .title("City Lights")
        .makeInstrumental(Boolean.FALSE)
        .build());

String taskId = submitted.getData();

// 2. 轮询（间隔与上限由业务层控制）
SunoFetchResponse latest = musicService.fetch(taskId);
while (!"SUCCESS".equalsIgnoreCase(latest.getData().getStatus())
        && latest.getData().getFailReason() == null) {
    Thread.sleep(5000L);
    latest = musicService.fetch(taskId);
}

// 3. 取曲
if (latest.isSuccess() && latest.getData().getData() != null
        && latest.getData().getData().isArray()) {
    for (JsonNode node : latest.getData().getData()) {
        SunoSong song = new ObjectMapper().treeToValue(node, SunoSong.class);
        System.out.println(song.getAudioUrl());
    }
}
```

:::note
SDK 不内建自动轮询循环。何时轮询、轮询间隔、超时与重试，都由调用方决定。`fetch` 只是一次同步 GET。
:::

## 8. 常见问题

### 8.1 `isSuccess()` 到底判断的是什么

`SunoSubmitResponse` 和 `SunoFetchResponse` 的 `isSuccess()` 只判断 `code == "success"`，即 **HTTP 调用本身成功**，不代表音乐已经生成完成。生成进度要看 `SunoTask.status`。

### 8.2 URL 时效性

:::tip
`audioUrl` / `videoUrl` / `imageUrl` 通常是临时直链，应尽快下载或转存到自有对象存储，避免链接过期失效。
:::

### 8.3 端点与占位符

`SunoConfig` 默认 `apiHost=https://api.chatfire.cn/`、`musicUrl=suno/submit/music`、`lyricsUrl=suno/submit/lyrics`、`fetchUrl=suno/fetch`。若网关 fetch 端点使用路径参数形式，可把 `fetchUrl` 配成带 `{task_id}` 占位符的模板（如 `suno/fetch/{task_id}`）。

## 9. 这一页的结论

> `IMusicService` 当前是一条由 Suno 网关实现的异步音乐生成 service 面：`submitMusic` / `submitLyrics` 提交任务拿到 task id，`fetch` 轮询任务结果。任务结果是松类型 `JsonNode`，`MUSIC` 动作下可反序列化成 `SunoSong`。SDK 不内建轮询循环，提交→轮询→取曲的生命周期编排由调用方负责。
