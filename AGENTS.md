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
4. Run the most relevant available checks or tests. Do not run expensive full-suite checks by default when targeted checks suffice, unless project policy requires them.
5. Fix failures caused by your change. Clearly distinguish pre-existing failures.
6. Report what changed, what was verified, and any material limitations. Stop.

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
- **验证：** Checks actually run and their results (or why not run).
- **注意：** Only meaningful risks, assumptions, or follow-ups; omit this section if none.
