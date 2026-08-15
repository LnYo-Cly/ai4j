---
sidebar_position: 35
title: "Video API (Generation and Polling)"
description: "Explains IVideoService video generation usage: create to submit a task, retrieve to poll status, content to fetch the result byte stream, and remix to rework; covers the OpenAI-compatible gateway, multipart submission, and the async polling model."
tags: [how-to]
---

# Video API (Generation and Polling)

Video generation capability is unified under `IVideoService`, which currently supports:

- `OPENAI` (including ChatFire's `/v1/videos` gateway)

Unlike Chat/Image, video generation follows an **async submit → poll** model: `create` only submits the task and immediately returns a task object. To actually obtain the finished video you must poll status with `retrieve`, then fetch the byte stream with `content`.

## 1. Unified entry point

```java
AiService aiService = new AiService(configuration);

IVideoService videoService = aiService.getVideoService(PlatformType.OPENAI);
```

`IVideoService` exposes four groups of capabilities. Each group provides overloads that take an explicit `baseUrl` / `apiKey`, as well as overloads that fall back to the default configuration:

- `create(...)` —— submits a generation task
- `retrieve(...)` —— polls task status
- `content(...)` —— fetches the finished video as a byte stream
- `remix(...)` —— reworks an existing video

## 2. Submitting a task (`create`)

```java
VideoCreateRequest request = VideoCreateRequest.builder()
        .model("veo3.1")
        .prompt("fly into the sky")
        .seconds(8)
        .size("1280x720")
        .build();

VideoResponse response = videoService.create(request);
// response.getStatus() is typically "queued"; response.getId() is the task id used for later polling/fetching
```

`create` sends **multipart/form-data**, not JSON. Core fields:

- `model` (required)
- `prompt` (required)
- `seconds` —— duration
- `size` —— resolution

Beyond the core fields, the request object reserves three passthrough channels to carry parameters that differ across gateways:

- `extraFields` —— additional form fields (e.g. `enable_upsample`)
- `fileFields` —— file fields such as reference images / reference videos
- `headers` —— custom request headers

```java
Map<String, Object> extra = new LinkedHashMap<String, Object>();
extra.put("enable_upsample", "true");

VideoCreateRequest request = VideoCreateRequest.builder()
        .model("veo3.1")
        .prompt("fly into the sky")
        .seconds(8)
        .size("1280x720")
        .extraFields(extra)
        .build();
```

## 3. Polling status (`retrieve`)

After the task is submitted, the business layer must poll at its own interval until `status` reaches a terminal state:

```java
VideoResponse status = videoService.retrieve(response.getId());
// status.getStatus()  : queued / in-progress / completed / failed
// status.getProgress(): 0~100
// status.getVideoUrl(): direct link to the finished video, present once completed
```

:::note
The SDK does not build in an automatic polling loop. When to poll, the polling interval, timeouts, and retries are all decided by the caller. `retrieve` is just a single synchronous GET; it does not block waiting for the finished video.
:::

## 4. Fetching the result byte stream (`content`)

Once `completed`, you can use `content` to get the finished video as a byte stream directly, without depending on a temporary URL:

```java
try (InputStream stream = videoService.content(response.getId())) {
    // write to disk / transfer to object storage yourself
}
```

:::warning
`content` returns an `InputStream` that wraps the HTTP response — **whoever consumes it closes it**. Leaving it open will hold the underlying connection indefinitely.
:::

`content` URL-encodes the task id, so an id containing special characters such as `:` or `/` (for example `video_1:openai/sora-2-t2v`) still correctly requests `/{id}/content`.

## 5. Reworking an existing video (`remix`)

`remix` takes an existing video id and starts a new rework task; its only input is a `prompt`:

```java
VideoResponse remix = videoService.remix("video-1", "change the background to a blue sky");
// returns a brand-new task object, which still goes through the retrieve/content lifecycle
```

`remix` sends a JSON body (`{ "prompt": "..." }`), POSTing to `/{id}/remix`, and returns a new task rather than modifying the original in place.

## 6. The response object (`VideoResponse`)

`VideoResponse` fields:

- `id` —— task id
- `object` —— resource type (typically `video`)
- `status` —— task status
- `model` / `size` / `seconds`
- `progress` —— completion percentage
- `videoUrl` —— direct link to the finished video (present once completed)
- `createdAt` —— creation timestamp
- `raw` —— the provider's original JSON, a fallback entry point for unmodeled fields

## 7. A complete submit → poll → fetch flow

```java
IVideoService videoService = aiService.getVideoService(PlatformType.OPENAI);

// 1. Submit
VideoResponse created = videoService.create(VideoCreateRequest.builder()
        .model("veo3.1")
        .prompt("fly into the sky")
        .seconds(8)
        .size("1280x720")
        .build());

// 2. Poll (interval and retry cap are controlled by the business layer)
VideoResponse latest = videoService.retrieve(created.getId());
while (!"completed".equalsIgnoreCase(latest.getStatus())
        && !"failed".equalsIgnoreCase(latest.getStatus())) {
    Thread.sleep(5000L);
    latest = videoService.retrieve(created.getId());
}

// 3. Fetch the finished video
if ("completed".equalsIgnoreCase(latest.getStatus())) {
    try (InputStream stream = videoService.content(created.getId())) {
        // write to disk / transfer
    }
}
```

## 8. Common issues

### 8.1 Called `create` but got no video

`create` is an async submission that only returns a task object. To get the finished video you must follow up with `retrieve` polling and a `content` fetch; you cannot read the bytes directly from the `create` response.

### 8.2 URL expiration

:::tip
`videoUrl` is usually a temporary direct link. Download it promptly or transfer it to your own object storage before the link expires. If you don't want to depend on the direct link, use `content` to fetch the byte stream directly.
:::

### 8.3 Gateway-specific fields

Different OpenAI-compatible gateways may have their own extra parameters. Prefer to pass them through `extraFields` / `fileFields` / `headers`; do not modify the core field definitions of the request object for this purpose.

## 9. Takeaways for this page

> `IVideoService` is currently a single async video-generation service surface implemented by an OpenAI-compatible gateway: `create` submits a task via multipart, `retrieve` polls status, `content` fetches the finished byte stream, and `remix` reworks an existing video. The SDK does not build in a polling loop; the orchestration of the submit → poll → fetch lifecycle is the caller's responsibility.
