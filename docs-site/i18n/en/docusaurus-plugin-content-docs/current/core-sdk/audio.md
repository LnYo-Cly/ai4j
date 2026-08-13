---
sidebar_position: 31
title: "Audio Service Interface"
description: "Introduces the AI4J audio service surface: unified entry points for TTS, transcription, and translation, the OpenAI implementation, request-object validation, and resource and failure semantics."
tags: [concept]
---

# Audio Service Interface

Audio capabilities in AI4J are currently a **standalone service surface implemented only by the OpenAI path**, not a general-purpose capability shared across all providers.

What this page actually clarifies: what it currently supports, how thin the implementation is, which validations happen at the request-object layer, and which resource-management responsibilities still rest with the caller.

:::tip The code on this page all runs
The TTS example below comes from
[`AudioAndRealtimeDocExamplesLiveTest`](https://github.com/LnYo-Cly/ai4j/blob/main/ai4j/src/test/java/io/github/lnyocly/ai4j/docs/AudioAndRealtimeDocExamplesLiveTest.java),
verified against a live gateway (`gpt-4o-mini-tts`).
:::

## 1. Current support matrix

Looking at the actual dispatch in `AiService.createAudioService(...)`, audio capabilities currently support only:

- `OPENAI`

In other words, unlike Chat/Embedding, Audio is not currently a multi-provider unified capability surface; it is a single provider's formal service surface.

## 2. What the unified contract looks like

The unified entry point is:

- `IAudioService`

It currently exposes three categories of capability:

- `textToSpeech(...)`
- `transcription(...)`
- `translation(...)`

Each category also provides:

- Overloads that take explicit `baseUrl` / `apiKey`
- Overloads that fall back to default configuration

So, like the embedding layer, the audio layer also supports per-call configuration override.

## 3. Actual behavior of `OpenAiAudioService`

`OpenAiAudioService` is essentially a fairly thin HTTP wrapper, but it has a few key details worth spelling out explicitly.

### Text to speech

`textToSpeech(...)`:

- Serializes `TextToSpeech` directly to JSON
- POSTs to `speechUrl`
- Returns an `InputStream` on success

The most important implementation detail here is not "it can return a stream", but that it uses an internal `ResponseInputStream` wrapper, so the caller can keep reading the response stream after the method returns; the underlying HTTP response is not actually closed until the stream is closed.

The test `OpenAiAudioServiceTest` specifically verifies this.

```java
IAudioService audio = new AiService(configuration).getAudioService(PlatformType.OPENAI);

InputStream speech = audio.textToSpeech(TextToSpeech.builder()
        .model("gpt-4o-mini-tts")
        .input("你好，这是 AI4J 的语音合成测试。")
        .voice("alloy")
        .build());

// The stream is the caller's responsibility to close; close it after consuming,
// otherwise the underlying HTTP response will not be released
Files.copy(speech, Paths.get("output.mp3"), StandardCopyOption.REPLACE_EXISTING);
speech.close();
```

### Transcription and translation

`transcription(...)` and `translation(...)` both go through multipart/form-data:

- file
- model
- temperature
- plus several optional fields

On success they parse into:

- `TranscriptionResponse`
- `TranslationResponse`

```java
File audioFile = new File("meeting.mp3");   // format must be in the allowlist

TranscriptionResponse resp = audio.transcription(Transcription.builder()
        .file(audioFile)
        .model("whisper-1")
        .language("zh")          // optional, improves accuracy
        .responseFormat("json")  // optional
        .build());

System.out.println(resp.getText());      // plain text
System.out.println(resp.getSegments());  // segments (when verbose_json)
```

On failure it throws a typed exception (`AiAuthException` / `AiRateLimitException` / `AiClientException`, etc.; see "Error semantics" below).

## 4. What validation the request-object layer already does

### `TextToSpeech`

The request object `TextToSpeech` directly carries several real constraints:

- Default `model = "tts-1"`
- Default `voice = alloy`
- Default `responseFormat = mp3`
- Default `speed = 1.0`
- `input` is required

It is more "a provider request object with defaults" than a unified DSL that fully abstracts away provider differences.

### `Transcription` / `Translation`

Both objects perform file-format allowlist validation at the builder and setter layers.
Currently allowed suffixes include:

- `flac`
- `mp3`
- `mp4`
- `mpeg`
- `mpga`
- `m4a`
- `ogg`
- `wav`
- `webm`

Anything outside the allowlist throws `IllegalArgumentException` directly; that is, a portion of the input-validity constraints are moved up to the request-object construction phase, rather than failing only after the HTTP request goes out.

## 5. Resource and failure semantics of the current implementation

### The caller is responsible for closing the TTS stream

:::warning
Because `textToSpeech(...)` returns an `InputStream` that can be read further, whoever consumes the stream should close it.
Otherwise the underlying HTTP response will remain held.
:::

### Error semantics: throw typed exceptions

A non-success response throws a typed exception decoded by `HttpErrorDecoder` (`AiAuthException` / `AiRateLimitException` / `AiServerErrorException` / `AiClientException`); the message carries the original error returned by the provider, and the status code can be read via `getStatusCode()`:

```java
try {
    TranscriptionResponse resp = audio.transcription(req);
} catch (AiRateLimitException e) {
    // rate-limited, can back off and retry
} catch (AiAuthException e) {
    // credential issue
} catch (AiHttpException e) {
    // other HTTP error; e.getStatusCode() + e.getMessage() contains the upstream original message
}
```

:::note Version differences
In v2.4.2 and earlier, transcription/translation failures would print the exception and **return `null`**; TTS likewise silently returned `null`, so the caller got a downstream NPE rather than the real cause. Since [PR #229](https://github.com/LnYo-Cly/ai4j/pull/229), this has been unified to throw typed exceptions.
:::

### Large-file handling is not the SDK's responsibility

The current implementation only puts the `File` into the multipart request; it does not automatically do the following for you:

- File size limits
- Chunked upload
- Temp-file cleanup
- Storage masking

These remain the responsibility of the business integration layer.

## 6. Why this layer is still fairly "OpenAI-native" for now

Although Audio hangs under the unified `IAudioService`, its current request objects and URL structure are clearly close to OpenAI:

- `speechUrl`
- `transcriptionUrl`
- `translationUrl`
- `whisper-1`
- `tts-1`

This shows that this capability surface is currently more "an OpenAI capability formally sealed into the service interface" than a cross-platform abstraction that has been thoroughly validated across multiple providers.

## 7. When this page alone is not enough

If you start caring about:

- File-upload gateway design
- Large-audio chunking
- Storage lifecycle
- End-to-end streaming playback

...that means you have gone beyond the Core SDK audio service itself; the question moves into application-layer interface design, which is not something the SDK layer can solve directly.

## 8. Conclusion of this page

> AI4J's current Audio capability is a formal service surface implemented exclusively by the OpenAI path. It has unified TTS, transcription, and translation into `IAudioService`, and does part of the input validation at the request-object layer, but error governance, file lifecycle, and large-file handling still primarily belong to the business integration layer.
