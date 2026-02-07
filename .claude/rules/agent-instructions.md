---
description: Guidelines for sub-agent delegation and task execution
---

# Agent Instructions

## Sub-Agent Delegation

- Use sub-agents for independent, parallelizable work (e.g., translations, multi-file searches).
- Each sub-agent should have a clear, focused task.
- Prefer one sub-agent per TODO item when working through a list.

## Exploring vs Implementing

- Use the Explore agent type for codebase research and understanding.
- Use the Plan agent type for designing implementation strategies.
- Only write code after understanding the existing patterns.

## Critical Thinking

- Read existing code before modifying it.
- Follow established patterns in the codebase.
- When unsure about an approach, explore first, then plan, then implement.
- Run builds/tests in sub-agents to keep context clean.
