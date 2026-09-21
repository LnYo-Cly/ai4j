---
title: "Extension SPI Internals"
description: "Explains the two-layer internal SPI behind plugin discovery and resource reading: the ExtensionLoader interface lets you discover plugins without ServiceLoader (the default ServiceLoaderExtensionLoader is annotated @Internal), the ExtensionResourceResolver public helper reads classpath text resources in plugin classloader -> TCCL -> resolver classloader order and isolates each plugin jar, and the DiscoveredExtension inspection projection (manifest + extension + enabled) returned by ExtensionRegistry.list() after discovery."
tags: [reference]
---

# Extension SPI Internals

This page covers two low-level wiring points in the plugin mechanism: **how plugins are discovered**, and **how classpath text resources are read from a plugin jar**. These are not things you hit day-to-day when writing plugins; you only need them when customizing discovery, troubleshooting resource reading, or building a host framework.

If you are just writing an ordinary plugin, the [Plugin Author Cookbook](/docs/extending/plugins/plugin-author-cookbook) is enough.

## Mechanism Diagram (Interactive)

The diagram below covers the full extension lifecycle: ServiceLoader discovers `Ai4jExtension`, `manifest`+`apply(ctx)` performs registration, `ExtensionRegistry` separates enable from five explicit expose gates, `ExtensionContext` exposes seven registries (tools/commands/skills/prompts/guardrails/lifecycle/interceptors), and the host surface picks up tools, commands, Skills, Prompts, and interceptors.

import useBaseUrl from '@docusaurus/useBaseUrl';

<iframe
  src={useBaseUrl('/archify/plugin-extension.html')}
  title="Plugin and extension system · interactive architecture diagram"
  style={{width: '100%', height: 940, border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: 8}}
/>

<a href={useBaseUrl('/archify/plugin-extension.html')} target="_blank" rel="noopener noreferrer">Open the full-screen diagram in a new window</a> (light/dark themes, zoom, and export included).

**Complementary view: extension discovery and enablement** — ServiceLoader discovers Ai4jExtension → manifest registration → enable → apply(context) contributes tools/prompts/skills → runtime snapshot tracks state.

<iframe
  src={useBaseUrl('/archify/plugin-discovery-enable.html')}
  title="plugin-discovery-enable"
  style={{width: '100%', height: 940, border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: 8}}
/>

<a href={useBaseUrl('/archify/plugin-discovery-enable.html')} target="_blank" rel="noopener noreferrer">Open the full-screen interactive diagram</a> (light/dark themes, zoom, export).


## 1. Plugin discovery: `ExtensionLoader`

### 1.1 Default path

When `ExtensionRegistry.discover()` is called with no arguments, it uses the `@Internal` default implementation `ServiceLoaderExtensionLoader`, which goes through the JDK's `ServiceLoader.load(Ai4jExtension.class, classLoader)`:

```java
public static ExtensionRegistry discover() {
    return discover(new ServiceLoaderExtensionLoader());
}
```

`ServiceLoaderExtensionLoader` itself is annotated `@Internal`, meaning "depend on the `ExtensionLoader` interface, do not depend on this implementation class directly." Its constructor accepts a `ClassLoader`, defaulting to the current thread's context class loader.

This SPI layer exists because ServiceLoader is not the only discovery mechanism.

### 1.2 The `ExtensionLoader` interface

`ExtensionLoader` has a single method:

```java
public interface ExtensionLoader {
    List<Ai4jExtension> load();
}
```

`ExtensionRegistry.discover(ExtensionLoader loader)` accepts any implementation:

```java
public static ExtensionRegistry discover(ExtensionLoader loader) {
    if (loader == null) {
        throw new IllegalArgumentException("extension loader must not be null");
    }
    return new ExtensionRegistry(loader.load());
}
```

Every returned `Ai4jExtension` must still have a valid manifest (`manifest()` non-null, id non-empty, and id unique), otherwise `ExtensionRegistry` fails fast during construction. In other words, the custom loader is responsible for "finding extension instances" and the registry is responsible for "validating the manifest and building the table" — the two responsibilities are kept separate.

### 1.3 When to write a custom loader

Only write a custom `ExtensionLoader` when ServiceLoader does not fit:

| Scenario | Custom loader needed? |
| --- | --- |
| Ordinary Maven / Gradle dependency plugin, `META-INF/services/...` on the classpath | No, the default is fine |
| Manually constructing a few extensions in a unit test | Use `ExtensionRegistry.of(extension...)`, no loader needed |
| Host framework wants to build extensions from a fixed allowlist or runtime scan results | Yes, write a loader that returns a fixed list |
| Want a different classloader isolation strategy for discovery | Yes (or just pass a specific classloader to `ServiceLoaderExtensionLoader`) |

A minimal fixed-list loader:

