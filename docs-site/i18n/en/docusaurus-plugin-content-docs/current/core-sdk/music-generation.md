---
sidebar_position: 36
title: "Music Interface (Generation and Lyrics)"
description: "Covers IMusicService music generation usage: submitMusic/submitLyrics to submit tasks, fetch to poll task results, Suno adapter, task lifecycle, field model, and polling notes."
tags: [how-to]
---

# Music Interface (Generation and Lyrics)

Music generation capability is unified under `IMusicService`, currently supporting:

- `SUNO` (via the ChatFire native Suno gateway)

As with video generation, music generation follows an **async submit → poll** model: `submitMusic` / `submitLyrics` only submit the task and immediately return a task id. To actually obtain the track you must `fetch` the task result.

## 1. Unified Entry Point

```java
AiService aiService = new AiService(configuration);

IMusicService musicService = aiService.getMusicService(PlatformType.SUNO);
```

`IMusicService` exposes three categories of capability. Each provides an overload that takes `baseUrl` / `apiKey` explicitly, plus an overload that falls back to the default configuration:

- `submitMusic(...)` — submit a music generation task
- `submitLyrics(...)` — submit a lyrics generation task
- `fetch(...)` — poll the task result

## 2. Submit Music Generation (`submitMusic`)

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
// response.isSuccess() indicates whether submission succeeded
// response.getData() is the taskId used by subsequent fetch calls
```

`submitMusic` POSTs to the Suno gateway's music submission endpoint (default `suno/submit/music`) and returns a `SunoSubmitResponse`:

- `code` — `success` indicates success
- `message` — informational message
- `data` — **task id** (string), the input to `fetch`
- `raw` — the provider's original JSON

## 3. Key Fields of `SunoMusicRequest`

`SunoMusicRequest` uses `@JsonAnyGetter` / `@JsonAnySetter` to reserve an `extraFields` pass-through. Its core fields follow Suno's native semantics:

- `prompt` — lyrics / description body
- `tags` — style tags
- `mv` — model version (e.g. `chirp-v4`)
- `title` — track title
- `gptDescriptionPrompt` — GPT description prompt
- `makeInstrumental` — whether to generate instrumental only
- `generationType` — generation type
- `negativeTags` — negative style tags
- `continueAt` / `continueClipId` / `continuedAlignedPrompt` — continuation-related
- `infillStartS` / `infillEndS` — partial infill (seconds)
- `task` — task type
- `coverClipId` — cover clip id

## 4. Submit Lyrics Generation (`submitLyrics`)

`submitLyrics` targets a separate lyrics endpoint (default `suno/submit/lyrics`) and only accepts `prompt`:

```java
Map<String, Object> extra = new LinkedHashMap<String, Object>();
extra.put("language", "zh");

SunoSubmitResponse response = musicService.submitLyrics(SunoLyricsRequest.builder()
        .prompt("chat fire")
        .extraFields(extra)
        .build());
```

The response structure matches `submitMusic`; `data` is still a task id.

## 5. Poll the Task Result (`fetch`)

```java
SunoFetchResponse result = musicService.fetch(response.getData());
// result.isSuccess()            : whether the call succeeded
// result.getData()              : SunoTask
// result.getData().getStatus()  : task status (e.g. SUCCESS / IN_PROGRESS)
// result.getData().getData()    : track array (JsonNode)
```

`fetch` URL-encodes the task id, so ids containing `/`, `:`, spaces, and similar characters are still requested correctly. `fetchUrl` also supports the `{task_id}` placeholder form (e.g. `suno/fetch/{task_id}`), configured via `SunoConfig`.

## 6. Task Objects (`SunoTask` / `SunoSong`)

`SunoFetchResponse.data` is a `SunoTask`:

- `taskId`
- `action` — task action (e.g. `MUSIC`)
- `status` — status (`IN_PROGRESS` / `SUCCESS` / failure)
- `failReason`
- `submitTime` / `startTime` / `finishTime`
- `progress`
- `data` — result payload (`JsonNode`). Under the `MUSIC` action this is typically a set of `SunoSong`

`SunoTask.data` is a loosely typed `JsonNode`, because the result structure differs across Suno actions. Array elements of a `MUSIC` result can be deserialized into `SunoSong`:

```java
JsonNode data = result.getData().getData();
if (data != null && data.isArray()) {
    for (JsonNode node : data) {
        SunoSong song = new ObjectMapper().treeToValue(node, SunoSong.class);
        // song.getAudioUrl() / song.getImageUrl() / song.getVideoUrl() ...
    }
}
```

Modeled fields on `SunoSong`:

- `id` / `clipId` / `title` / `handle`
- `tags` / `prompt` / `state` / `status`
- `duration` / `metadata`
- `audioUrl` — direct audio URL
- `imageUrl` / `imageLargeUrl` — cover image
- `videoUrl` — visualization video direct URL
- `modelName`

## 7. A Complete Submit → Poll → Retrieve Track Flow

```java
IMusicService musicService = aiService.getMusicService(PlatformType.SUNO);

// 1. Submit
SunoSubmitResponse submitted = musicService.submitMusic(SunoMusicRequest.builder()
        .prompt("[Verse] city lights")
        .tags("emotional punk")
        .mv("chirp-v4")
        .title("City Lights")
        .makeInstrumental(Boolean.FALSE)
        .build());

String taskId = submitted.getData();

// 2. Poll (interval and retry limit controlled by the business layer)
SunoFetchResponse latest = musicService.fetch(taskId);
while (!"SUCCESS".equalsIgnoreCase(latest.getData().getStatus())
        && latest.getData().getFailReason() == null) {
    Thread.sleep(5000L);
    latest = musicService.fetch(taskId);
}

// 3. Retrieve tracks
if (latest.isSuccess() && latest.getData().getData() != null
        && latest.getData().getData().isArray()) {
    for (JsonNode node : latest.getData().getData()) {
        SunoSong song = new ObjectMapper().treeToValue(node, SunoSong.class);
        System.out.println(song.getAudioUrl());
    }
}
```

:::note
The SDK does not build in an automatic polling loop. When to poll, the polling interval, timeout, and retries are all decided by the caller. `fetch` is a single synchronous GET.
:::

## 8. Common Issues

### 8.1 What `isSuccess()` Actually Checks

The `isSuccess()` on `SunoSubmitResponse` and `SunoFetchResponse` only checks `code == "success"`, meaning the **HTTP call itself succeeded** — it does not mean the music has finished generating. For generation progress, check `SunoTask.status`.

### 8.2 URL Lifespan

:::tip
`audioUrl` / `videoUrl` / `imageUrl` are typically temporary direct links. Download them promptly or transfer them to your own object storage to avoid link expiration.
:::

### 8.3 Endpoints and Placeholders

`SunoConfig` defaults to `apiHost=https://api.chatfire.cn/`, `musicUrl=suno/submit/music`, `lyricsUrl=suno/submit/lyrics`, `fetchUrl=suno/fetch`. If the gateway's fetch endpoint uses a path-parameter form, configure `fetchUrl` as a template with the `{task_id}` placeholder (e.g. `suno/fetch/{task_id}`).

## 9. Takeaway

> `IMusicService` is currently an async music generation service surface implemented by the Suno gateway: `submitMusic` / `submitLyrics` submit a task to obtain a task id, and `fetch` polls the task result. The task result is a loosely typed `JsonNode`; under the `MUSIC` action it can be deserialized into `SunoSong`. The SDK does not build in a polling loop — the submit → poll → retrieve lifecycle orchestration is the caller's responsibility.
