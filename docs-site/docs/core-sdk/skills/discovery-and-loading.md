# Discovery and Loading

这一页解释 skill 是如何被发现和读取的。

## 1. 发现入口

基础能力入口在：

- `Skills.discoverDefault(...)`

## 2. 常见 roots

- `<workspace>/.ai4j/skills`
- `~/.ai4j/skills`
- 额外挂载的 `skillDirectories`

## 3. 读取方式

模型不会一开始就拿到全部 skill 正文。

更常见的链路是：

1. 先看到 skill 清单
2. 再按需调用 `read_file`
3. 读取对应 `SKILL.md`

这样能避免上下文污染。
