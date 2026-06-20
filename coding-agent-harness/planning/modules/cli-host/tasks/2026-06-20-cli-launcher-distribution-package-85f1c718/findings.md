# CLI launcher distribution package - 发现记录

## 关键判断

| ID | Finding | Evidence | Decision |
| --- | --- | --- | --- |
| F-001 | 发行包应围绕已有 fat jar，而不是新增 Java launcher 层 | `Ai4jCliMain` 已是稳定 main class；`Ai4jCli` 已支持 `code` / `run` / `tui` / `acp` / `extension` | 只新增 shell/cmd launcher 和 assembly |
| F-002 | `src/main/dist` 会被根 `.gitignore` 的 `dist/` 误忽略 | `git check-ignore -v ai4j-cli/src/main/dist/bin/ai4j` 命中根规则 | 添加精确反忽略 `!ai4j-cli/src/main/dist/**` |
| F-003 | launcher 必须不保存 provider 配置 | 用户提供了真实 provider token；发布层不应接触或记录 | 示例配置只使用 `${OPENAI_API_KEY}` / `${YOUR_PROVIDER_API_KEY}` placeholder，并增加 generic token-pattern test |
| F-004 | Maven filtering 必须显式支持 `@project.version@` | 默认 resources filtering 不替换 `@project.version@` | 在 `maven-resources-plugin` 配置 delimiter `@`，并用 package smoke 检查 zip 内 launcher |

## 不采用方案

- 不引入 jpackage/native-image：维护成本高，且当前目标只是源码可生成稳定 CLI distribution。
- 不在 launcher 中写 provider/model/baseUrl：配置解析已经在 CLI runtime，launcher 只做启动。
- 不在本任务重构在线 install 脚本：已有 `docs-site/static/install.*` 可独立作为在线安装链路，dist 包是 release asset 链路。
- 不做真实 provider CLI 测试：本任务验证启动/打包，不验证模型响应。

## 风险与残余

| Risk | Owner | Status | Follow-up |
| --- | --- | --- | --- |
| checksum / GitHub Release asset 上传尚未实现 | coordinator | accepted | 后续 release automation task |
| Unix launcher 未在真实 Unix shell 下执行 | coordinator | accepted | CI/Linux package smoke 后续补齐 |
| docs-site 在线脚本域名/发布版本需要 release 时再确认 | coordinator | accepted | release workflow task |
