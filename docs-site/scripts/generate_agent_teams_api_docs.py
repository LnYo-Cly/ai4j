from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import List

REPO_ROOT = Path(__file__).resolve().parents[2]
SOURCE_ROOT = REPO_ROOT / "ai4j-agent" / "src" / "main" / "java" / "io" / "github" / "lnyocly" / "ai4j" / "agent" / "team"
OUTPUT_FILE = REPO_ROOT / "docs-site" / "docs" / "agent" / "agent-teams-api-reference.md"

CLASS_RE = re.compile(r"^\s*(public|protected|private)?\s*(?:static\s+)?(?:abstract\s+)?(class|interface|enum)\s+([A-Za-z_][A-Za-z0-9_]*)")
FIELD_RE = re.compile(
    r"^\s*(public|protected|private)\s+"
    r"((?:(?:static|final|volatile|transient)\s+)*)"
    r"([A-Za-z0-9_<>,\[\]. ?]+?)\s+"
    r"([A-Za-z_][A-Za-z0-9_]*)\s*(?:=[^;]*)?;\s*$"
)
METHOD_RE = re.compile(
    r"^\s*(public|protected|private)\s+"
    r"((?:(?:static|final|synchronized|native|abstract|default|strictfp)\s+)*)"
    r"(?:<[^>]+>\s*)?"
    r"([A-Za-z0-9_<>,\[\]. ?]+?)\s+"
    r"([A-Za-z_][A-Za-z0-9_]*)\s*\(([^)]*)\)\s*(?:throws [^{;]+)?[;{]\s*$"
)
CTOR_RE = re.compile(
    r"^\s*(public|protected|private)\s+"
    r"((?:(?:static|final|synchronized)\s+)*)"
    r"([A-Za-z_][A-Za-z0-9_]*)\s*\(([^)]*)\)\s*(?:throws [^{;]+)?\{\s*$"
)

CONTROL_PREFIXES = (
    "if ",
    "for ",
    "while ",
    "switch ",
    "catch ",
    "return ",
    "throw ",
    "else ",
    "do ",
    "try ",
    "synchronized ",
)

CLASS_DESCRIPTIONS = {
    "AgentTeam": "Agent Teams 的总调度器。负责规划、任务派发、并发执行、消息协作、最终汇总。",
    "AgentTeamBuilder": "构建 AgentTeam 的入口，组装 lead/planner/synthesizer/member/options。",
    "AgentTeamControl": "团队运行期控制接口，统一成员、任务、消息操作能力。",
    "AgentTeamHook": "生命周期钩子接口，支持在规划/任务/汇总阶段埋点与审计。",
    "AgentTeamMember": "成员定义对象，包含成员身份与绑定 Agent 实例。",
    "AgentTeamMemberResult": "单个任务执行结果对象，记录产出、耗时、错误和状态。",
    "AgentTeamMessage": "团队消息模型，承载 from/to/type/taskId/content。",
    "AgentTeamMessageBus": "消息总线抽象，定义 publish/snapshot/historyFor/clear。",
    "AgentTeamOptions": "团队运行配置对象，控制并发、容错、消息注入、超时回收等行为。",
    "AgentTeamPlan": "Planner 输出后的计划模型，包含任务列表。",
    "AgentTeamPlanApproval": "计划审批回调，允许在派发前人为/策略拦截。",
    "AgentTeamPlanner": "规划器接口，输入目标和成员，输出任务计划。",
    "AgentTeamPlanParser": "将模型输出文本解析为 AgentTeamPlan，含 JSON 提取和兜底策略。",
    "AgentTeamResult": "一次团队运行的完整结果快照，含计划、成员结果、任务状态、消息、轮次。",
    "AgentTeamSynthesizer": "汇总器接口，将成员结果整合为最终输出。",
    "AgentTeamTask": "任务定义模型，包含 id/memberId/task/context/dependsOn。",
    "AgentTeamTaskBoard": "任务状态机与依赖调度核心，实现 READY/IN_PROGRESS/COMPLETED 等流转。",
    "AgentTeamTaskState": "任务运行态对象，记录 claim、heartbeat、输出、错误、耗时。",
    "AgentTeamTaskStatus": "任务状态枚举。",
    "InMemoryAgentTeamMessageBus": "内存消息总线实现，适合单进程场景。",
    "LlmAgentTeamPlanner": "基于 Agent 的默认规划器实现，模型输出计划，失败时可回退简单计划。",
    "LlmAgentTeamSynthesizer": "基于 Agent 的默认汇总器实现，汇总成员结果为最终答复。",
    "AgentTeamToolExecutor": "team_* 工具执行器，将成员工具调用路由到 AgentTeamControl。",
    "AgentTeamToolRegistry": "team_* 工具注册表，定义并暴露团队内置协作工具。",
    "RuntimeMember": "AgentTeam 内部成员运行态对象，包装成员定义并缓存执行引用。",
    "PreparedDispatch": "AgentTeam 内部派发单元，绑定 task/member 用于执行轮次。",
    "DispatchOutcome": "AgentTeam 内部派发汇总对象，记录成员结果和轮次数。",
}


