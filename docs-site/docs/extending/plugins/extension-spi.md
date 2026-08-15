---
title: Extension SPI Internals
description: 讲清插件发现与资源读取的两层内部 SPI：ExtensionLoader 接口允许用非 ServiceLoader 方式发现插件（默认 ServiceLoaderExtensionLoader 标注 Internal），ExtensionResourceResolver 公共助手按插件 classloader -> TCCL -> resolver classloader 顺序读取 classpath 文本资源并隔离各插件 jar，以及发现之后 ExtensionRegistry.list() 返回的检视投影 DiscoveredExtension（manifest + extension + enabled）。
tags: [reference]
---

# Extension SPI Internals

这一页覆盖插件机制里两个底层接线点：**怎么发现插件**，以及**怎么从插件 jar 里读 classpath 文本资源**。它们不是日常写插件会碰到的东西，只有在自定义发现方式、排查资源读取问题或构建宿主框架时才需要。

如果你只是写一个普通插件，看 [Plugin Author Cookbook](/docs/extending/plugins/plugin-author-cookbook) 就够了。

## 1. 插件发现：`ExtensionLoader`

### 1.1 默认路径

`ExtensionRegistry.discover()` 不带参数时，使用 `@Internal` 的默认实现 `ServiceLoaderExtensionLoader`，它走 JDK 的 `ServiceLoader.load(Ai4jExtension.class, classLoader)`：

```java
public static ExtensionRegistry discover() {
    return discover(new ServiceLoaderExtensionLoader());
}
```

`ServiceLoaderExtensionLoader` 本身标注 `@Internal`，意思是“依赖 `ExtensionLoader` 接口，不要直接依赖这个实现类”。它的构造函数接受一个 `ClassLoader`，默认用当前线程的 context class loader。

这层 SPI 的存在是因为 ServiceLoader 不是唯一的发现方式。

### 1.2 `ExtensionLoader` 接口

`ExtensionLoader` 只有一个方法：

```java
public interface ExtensionLoader {
    List<Ai4jExtension> load();
}
```

`ExtensionRegistry.discover(ExtensionLoader loader)` 接受任意实现：

```java
public static ExtensionRegistry discover(ExtensionLoader loader) {
    if (loader == null) {
        throw new IllegalArgumentException("extension loader must not be null");
    }
    return new ExtensionRegistry(loader.load());
}
```

返回的每个 `Ai4jExtension` 仍然必须有合法 manifest（`manifest()` 非 null，id 非空，且 id 不重复），否则 `ExtensionRegistry` 构造时会 fail-fast。也就是说，自定义 loader 负责“找到 extension 实例”，registry 负责“校验 manifest 并建表”——两层职责分开。

### 1.3 什么时候写自定义 loader

只有在 ServiceLoader 不适合时才写自定义 `ExtensionLoader`：

| 场景 | 是否需要自定义 loader |
| --- | --- |
| 普通 Maven / Gradle 依赖插件，classpath 上有 `META-INF/services/...` | 否，默认即可 |
| 单测里手动构造几个 extension | 用 `ExtensionRegistry.of(extension...)`，不必写 loader |
| 宿主框架想从固定白名单或运行时扫描结果构造 extension | 是，写一个返回固定列表的 loader |
| 想换不同的 classloader 隔离策略发现插件 | 是（或直接给 `ServiceLoaderExtensionLoader` 传指定 classloader） |

一个固定列表 loader 的最小实现：

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
自定义 loader 跳过 ServiceLoader 后，仍然由 `ExtensionRegistry` 做 manifest 校验和去重。不要在 loader 里做启用/暴露/授权——那些是 registry 的职责。
:::

### 1.4 发现之后怎么检视：`DiscoveredExtension`

`DiscoveredExtension` 不是发现管线里的一个环节——loader 从不产出它。它是 registry 在**被检视时**按需构造的投影，把每个已登记扩展的三样东西打包给外部读：

```java
public final class DiscoveredExtension {
    public ExtensionManifest getManifest();   // id、version、capabilities 等声明
    public Ai4jExtension getExtension();      // 扩展实例本身
    public String getSourceClassName();       // extension.getClass().getName()，定位来源 jar
    public boolean isEnabled();               // 是否通过了启用/暴露门禁
}
```

入口是 `ExtensionRegistry.list()`：

```java
for (DiscoveredExtension discovered : registry.list()) {
    if (!discovered.isEnabled()) {
        System.out.println("disabled: " + discovered.getManifest().getId()
                + " @ " + discovered.getSourceClassName());
    }
}
```

`enabled` 反映的是 registry 的启用门禁（enable/expose 授权），不是 manifest 声明——一个扩展可以已登记但未启用。这个投影有两个真实消费者：`ExtensionValidator` 遍历 `registry.list()` 逐个校验 manifest 与 `apply(...)` 贡献；CLI 的 `extension inspect` 底层也走它（见 [Plugin Author Cookbook — Runtime inspection](/docs/extending/plugins/plugin-author-cookbook)）。

