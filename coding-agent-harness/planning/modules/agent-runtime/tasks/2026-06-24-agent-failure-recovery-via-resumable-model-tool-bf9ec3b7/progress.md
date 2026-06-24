# Agent failure recovery via resumable model/tool decorators - 进度

## 状态：进行中

## 进度记录

### [2026-06-24] - Phase 2 实现 + 真实 LLM 验证（PR #147 merged 0e4168a）

- 做了什么（包 io.github.lnyocly.ai4j.agent.replay，建在 Phase 1 之上）：
  - ResumeCache：内容寻址缓存（prompt→AgentModelResult、name|args→tool output）+ 命中/未命中计数 + removeLastModelEntry（模拟崩溃）。
  - ResumableModelClient：命中返回缓存（零 LLM 调用），未命中 delegate+记录。同一实例既捕获（run1）又恢复（run2+）。
  - ResumableToolExecutor：命中返回缓存**不重复副作用**（不二次写文件/调 API/扣费）。这是安全失败恢复的关键。
- 设计依据：内容寻址正确——run2 用 run1 结果短路 step N ⇒ runtime 构造 step N+1 prompt 一致 ⇒ 同 key 命中，归纳整条轨迹可恢复。Phase 2 需完整 AgentModelResult（含 toolCalls），事件流只有 rawResponse，故在 ModelClient 层捕获（与 Phase 1 事件级捕获互补）。
- 验证：
  - 离线 ResumeCacheTest 4 测试（捕获/恢复/部分恢复/工具副作用跳过）；ai4j-agent 全模块 158 测试 0 失败。
  - **真实 LLM**：AgentFailureRecoveryLiveTest 经 GLM——run1 捕获（2 model + 1 tool）；run2 同输入全恢复 = **零真实 LLM 调用 + 零工具副作用**；run3（去掉最后一步=崩溃前）只重跑缺失步（1 call, 0 tool）。
  - git diff --check 干净。
- 证据：command:G:\My_Project\javai4j-sdk:4 offline + 1 live (real GLM); ai4j-agent 158 tests; PR #147 MERGED 0e4168a

## 残余

- 跨进程持久化 ResumeCache（真重启后从盘加载）是小后续；恢复语义已在此证明。
- live 测试用的 GLM key 出现在会话历史，建议轮换。

## 协调者交接

- Global sync status：pending-coordinator-pass
- 负责人：coordinator