```java
public class FixedListExtensionLoader implements ExtensionLoader {
    private final List<Ai4jExtension> extensions;

    public FixedListExtensionLoader(List<Ai4jExtension> extensions) {
        this.extensions = extensions;
    }

    public List<Ai4jExtension> load() {
        return extensions;
    }
}
```

```java
ExtensionRegistry registry = ExtensionRegistry.discover(
        new FixedListExtensionLoader(Arrays.asList(new FooExtension(), new BarExtension())));
```

:::note
After a custom loader bypasses ServiceLoader, manifest validation and deduplication are still done by `ExtensionRegistry`. Do not perform enable/expose/authorization in the loader — those are the registry's responsibilities.
:::

### 1.4 Inspecting after discovery: `DiscoveredExtension`

`DiscoveredExtension` is not a stage in the discovery pipeline — the loader never produces it. It is a projection the registry builds **on demand when inspected**, packaging three things about each registered extension for external readers:

```java
public final class DiscoveredExtension {
    public ExtensionManifest getManifest();   // declarations: id, version, capabilities, etc.
    public Ai4jExtension getExtension();      // the extension instance itself
    public String getSourceClassName();       // extension.getClass().getName(), locates the source jar
    public boolean isEnabled();               // whether it passed the enable/expose gate
}
```

The entry point is `ExtensionRegistry.list()`:

```java
for (DiscoveredExtension discovered : registry.list()) {
    if (!discovered.isEnabled()) {
        System.out.println("disabled: " + discovered.getManifest().getId()
                + " @ " + discovered.getSourceClassName());
    }
}
```

`enabled` reflects the registry's enable gate (enable/expose authorization), not the manifest declaration — an extension can be registered but not enabled. This projection has two real consumers: `ExtensionValidator` walks `registry.list()` to validate each manifest and `apply(...)` contribution; the CLI's `extension inspect` also goes through it under the hood (see [Plugin Author Cookbook — Runtime inspection](/docs/extending/plugins/plugin-author-cookbook)).

If you just want to know "which extensions exist and which are enabled," use `registry.list()`. To get a single extension's manifest, use `registry.manifest(id)` — you do not need to filter this list yourself.

### 1.5 Isolated loading: `IsolatedExtensionLoader`

The default `ServiceLoaderExtensionLoader` loads every extension into a single classloader — simple, but two plugins carrying conflicting dependencies can pollute each other. `IsolatedExtensionLoader` (`@Experimental`) creates a dedicated `URLClassLoader` per extension jar, with the classloader that loaded `ai4j-extension-api` as the fixed parent:

```java
ExtensionRegistry registry = ExtensionRegistry.discover(
        IsolatedExtensionLoader.fromDirectory(Paths.get("plugins")));
```

- SPI contract types (`Ai4jExtension`, `ExtensionContext`, manifests, specs) are shared through the parent, so isolation never produces `ClassCastException` on the contract surface.
- Each plugin jar (or equivalent directory) gets its own `URLClassLoader`; its private dependencies stay invisible to other plugins and never leak onto the host classpath.

:::warning
Classloader isolation solves **dependency conflicts and lifecycle management** — it is not a security sandbox: a malicious plugin still runs in the host process. Untrusted code needs a process boundary, not a classloader.
:::

### 1.6 Tool namespaces and the unload lifecycle

`registerTool` rewrites the tool name to `plugin__<extensionId>__<tool>` — two independently authored plugins can both declare `echo`, and the model sees `plugin__a__echo` plus `plugin__b__echo` with no collision. Guardrails and interceptors observe this namespaced id via `request.getTarget()` too.

`exposeTool(...)` and the CLI `--expose-tool` accept both forms:

| Form | Behavior |
| --- | --- |
| `plugin__pack-a__echo` (full namespaced id) | Exact match |
| `echo` (bare name) | Resolves to the unique registered tool ending in `__echo`; multiple matches raise `ambiguous tool id` |

For lifecycle, `Ai4jExtension` provides a default no-op `onStop()` hook — existing extensions do not need to change:

- When `apply(...)` throws after `enable(...)`: everything that extension registered is discarded with the temporary state (transactional rollback), `onStop()` is invoked, and — if loaded via `IsolatedExtensionLoader` — its classloader is released.
- `registry.disable(id)`: removes the extension from the enabled set, invokes `onStop()`, and releases its classloader; the next `snapshot()` rebuilds runtime state without its contributions.
- `registry.close()`: disables every enabled extension in reverse order, then closes the isolated loader.

Note that `disable` does not prune leftover `exposeTool(...)` entries — the next `snapshot()` fails with `tool not registered`. Manage enable/expose and disable in the same configuration surface to keep them consistent.

## 2. Plugin resource reading: `ExtensionResourceResolver`

### 2.1 What problem it solves