如果你只是想知道"有哪些扩展、哪些启用了"，用 `registry.list()`；要拿单个扩展的 manifest，用 `registry.manifest(id)`，不必自己过滤这个列表。

## 2. 插件资源读取：`ExtensionResourceResolver`

### 2.1 它解决什么问题

插件声明的 Skill / Prompt 是 jar 内的 classpath 文本资源（`SKILL.md`、`*.md`），通过 `resourcePath(...)` 注册。运行时要把这些路径解析成实际文本，就会遇到两个问题：

1. **同名资源串读**：classpath 上有多个 jar，如果两个 jar 都有 `skills/weather/SKILL.md`，普通 `ClassLoader.getResourceAsStream(...)` 可能返回错误的那个。
2. **路径规范化**：作者写的路径可能带 `classpath:` 前缀或前导 `/`，需要归一。

`ExtensionResourceResolver` 是 `ai4j-extension-api` 暴露的公共助手，专门处理这两件事。它是一组静态方法，不可实例化。

### 2.2 解析顺序与 classloader 隔离

读取的核心方法是重载的 `readText` / `readTextStrict` 和 `exists` / `existsStrict`。它们的区别在是否允许 classloader 回退：

| 方法 | 解析顺序 | 用途 |
| --- | --- | --- |
| `readText(path, cl)` / `exists(path, cl)` | 插件 cl → TCCL → resolver 自身 cl | 默认兼容读法，尽力找到资源 |
| `readTextStrict(path, cl)` / `existsStrict(path, cl)` | **只**在插件 cl 上找 | 严格隔离：插件资源必须在它自己的 jar 里 |

`cl` 是“优先 classloader”，通常取自插件实现类的 classloader——`ExtensionRegistry.getExtensionClassLoader(extensionId)` 返回的就是这个：

```java
public ClassLoader getExtensionClassLoader(String extensionId) {
    Ai4jExtension extension = discovered.get(normalized);
    ClassLoader classLoader = extension == null ? null : extension.getClass().getClassLoader();
    return classLoader == null ? Thread.currentThread().getContextClassLoader() : classLoader;
}
```

`strict` 变体把解析约束在插件自己的 classloader 上：**插件资源缺失不会被别的 jar 里同名资源掩盖**。AI4J 在严格资源授权路径下用 strict 读法，所以插件必须自带它声明的资源，不能“借用”宿主或其它插件的同名文件。

### 2.3 路径规范化

`normalizeResourcePath(...)` 做三件事：

- 去掉可选的 `classpath:` 前缀
- 去掉前导 `/`（`/skills/x.md` → `skills/x.md`）
- 拒绝包含 `..` 的路径，防止把资源路径伪装成任意文件读取

```java
// 这些输入都会归一成 skills/weather/SKILL.md
ExtensionResourceResolver.normalizeResourcePath("classpath:skills/weather/SKILL.md")
ExtensionResourceResolver.normalizeResourcePath("/skills/weather/SKILL.md")
ExtensionResourceResolver.normalizeResourcePath("skills/weather/SKILL.md")

// 含 ".." 会抛 IllegalArgumentException
ExtensionResourceResolver.normalizeResourcePath("skills/../etc/secret.md")
```

### 2.4 直接使用

正常情况下你不需要直接调用 `ExtensionResourceResolver`——AI4J 在读取 Skill / Prompt 资源时已经用它。但插件作者写测试、或宿主框架要按同样规则读 classpath 文本时，可以直接复用，保证读法和 SDK 一致：

```java
ClassLoader pluginCl = registry.getExtensionClassLoader("weather-pack");
String markdown = ExtensionResourceResolver.readTextStrict(
        "skills/weather/SKILL.md", pluginCl);
```

读不到时 `readText` / `readTextStrict` 抛 `ExtensionException("extension resource not found: ...")`；`exists` / `existsStrict` 返回 `false`。读取统一按 UTF-8 解码。

## 3. 速查表

| 想做的事 | 用什么 |
| --- | --- |
| 默认从 classpath 发现插件 | `ExtensionRegistry.discover()` |
| 用指定 classloader 发现插件 | `new ServiceLoaderExtensionLoader(classLoader)` 传入 `discover(loader)` |
| 非 ServiceLoader 发现 | 实现 `ExtensionLoader`，传入 `discover(loader)` |
| 手动构造少量 extension（测试） | `ExtensionRegistry.of(extension...)` |
| 拿到插件的 classloader | `registry.getExtensionClassLoader(extensionId)` |
| 严格读插件 jar 内文本资源 | `ExtensionResourceResolver.readTextStrict(path, pluginCl)` |
| 检查插件资源是否存在 | `ExtensionResourceResolver.existsStrict(path, pluginCl)` |
| 规范化资源路径 | `ExtensionResourceResolver.normalizeResourcePath(path)` |

## 4. 下一步阅读

1. [Plugin Packages](/docs/extending/plugins/plugin-packages)
2. [Plugin Author Cookbook](/docs/extending/plugins/plugin-author-cookbook)
3. [Lifecycle Extensions](/docs/extending/plugins/lifecycle-extensions)
