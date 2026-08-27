---
sidebar_position: 35
title: Video 接口（生成与轮询）
description: 讲解 IVideoService 视频生成用法：create 提交任务、retrieve 轮询状态、content 拉取成片字节流、remix 改写，OpenAI 兼容网关、multipart 提交与异步轮询模型。
tags: [how-to]
---

# Video 接口（生成与轮询）

视频生成能力统一在 `IVideoService`，当前支持：

- `OPENAI`（含 ChatFire 的 `/v1/videos` 网关）

与 Chat/Image 不同，视频生成是 **异步提交 → 轮询** 模型：`create` 只负责提交任务并立即返回任务对象，真正拿到成片需要 `retrieve` 轮询状态、再用 `content` 拉取字节流。

## 1. 统一入口

```java
AiService aiService = new AiService(configuration);

IVideoService videoService = aiService.getVideoService(PlatformType.OPENAI);
```

`IVideoService` 暴露四类能力，每类都提供显式传 `baseUrl` / `apiKey` 的重载与回退默认配置的重载：

- `create(...)` —— 提交生成任务
- `retrieve(...)` —— 轮询任务状态
- `content(...)` —— 拉取成片字节流
- `remix(...)` —— 基于已有视频改写

## 2. 提交任务（`create`）

```java
VideoCreateRequest request = VideoCreateRequest.builder()
        .model("veo3.1")
        .prompt("飞上天")
        .seconds(8)
        .size("1280x720")
        .build();

VideoResponse response = videoService.create(request);
// response.getStatus() 通常为 "queued"，response.getId() 是后续轮询/拉取用的任务 id
```

`create` 走 **multipart/form-data**，而不是 JSON。核心字段：

- `model`（必填）
- `prompt`（必填）
- `seconds` —— 时长
- `size` —— 分辨率

除核心字段外，请求对象还预留了两组透传通道，用于承接不同网关的差异化参数：

- `extraFields` —— 额外的表单字段（如 `enable_upsample`）
- `headers` —— 自定义请求头

```java
Map<String, Object> extra = new LinkedHashMap<String, Object>();
extra.put("enable_upsample", "true");

VideoCreateRequest request = VideoCreateRequest.builder()
        .model("veo3.1")
        .prompt("飞上天")
        .seconds(8)
        .size("1280x720")
        .extraFields(extra)
        .build();
```

## 3. 轮询状态（`retrieve`）

任务提交后，业务层需要自己按一定间隔轮询，直到 `status` 进入终态：

```java
VideoResponse status = videoService.retrieve(response.getId());
// status.getStatus()  : queued / in-progress / completed / failed
// status.getProgress(): 0~100
// status.getVideoUrl(): completed 后出现的成片直链
```

:::note
SDK 不内建自动轮询循环。何时轮询、轮询间隔、超时与重试，都由调用方决定。`retrieve` 只是一次同步 GET，不阻塞等待成片。
:::

## 4. 拉取成片字节流（`content`）

`completed` 之后，可以用 `content` 直接拿到成片的字节流，而不依赖临时 URL：

```java
try (InputStream stream = videoService.content(response.getId())) {
    // 自行写入磁盘 / 转存对象存储
}
```

:::warning
`content` 返回的是包装过 HTTP response 的 `InputStream`，**谁消费谁关闭**。不关闭会导致底层连接一直占用。
:::

`content` 会对任务 id 做 URL 编码，因此 id 中带 `:` `/` 等特殊字符（例如 `video_1:openai/sora-2-t2v`）也能正确请求到 `/{id}/content`。

## 5. 改写已有视频（`remix`）

`remix` 基于一个已存在的视频 id，发起新的改写任务，入参只有一个 `prompt`：

```java
VideoResponse remix = videoService.remix("video-1", "让背景变成蓝天");
// 返回的是一个全新的任务对象，仍需走 retrieve/content 生命周期
```

`remix` 走 JSON body（`{ "prompt": "..." }`），POST 到 `/{id}/remix`，返回的是新任务而非原地修改。

## 6. 响应对象（`VideoResponse`）

`VideoResponse` 字段：

- `id` —— 任务 id
- `object` —— 资源类型（通常为 `video`）
- `status` —— 任务状态
- `model` / `size` / `seconds`
- `progress` —— 完成百分比
- `videoUrl` —— 成片直链（completed 后出现）
- `createdAt` —— 创建时间戳
- `raw` —— provider 原始 JSON，未建模字段的兜底入口

## 7. 一条完整的提交 → 轮询 → 拉取链路

```java
IVideoService videoService = aiService.getVideoService(PlatformType.OPENAI);

// 1. 提交
VideoResponse created = videoService.create(VideoCreateRequest.builder()
        .model("veo3.1")
        .prompt("飞上天")
        .seconds(8)
        .size("1280x720")
        .build());

// 2. 轮询（间隔与上限由业务层控制）
VideoResponse latest = videoService.retrieve(created.getId());
while (!"completed".equalsIgnoreCase(latest.getStatus())
        && !"failed".equalsIgnoreCase(latest.getStatus())) {
    Thread.sleep(5000L);
    latest = videoService.retrieve(created.getId());
}

// 3. 拉取成片
if ("completed".equalsIgnoreCase(latest.getStatus())) {
    try (InputStream stream = videoService.content(created.getId())) {
        // 写盘 / 转存
    }
}
```

## 8. 常见问题

### 8.1 调了 `create` 却拿不到视频

`create` 是异步提交，只返回任务对象。拿到成片必须再走 `retrieve` 轮询 + `content` 拉取，不能直接从 `create` 的响应里取字节。

### 8.2 URL 时效性

:::tip
`videoUrl` 通常是临时直链，应尽快下载或转存到自有对象存储，避免链接过期失效。如果不想依赖直链，可直接用 `content` 拉字节流。
:::

### 8.3 网关差异字段

不同 OpenAI 兼容网关可能有各自的额外参数。优先用 `extraFields` / `headers` 透传，不要为此改动请求对象的核心字段定义。

## 9. 这一页的结论

> `IVideoService` 当前是一条由 OpenAI 兼容网关实现的异步视频生成 service 面：`create` 用 multipart 提交任务，`retrieve` 轮询状态，`content` 拉取成片字节流，`remix` 改写已有视频。SDK 不内建轮询循环，提交→轮询→拉取的生命周期编排由调用方负责。
