# 2026-03-27 Coding Agent 收口计划

- 状态：Draft
- 优先级：P0
- 目标模块：`ai4j-coding`、`ai4j-tui`、`ai4j-cli`
- 关联文档：
  - `docs-site/docs/getting-started/coding-agent-cli-quickstart.md`
  - `docs-site/docs/agent/coding-agent-cli.md`
  - `docs-site/docs/agent/coding-agent-command-reference.md`
  - `docs/plans/2026-03-27-jline-status-observability-design.md`
  - `docs/plans/2026-03-26-esc-stream-cancel-design.md`

## 1. 目标

把当前 coding agent 从“已基本可用”收口到“功能、文档、构建状态一致，可对外稳定说明”的状态。

本轮不追求再扩大量新功能，重点是把已有能力收平：

- 构建与测试可通过
- CLI/TUI 行为与文档一致
- 关键交互语义清晰可解释
- 模块边界稳定
- 可以作为后续 FlowGram / site 对接之外的一条独立能力线继续演进

## 2. 当前基线

当前仓库已经具备这些基础：

- 顶层模块已经拆出 `ai4j-coding`、`ai4j-tui`、`ai4j-cli`
- `README.md` 已经把 coding agent 作为正式能力入口写入
- docs-site 已有快速开始、CLI/TUI、命令参考、多 provider profile 等页面
- CLI 已支持：
  - provider profile
  - workspace model override
  - `/skills` 与 `skillDirectories`
  - session/process 管理
  - `/stream`
  - CLI / TUI 双入口

## 3. 已知问题

### 3.1 构建未完全收口

当前执行：

```bash
mvn -pl ai4j-cli -am -DskipTests package
```

会在 `ai4j-tui` 的测试编译阶段失败。

当前明确失败点：

- `ai4j-tui/src/test/java/io/github/lnyocly/ai4j/tui/StreamsTerminalIOTest.java:45`

测试还在调用旧签名：

- `StreamsTerminalIO.resolveTerminalCharset(String[], String[], String[], boolean)`

但当前生产代码暴露的是：

- `ai4j-tui/src/main/java/io/github/lnyocly/ai4j/tui/StreamsTerminalIO.java:27`
- `public static Charset resolveTerminalCharset()`

### 3.2 `/stream` 文档与代码语义不一致

当前文档仍写成：

- `/stream` 只是 transcript 渲染开关
- 不是 provider 请求语义切换

但当前实现已经绑定到请求级 stream 选项，并会重建 session runtime。

相关代码点：

- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/runtime/CodingCliSessionRunner.java:4473`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/runtime/CodingCliSessionRunner.java:4508`

这意味着 README、quickstart、CLI/TUI 文档、命令参考都需要统一。

### 3.3 中断 / 状态可观测文案仍需收口

用户已经多轮验证过：

- `Esc` 中断是否真的生效
- 中断后输入框恢复是否明确
- 模型 working / reconnecting / waiting 的状态是否可解释

设计文档已有，但还需要落实到：

- 实际交互表现
- 文档说明
- 验收标准

### 3.4 对外“已完成”的口径还不够稳

当前可以说“可用”，但还不能稳妥说“全部完善”，因为至少还有：

- 构建失败项
- 文档不一致项
- CLI/TUI 行为边界说明不完整

## 4. 本轮收口范围

本轮只做以下四类工作：

1. 修复 coding 栈构建/测试收口问题
2. 统一 `/stream`、ESC/中断、skills、session/process 的文档口径
3. 跑一轮 `ai4j-coding + ai4j-tui + ai4j-cli` 的验证
4. 输出“当前已完成 / 当前边界 / 下一步能力”的稳定说明

本轮不做：

- 再新增大块交互能力
- 大规模包重构
- 新协议或新平台适配
- 影响当前行为的高风险重写

## 5. 阶段计划

### Phase 1：构建与测试收口

目标：

- 先把 coding 栈重新拉回“可打包、可测试”的最小稳定状态

任务：

- 修复 `StreamsTerminalIOTest` 与当前 `StreamsTerminalIO` API 签名不一致的问题
- 复查 `ai4j-tui` 新拆分后的测试是否还有同类旧引用
- 跑通最小构建链：
  - `ai4j-coding`
  - `ai4j-tui`
  - `ai4j-cli`

验收：

- `mvn -pl ai4j-cli -am -DskipTests package` 通过
- 或至少明确剩余失败项不属于 coding agent 变更引入的问题

### Phase 2：文档语义统一

目标：

- 让 README、quickstart、CLI/TUI 文档、命令参考与当前真实行为一致

重点收口点：

- `/stream`
  - 当前是否绑定模型请求 `stream`
  - 是否会重建 runtime
  - stream off 时用户能看到什么
- `/skills`
  - skill 发现规则
  - `skillDirectories` 的作用
  - `/skills <name>` 只展示元信息，不打印 `SKILL.md`
- ESC / 中断
  - 中断什么
  - 保留什么
  - 为什么可能看起来“停了但没有立即恢复输入框”
