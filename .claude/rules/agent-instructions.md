---
description: Sub-agent delegation limits
---

# Agent Instructions

## Sub-Agent Delegation

Delegate only for large, genuinely independent tracks of work — a wide multi-file
investigation across unfamiliar packages, or a sweep that would otherwise mean reading
dozens of files to answer one question.

- Do not delegate work you can finish yourself in a handful of tool calls.
- Do not use a sub-agent to verify or double-check your own work.
- If one sub-agent can do the job, use one rather than several. Keep spawn counts low.

## Output Isolation Is Not Delegation

The cap above is about splitting up *work*. Routing a command through an agent purely to
keep its output out of the main context is a different thing, and the cap does not apply:

- `devtools:build-runner` for every build, test, and lint run (see `rules/build-commands.md`).

Large tool output is re-sent on every subsequent turn, so isolating it stays worthwhile
regardless of context window size.