@dataclass
class FieldInfo:
    visibility: str
    modifiers: str
    type_name: str
    name: str
    line: int


@dataclass
class MethodInfo:
    visibility: str
    modifiers: str
    return_type: str
    name: str
    params: str
    line: int
    is_constructor: bool = False


@dataclass
class ClassInfo:
    kind: str
    name: str
    full_name: str
    file_path: str
    line: int
    fields: List[FieldInfo] = field(default_factory=list)
    methods: List[MethodInfo] = field(default_factory=list)


@dataclass
class StackEntry:
    class_info: ClassInfo
    active_depth: int


def clean_modifiers(raw: str) -> str:
    value = " ".join(raw.strip().split())
    return value if value else "-"


def infer_field_description(name: str, type_name: str) -> str:
    lower = name.lower()
    if name.endswith("Id") or lower.endswith("id"):
        return "唯一标识/关联标识字段，用于实体定位或引用。"
    if "timeout" in lower:
        return "超时配置字段，用于控制等待和回收策略。"
    if "concurrency" in lower or lower.startswith("max"):
        return "并发或阈值配置字段，影响调度上限。"
    if lower.startswith("enable") or lower.startswith("allow") or lower.startswith("require"):
        return "布尔开关字段，控制功能启用或治理策略。"
    if "message" in lower:
        return "消息相关字段，承载协作通信数据。"
    if "task" in lower:
        return "任务相关字段，保存任务定义或运行态信息。"
    if "member" in lower:
        return "成员相关字段，描述团队角色或成员映射。"
    if "plan" in lower:
        return "规划相关字段，保存 planner 输入/输出。"
    if "result" in lower or "output" in lower:
        return "结果字段，存储执行输出或汇总产物。"
    if lower.startswith("options") or lower.endswith("options"):
        return "运行配置对象，影响行为和策略。"
    if lower.endswith("agent"):
        return "Agent 执行实例引用。"
    if "list" in type_name.lower() or "map" in type_name.lower():
        return "集合字段，用于维护批量数据或索引映射。"
    return "运行期状态或配置字段，参与该类的核心行为。"


def infer_method_description(name: str, is_constructor: bool, return_type: str) -> str:
    if is_constructor:
        return "构造函数，初始化该类型的必要依赖与默认状态。"

    lower = name.lower()
    if lower in {"build", "builder"}:
        return "构建入口方法，用于创建并返回目标对象。"
    if lower.startswith("run") or lower.startswith("execute"):
        return "执行入口方法，驱动主流程并返回执行结果。"
    if lower.startswith("plan"):
        return "规划方法，根据目标/成员生成任务计划。"
    if lower.startswith("synthesize"):
        return "汇总方法，将多成员结果合并为最终输出。"
    if lower.startswith("dispatch") or lower.startswith("prepared"):
        return "任务派发方法，负责轮次调度与执行分配。"
    if lower.startswith("claim") or lower.startswith("release") or lower.startswith("reassign"):
        return "任务认领/释放/重分配方法，维护任务所有权。"
    if lower.startswith("heartbeat") or lower.startswith("recover"):
        return "运行保活与恢复方法，用于检测超时并回收任务。"
    if lower.startswith("mark"):
        return "状态写入方法，推进任务状态机到下一个阶段。"
    if lower.startswith("list") or lower.startswith("snapshot") or lower.startswith("history"):
        return "查询方法，读取当前快照或历史记录。"
    if lower.startswith("publish") or lower.startswith("send") or lower.startswith("broadcast"):
        return "消息发布方法，向单成员或全体广播协作信息。"
    if lower.startswith("parse"):
        return "解析方法，将文本/参数转换为结构化对象。"
    if lower.startswith("normalize") or lower.startswith("resolve") or lower.startswith("validate"):
        return "规范化与校验方法，保证输入可用和行为一致。"
    if lower.startswith("supports"):
        return "能力探测方法，判断是否支持某个功能或工具。"
    if lower.startswith("copy") or lower.startswith("to"):
        return "转换方法，在内部对象与公开对象之间映射。"
    if return_type == "boolean":
        return "布尔判定方法，返回条件是否满足。"
    return "内部辅助方法，服务于该类的核心执行逻辑。"