- session / process
  - 当前哪些能力是正式支持
  - 哪些还是边界能力

涉及文档：

- `README.md`
- `docs-site/docs/getting-started/coding-agent-cli-quickstart.md`
- `docs-site/docs/agent/coding-agent-cli.md`
- `docs-site/docs/agent/coding-agent-command-reference.md`

验收：

- 同一能力在 README 和 docs-site 中没有自相矛盾描述
- `/stream` 说明和当前代码行为一致
- 用户能够仅靠文档理解当前边界

### Phase 3：交互与状态说明收口

目标：

- 把“状态是否可信”这件事单独收平

任务：

- 检查 `Thinking / Working / Responding / Waiting / Reconnecting` 等状态文案是否一致
- 检查 ESC 中断后：
  - transcript 是否保留
  - session memory 是否保留
  - 半截输出是否是否进入已提交上下文
- 补充中断后的用户可见提示
- 补充 spinner / reconnecting 的解释文档

验收：

- 用户能区分：
  - 还在工作中
  - 已中断
  - 正在重连
  - 已完成但壳层未完全刷新

### Phase 4：对外完成度收口

目标：

- 给 coding agent 一套准确的“当前完成度声明”

输出：

- 已完成能力清单
- 当前边界清单
- 推荐使用路径
- 后续增强项清单

验收：

- 可以明确回答“coding agent 现在是否已经完善”
- 口径统一为：
  - 哪些是已稳定
  - 哪些是 P1/P2
  - 哪些尚未做

## 6. 详细任务清单

### A. 代码与测试

- 修复 `ai4j-tui` 中 charset 解析测试
- 复跑 `ai4j-tui` 单测
- 复跑 `ai4j-cli` 关键测试
- 复跑 packaging

### B. 文档

- README 中 coding agent 段落校正
- quickstart 更新
- CLI/TUI 说明页更新
- command reference 更新
- 如有必要，补一页“中断与状态可观测”专题页

### C. 行为核对

- `/stream on|off`
- `/skills`
- `/skills <name>`
- `Esc`
- `session resume/fork`
- `process follow/logs/stop`

### D. 对外说明

- 形成一份“当前能力与边界”摘要
- 确定之后回答用户时的标准口径

## 7. 建议修改文件范围

### 预期修改

- `ai4j-tui/src/test/java/io/github/lnyocly/ai4j/tui/StreamsTerminalIOTest.java`
- `README.md`
- `docs-site/docs/getting-started/coding-agent-cli-quickstart.md`
- `docs-site/docs/agent/coding-agent-cli.md`
- `docs-site/docs/agent/coding-agent-command-reference.md`

### 可能涉及

- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/runtime/CodingCliSessionRunner.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodeCommand.java`

只有在确认当前实现与设计不符时，才动运行时代码；否则优先文档收口。

## 8. 验证命令

### 最小构建验证

```bash
mvn -pl ai4j-cli -am -DskipTests package
```

### 定向测试验证

```bash
mvn --% -pl ai4j-cli -am -Dtest=CodeCommandTest,SlashCommandControllerTest,DefaultCodingCliAgentFactoryTest,CliProviderConfigManagerTest,CliMcpConfigManagerTest -Dsurefire.failIfNoSpecifiedTests=false -DskipTests=false test
```

### TUI 定向验证

```bash
mvn --% -pl ai4j-tui -am -Dtest=StreamsTerminalIOTest,TuiConfigManagerTest,TuiSessionViewTest -Dsurefire.failIfNoSpecifiedTests=false -DskipTests=false test
```

## 9. 验收标准

本轮可以宣告“coding agent 已收口到可稳定说明”必须同时满足：

- `ai4j-coding`、`ai4j-tui`、`ai4j-cli` 构建通过
- 至少一轮 coding 栈关键测试通过
- `/stream` 文档与实现一致
- `/skills` 文档与实现一致
- ESC / 中断 / 状态说明文档可回答用户真实疑问
- README 与 docs-site 的能力描述没有明显冲突

## 10. 完成后的对外口径

完成本计划后，对外应统一为：

- coding agent 主干能力已完成并可用
- CLI/TUI、skills、provider profile、session/process 为正式能力
- `/stream`、ESC/中断、状态可观测语义已文档化
- 后续仍会继续增强，但不再属于“基础能力未完成”

## 11. 风险与处理

### 风险 1：文档与实现再次漂移

处理：

- 行为核对必须以当前代码为准
- 文档修改后立即做一轮 spot check

### 风险 2：测试失败暴露更多拆分遗留问题

处理：

- 先修 compile error
- 再按模块逐步扩大验证范围
- 避免一次性全库大扫荡

### 风险 3：在脏工作树中误碰无关文件

处理：

- 只改 coding agent 相关模块与文档
- 不回滚现有未提交改动

## 12. 建议执行顺序

1. 先修 `ai4j-tui` 测试编译错误
2. 立即复跑 `mvn -pl ai4j-cli -am -DskipTests package`
3. 再收 README 与 docs-site
4. 最后输出完成度说明与下一步项
