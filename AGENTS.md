# AGENTS.md — Pragmatic Coding Rules

These instructions apply to all coding tasks unless a more specific project instruction overrides them.

## Communication
- Respond in Simplified Chinese by default. Keep code, identifiers, commands, and existing project terminology in their original language.
- Be concise. Explain the outcome, important trade-offs, and verification results; do not narrate routine steps.
- If requirements are genuinely ambiguous and the choice materially changes the implementation, ask a focused question. Otherwise make a reasonable assumption and state it briefly.

## Core principles
1. **Think before coding.** Understand the request and inspect the relevant code before editing. Identify the smallest change that solves the actual problem.
2. **Simplicity first.** Prefer straightforward, readable code over clever abstractions, speculative flexibility, or elaborate architecture.
3. **Surgical changes.** Modify only files and code paths needed for the request. Preserve existing conventions and behavior outside the requested scope.
4. **Evidence over speculation.** Add complexity only for a concrete requirement, demonstrated failure mode, or established project convention.
5. **Know when to stop.** Once requirements are met and relevant checks pass, stop. Do not expand the task into unrelated cleanup or redesign.

## Avoid overengineering
- Do not introduce new layers, frameworks, patterns, factories, interfaces, base classes, helpers, configuration switches, or dependencies for a single straightforward use case.
- Do not build extension points for hypothetical future requirements. Implement today's requirement; refactor when a real second use case justifies it.
- Prefer existing project utilities and patterns. Do not duplicate functionality that already exists.
- Do not split a simple function merely to create abstraction. Extract only when it improves clarity, removes meaningful duplication, or isolates a real responsibility.
- Avoid premature optimization. Optimize only when performance requirements or measurements justify it.
- Keep comments focused on non-obvious intent and constraints; do not restate what the code already says.

## Avoid excessive defensive programming
- Validate input at real trust boundaries (user input, network responses, external files, third-party services). Do not repeatedly validate values already guaranteed by internal contracts.
- Do not add speculative null checks, type guards, defaults, or fallback branches for states that cannot occur under the documented contract.
- Do not wrap every operation in try/catch or try/except. Catch exceptions only where you can meaningfully recover, add actionable context, or translate an error at a boundary.
- Never silently swallow errors or return fake success values. Prefer clear failures over hidden corruption.
- Do not add retries, timeouts, caches, circuit breakers, compatibility shims, or graceful degradation without a real requirement or demonstrated failure mode.
- Do not add redundant logging, defensive copies, or state synchronization without a concrete reason.
- **Safety exception:** Preserve or add necessary authentication, authorization, input validation, transaction integrity, resource cleanup, and security checks. Simplicity must not compromise correctness or safety.

## Scope and compatibility
- Do not refactor adjacent code, rename unrelated symbols, reformat whole files, or change public APIs unless required.
- Do not add backward-compatibility code for unsupported or unspecified environments. Respect compatibility requirements explicitly stated by the project.
- Preserve existing tests and behaviors unless the request explicitly changes them.
- Do not modify generated files, lockfiles, or dependency manifests unless the task requires it.
- Avoid broad search-and-replace when a targeted edit is possible.

## Implementation workflow
1. Read the relevant instructions, files, and nearby tests.
2. State a short plan only for nontrivial tasks; do not produce lengthy design documents for small edits.
3. Make the smallest coherent implementation.
4. Run the most relevant available checks or tests. Do not run expensive full-suite checks by default when targeted checks suffice. Do not run a build unless the user explicitly requests a build, launch, or build verification for the current work.
5. Fix failures caused by your change. Clearly distinguish pre-existing failures.
6. Update progress records as meaningful work is completed, then commit, push, and merge according to the rules below once the current task meets its acceptance criteria.
7. Report what changed, current progress, verification evidence, and delivery results. Stop.

## 进度跟踪
- 使用 `docs/progress.md` 作为统一进度入口，并同步更新 `docs/development-plan.md` 中对应阶段的状态及验收复选框。
- 开始一个阶段、完成有意义的任务、发现阻塞或验证结果发生变化时，立即更新记录，不等到最终回复才统一补写。
- 记录必须说明：当前阶段、已完成内容、正在进行的工作、未完成 / 未验收项、下一步，以及实际执行的检查和结果。
- 区分“已实现”“构建通过”“启动成功”“交互验收通过”。未执行的验收不得勾选；不虚构完成比例或把文档规划当成已交付功能。
- 工作期间及时向用户说明有意义的进展；最终回复交代做到哪里、还有什么未完成。

## 分支与交付
- 新功能开发默认新建 `codex/<feature>` 分支；如果已有同一目标的功能分支，优先复用。一条分支覆盖一个完整功能或相对完整的开发阶段，不按文件、按钮或细小任务拆分分支。
- 常规文档修订和小型修复可复用适合的当前分支；涉及同一目标的改动集中交付，不为拆分而拆分。
- 当前已获授权的任务达到约定验收要求后，直接暂存本仓库工作区的全部改动，提交并推送功能分支，再合并到远端默认主分支（当前为 `main`）并推送，不再询问确认。若直接在主分支上处理允许的小型改动，则提交后直接推送主分支。
- “任务完成”以本次授权范围为准，不要求整个项目所有阶段完成。例如规划文档可以独立交付，但不能把尚未完成的产品阶段标成通过。
- 合并前获取远端最新状态并完成本次变更适用的检查。遵循用户默认不构建的约定，不因提交或合并而额外运行构建；如某验收尚未执行，准确记录验证边界，不宣称其通过。
- 使用正常的 Git 合并与推送，不强制推送、不破坏已有历史。远端分支保护、权限或无法安全解决的冲突阻塞交付时，保留现有工作并明确报告实际状态，不绕过限制。
- 提交信息使用 Conventional Commits：`<type>(<scope>): <subject>`。`type` 仅允许 `feat`、`fix`、`perf`、`refactor`、`docs`、`style`、`test`、`build`、`revert`、`ci`、`chore`、`release`、`workflow`；`scope` 优先使用实际模块、页面、组件、目录或领域，`subject` 使用简洁中文；有多个重点时使用中文分行补充正文。
- 提交、推送和合并后核对远端状态，在最终回复中说明提交与合并结果。

## Tests and quality
- Add or update tests when behavior changes or a meaningful regression risk exists; avoid tests that only assert implementation details.
- Do not create extensive mocks, fixtures, test helpers, or edge-case matrices without a concrete need.
- Do not claim a test passed unless it was actually run.
- Follow the project's formatter, linter, type checker, and established style where applicable.
- Prefer explicit, readable control flow. Avoid unnecessary indirection and deeply nested conditionals.

## Decision checklist
Before adding code, ask:
- Is this required by the current task or an established project contract?
- Is there a simpler solution using existing code?
- Am I handling a real failure mode or imagining one?
- Does this change touch anything unrelated?
- Will the added complexity clearly pay for itself now?

If the answer does not justify the complexity, leave it out.

## Completion format
In Simplified Chinese, briefly provide:
- **变更：** What was changed.
- **进度：** Current phase, completed work, and next step.
- **验证：** Checks actually run and their results (or why not run).
- **交付：** Commit, push, and main-branch merge results.
- **注意：** Only meaningful risks, assumptions, or follow-ups; omit this section if none.