def parse_java_file(path: Path) -> List[ClassInfo]:
    rel_path = path.relative_to(REPO_ROOT).as_posix()
    classes: List[ClassInfo] = []
    stack: List[StackEntry] = []
    depth = 0

    lines = path.read_text(encoding="utf-8").splitlines()
    for idx, raw_line in enumerate(lines, 1):
        line = raw_line.strip()

        while stack and depth < stack[-1].active_depth:
            stack.pop()

        class_match = CLASS_RE.match(line)
        if class_match and "(" not in line:
            kind = class_match.group(2)
            name = class_match.group(3)
            parent = stack[-1].class_info.full_name if stack else ""
            full_name = f"{parent}.{name}" if parent else name
            info = ClassInfo(kind=kind, name=name, full_name=full_name, file_path=rel_path, line=idx)
            classes.append(info)
            if "{" in line:
                stack.append(StackEntry(class_info=info, active_depth=depth + line.count("{") - line.count("}")))

        if stack:
            current = stack[-1].class_info

            field_match = FIELD_RE.match(line)
            if field_match and "(" not in line:
                current.fields.append(
                    FieldInfo(
                        visibility=field_match.group(1),
                        modifiers=clean_modifiers(field_match.group(2) or ""),
                        type_name=" ".join(field_match.group(3).split()),
                        name=field_match.group(4),
                        line=idx,
                    )
                )
            else:
                if not line.startswith("@") and not any(line.startswith(prefix) for prefix in CONTROL_PREFIXES):
                    method_match = METHOD_RE.match(line)
                    if method_match:
                        current.methods.append(
                            MethodInfo(
                                visibility=method_match.group(1),
                                modifiers=clean_modifiers(method_match.group(2) or ""),
                                return_type=" ".join(method_match.group(3).split()),
                                name=method_match.group(4),
                                params=" ".join(method_match.group(5).split()),
                                line=idx,
                            )
                        )
                    else:
                        ctor_match = CTOR_RE.match(line)
                        if ctor_match:
                            ctor_name = ctor_match.group(3)
                            if ctor_name == current.name:
                                current.methods.append(
                                    MethodInfo(
                                        visibility=ctor_match.group(1),
                                        modifiers=clean_modifiers(ctor_match.group(2) or ""),
                                        return_type="(constructor)",
                                        name=ctor_name,
                                        params=" ".join(ctor_match.group(4).split()),
                                        line=idx,
                                        is_constructor=True,
                                    )
                                )

        depth += raw_line.count("{")
        depth -= raw_line.count("}")

    return classes