Skills / Prompts declared by a plugin are classpath text resources inside the jar (`SKILL.md`, `*.md`), registered via `resourcePath(...)`. Resolving these paths to actual text at runtime runs into two problems:

1. **Same-name resource bleed**: with multiple jars on the classpath, if two jars both contain `skills/weather/SKILL.md`, a plain `ClassLoader.getResourceAsStream(...)` may return the wrong one.
2. **Path normalization**: paths written by authors may carry a `classpath:` prefix or a leading `/`, and need to be normalized.

`ExtensionResourceResolver` is a public helper exposed by `ai4j-extension-api` dedicated to these two concerns. It is a set of static methods and is not instantiable.

### 2.2 Resolution order and classloader isolation

The core read methods are the overloaded `readText` / `readTextStrict` and `exists` / `existsStrict`. They differ in whether classloader fallback is allowed:

| Method | Resolution order | Use case |
| --- | --- | --- |
| `readText(path, cl)` / `exists(path, cl)` | plugin cl -> TCCL -> resolver's own cl | Default lenient read, best effort to find the resource |
| `readTextStrict(path, cl)` / `existsStrict(path, cl)` | search **only** on the plugin cl | Strict isolation: a plugin resource must live in its own jar |

`cl` is the "preferred classloader," usually taken from the plugin implementation class's classloader — which is exactly what `ExtensionRegistry.getExtensionClassLoader(extensionId)` returns:

```java
public ClassLoader getExtensionClassLoader(String extensionId) {
    Ai4jExtension extension = discovered.get(normalized);
    ClassLoader classLoader = extension == null ? null : extension.getClass().getClassLoader();
    return classLoader == null ? Thread.currentThread().getContextClassLoader() : classLoader;
}
```

The `strict` variants constrain resolution to the plugin's own classloader: **a missing plugin resource cannot be masked by a same-named resource in another jar**. AI4J uses the strict read on the strict resource authorization path, so a plugin must ship the resources it declares — it cannot "borrow" a same-named file from the host or another plugin.

### 2.3 Path normalization

`normalizeResourcePath(...)` does three things:

- Strips the optional `classpath:` prefix
- Strips the leading `/` (`/skills/x.md` becomes `skills/x.md`)
- Rejects paths containing `..`, preventing a resource path from being disguised as an arbitrary file read

```java
// All of these inputs normalize to skills/weather/SKILL.md
ExtensionResourceResolver.normalizeResourcePath("classpath:skills/weather/SKILL.md")
ExtensionResourceResolver.normalizeResourcePath("/skills/weather/SKILL.md")
ExtensionResourceResolver.normalizeResourcePath("skills/weather/SKILL.md")

// Containing ".." throws IllegalArgumentException
ExtensionResourceResolver.normalizeResourcePath("skills/../etc/secret.md")
```

### 2.4 Direct usage

Under normal circumstances you do not need to call `ExtensionResourceResolver` directly — AI4J already uses it when reading Skill / Prompt resources. But when a plugin author writes tests, or a host framework needs to read classpath text by the same rules, it can be reused directly to keep the read behavior consistent with the SDK:

```java
ClassLoader pluginCl = registry.getExtensionClassLoader("weather-pack");
String markdown = ExtensionResourceResolver.readTextStrict(
        "skills/weather/SKILL.md", pluginCl);
```

When a read fails, `readText` / `readTextStrict` throw `ExtensionException("extension resource not found: ...")`; `exists` / `existsStrict` return `false`. Reads are uniformly decoded as UTF-8.

## 3. Cheat sheet

| What you want to do | What to use |
| --- | --- |
| Discover plugins from the classpath by default | `ExtensionRegistry.discover()` |
| Discover plugins with a specific classloader | Pass `new ServiceLoaderExtensionLoader(classLoader)` into `discover(loader)` |
| Non-ServiceLoader discovery | Implement `ExtensionLoader` and pass it to `discover(loader)` |
| Manually construct a few extensions (testing) | `ExtensionRegistry.of(extension...)` |
| Get a plugin's classloader | `registry.getExtensionClassLoader(extensionId)` |
| Strictly read a text resource inside a plugin jar | `ExtensionResourceResolver.readTextStrict(path, pluginCl)` |
| Check whether a plugin resource exists | `ExtensionResourceResolver.existsStrict(path, pluginCl)` |
| Normalize a resource path | `ExtensionResourceResolver.normalizeResourcePath(path)` |

## 4. Further reading

1. [Plugin Packages](/docs/extending/plugins/plugin-packages)
2. [Plugin Author Cookbook](/docs/extending/plugins/plugin-author-cookbook)
3. [Lifecycle Extensions](/docs/extending/plugins/lifecycle-extensions)
4. [Interceptor Extensions](/docs/extending/plugins/interceptor-extensions)
