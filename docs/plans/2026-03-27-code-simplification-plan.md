## Goal

Do a non-functional cleanup pass on the recently touched stream/runtime code and adjacent CLI wiring so the code is easier to read, easier to extend, and still behaves exactly the same.

## Constraints

- Preserve all current behavior.
- Avoid broad rewrites in heavily edited files.
- Prefer extra clarity over cleverness.
- Keep the cleanup limited to low-risk areas that already changed in this stream-control slice.

## Work Plan

### 1. Audit and choose safe targets

- Review the current stream execution path across `ai4j-model`, `ai4j-agent`, and `ai4j-cli`.
- Identify duplicated lifecycle logic and option-building code that can be centralized safely.
- Avoid large refactors inside `CodingCliSessionRunner` because it already has heavy in-flight edits.

### 2. Simplify shared stream lifecycle code

- Extract duplicated listener lifecycle behavior into a shared base class.
- Keep provider-facing behavior unchanged:
  - first-token timeout
  - idle timeout
  - retry callback propagation
  - cancel-aware shutdown
  - deferred error dispatch after stream wait

### 3. Simplify CLI option and factory wiring

- Reduce repeated constructor/copy code in CLI option objects.
- Extract explicit helper methods for stream execution defaults in the CLI agent factory.
- Keep `/stream` semantics unchanged: it remains the single real API streaming switch.

### 4. Review structure and document pragmatic next steps

- Check the top-level Maven module split for obvious problems.
- Check whether the current package layout is too flat in high-change modules.
- Only document medium-term structure improvements in this pass; do not perform risky package moves while the workspace is already very dirty.

## Validation

### Focused regression suite

```bash
mvn --% -pl ai4j-cli -am -Dtest=CodeCommandTest,CodeCommandOptionsParserTest,DefaultCodingCliAgentFactoryTest,AgentRuntimeTest,ChatModelClientTest,ResponsesModelClientTest,StreamExecutionSupportTest -Dsurefire.failIfNoSpecifiedTests=false -DskipTests=false test
```

### Broader packaging signal

```bash
mvn --% -pl ai4j-cli -am -DskipTests package
```

## Layout Review Notes

### Keep as-is for now

- The top-level Maven split is directionally correct:
  - `ai4j-core`
  - `ai4j-model`
  - `ai4j-agent`
  - `ai4j-coding`
  - `ai4j-tui`
  - `ai4j-cli`

### Improve later in staged refactors

- `ai4j-cli` is functionally workable but too flat. A later staged split should move classes into subpackages such as:
  - `command`
  - `config`
  - `mcp`
  - `session`
  - `render`
  - `terminal`
  - `factory`
- Do not do that package move in this pass because it would create a noisy, high-risk diff for limited value.