def render_markdown(classes: List[ClassInfo]) -> str:
    lines: List[str] = []
    lines.extend(
        [
            "---",
            "sidebar_position: 15",
            "---",
            "",
            "# Agent Teams 全量 API 参考（类/函数/变量 + Demo + 预期）",
            "",
            "本页覆盖 `io.github.lnyocly.ai4j.agent.team` 与 `io.github.lnyocly.ai4j.agent.team.tool` 包中所有源码类，",
            "并按“类 -> 变量 -> 函数”展开说明，方便排查与二次开发。",
            "",
            "> 文档由脚本从源码生成，建议在 Agent Teams 代码变更后重新执行：",
            "> `python docs-site/scripts/generate_agent_teams_api_docs.py`",
            "",
            "## 1. 快速 Demo",
            "",
            "```java",
            "Agent lead = Agents.react()",
            "        .modelClient(new ResponsesModelClient(responsesService))",
            "        .model(\"doubao-seed-1-8-251228\")",
            "        .systemPrompt(\"你是团队负责人，先规划再汇总\")",
            "        .build();",
            "",
            "Agent backend = Agents.react()",
            "        .modelClient(new ResponsesModelClient(responsesService))",
            "        .model(\"doubao-seed-1-8-251228\")",
            "        .build();",
            "",
            "Agent frontend = Agents.react()",
            "        .modelClient(new ResponsesModelClient(responsesService))",
            "        .model(\"doubao-seed-1-8-251228\")",
            "        .build();",
            "",
            "AgentTeam team = Agents.team()",
            "        .leadAgent(lead)",
            "        .member(AgentTeamMember.builder().id(\"backend\").name(\"后端\").agent(backend).build())",
            "        .member(AgentTeamMember.builder().id(\"frontend\").name(\"前端\").agent(frontend).build())",
            "        .options(AgentTeamOptions.builder()",
            "                .parallelDispatch(true)",
            "                .continueOnMemberError(true)",
            "                .maxRounds(64)",
            "                .build())",
            "        .build();",
            "",
            "AgentTeamResult result = team.run(\"输出本周交付计划\");",
            "System.out.println(result.getOutput());",
            "```",
            "",
            "## 2. 预期行为（用于验收）",
            "",
            "- 预期 1：Planner 先产出 `tasks`，任务进入 `PENDING/READY`。",
            "- 预期 2：并发开启时，多成员任务会并行执行；串行模式则按批次单线程执行。",
            "- 预期 3：成员成功后任务转为 `COMPLETED`；异常转 `FAILED`，依赖任务可能转 `BLOCKED`。",
            "- 预期 4：`continueOnMemberError=true` 时，失败任务不会中断整个团队，最终仍会尝试汇总。",
            "- 预期 5：启用 `enableMemberTeamTools` 后，成员可调用 `team_send_message/team_claim_task/...` 完成主动协作。",
            "",
            "## 3. 类/变量/函数全量说明",
            "",
        ]
    )

    for cls in sorted(classes, key=lambda c: (c.file_path, c.full_name)):
        lines.append(f"### {cls.kind} `{cls.full_name}`")
        lines.append("")
        lines.append(f"- 源码：`{cls.file_path}:{cls.line}`")
        lines.append(f"- 职责：{CLASS_DESCRIPTIONS.get(cls.name, '该类型用于 Agent Teams 运行链路中的结构定义或执行逻辑。')}")
        lines.append("")

        lines.append("**变量（字段）**")
        lines.append("")
        if not cls.fields:
            lines.append("- 无显式字段（或仅由 Lombok/编译器生成）。")
        else:
            lines.append("| 名称 | 类型 | 可见性 | 修饰符 | 说明 |")
            lines.append("| --- | --- | --- | --- | --- |")
            for field in cls.fields:
                desc = infer_field_description(field.name, field.type_name)
                lines.append(
                    f"| `{field.name}` | `{field.type_name}` | `{field.visibility}` | `{field.modifiers}` | {desc} |"
                )
        lines.append("")

        lines.append("**函数（方法）**")
        lines.append("")
        if not cls.methods:
            lines.append("- 无显式方法（或主要由 Lombok 生成 getter/setter/builder）。")
        else:
            lines.append("| 方法 | 返回 | 可见性 | 修饰符 | 说明 |")
            lines.append("| --- | --- | --- | --- | --- |")
            for method in cls.methods:
                signature = f"{method.name}({method.params})"
                desc = infer_method_description(method.name, method.is_constructor, method.return_type)
                lines.append(
                    f"| `{signature}` | `{method.return_type}` | `{method.visibility}` | `{method.modifiers}` | {desc} |"
                )
        lines.append("")

    return "\n".join(lines).rstrip() + "\n"


def main() -> None:
    if not SOURCE_ROOT.exists():
        raise SystemExit(f"Source root not found: {SOURCE_ROOT}")

    all_classes: List[ClassInfo] = []
    for file_path in sorted(SOURCE_ROOT.rglob("*.java")):
        all_classes.extend(parse_java_file(file_path))

    markdown = render_markdown(all_classes)
    OUTPUT_FILE.write_text(markdown, encoding="utf-8")
    print(f"Generated {OUTPUT_FILE.relative_to(REPO_ROOT)} with {len(all_classes)} class entries.")


if __name__ == "__main__":
    main()
