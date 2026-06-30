# Agent node IO capture + node-level replay - 进度

## 状态：已完成

## 进度记录

### [2026-06-24] - Phase 1 实现 + 真实 LLM 验证（PR #146 merged）

- 做了什么（包 io.github.lnyocly.ai4j.agent.replay）：
  - NodeIoRecord（MODEL/TOOL 节点 input/output + correlation）
  - IoCaptureSink + InMemoryIoCaptureSink（持真实对象供 live 重放）+ JsonlIoCaptureSink（durable JSONL + load()）
  - IoCaptureAgentListener：订阅 MODEL_REQUEST/RESPONSE、TOOL_CALL/RESULT、STEP_END 配对组装记录；复用已有事件流，零 runtime 改动；永不抛异常。
  - NodeReplayer：replayModelLive（真实再调 LLM）/ replayModelMock（确定性）/ replayToolLive。
- 关键决策：runtime 已把完整 I/O 发到事件总线（MODEL_REQUEST payload=AgentPrompt 等），故只加消费者+重放器。
- 验证：
  - 离线 NodeIoCaptureReplayTest 6 测试全绿；ai4j-agent 全模块 154 测试 0 失败。
  - **真实 LLM**：NodeIoCaptureReplayLiveTest 经 GLM coding-plan key（anthropicMessages）跑真实 turn（echo_text 工具）→ 捕获 MODEL+TOOL 节点 → **replayModelLive 用捕获 prompt 真实再调 GLM**（toolCalls=1）→ 工具节点重放成功。
  - git diff --check 干净。
- 证据：command:G:\My_Project\javai4j-sdk:6 offline + 1 live (real GLM) pass; ai4j-agent 154 tests; PR #146 MERGED dff2af1

## 残余

- Phase 1 内无。幂等/副作用（Phase 2 失败恢复）、JDBC snapshot store（Phase 3 断点续跑）、哈希链防篡改（Phase 4 审计）= 后续独立任务。
- live 测试用的 GLM key 出现在会话历史，建议轮换。

## 协调者交接

- Global sync status：pending-coordinator-pass
- 负责人：coordinator
