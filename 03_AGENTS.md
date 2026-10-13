# AGENTS.md — Permanent Instructions for Coding Agents

## Project

Smart Grocery Store Management System.

This is a college project with an approved core idea. The agent's job is to help implement it professionally without silently changing its purpose.

## Source of Truth

Read these files before making substantial changes:

1. PRD.md
2. ARCHITECTURE.md
3. PROJECT_MEMORY.md
4. DECISIONS.md
5. TASKS.md
6. TEST_PLAN.md

If source project material is available, treat the approved project requirements as the original source of truth.

## Beginner-First Rule

The owner has limited coding and project-development experience.

Whenever implementing a feature:

- Explain what you are doing in simple language.
- Explain important files you changed.
- Explain how to run/test the change.
- Explain any new concept the owner needs to understand.
- Do not assume the owner knows framework terminology.

## Before Coding

1. Inspect the repository.
2. Read relevant documentation.
3. Identify existing implementation.
4. Check whether the feature already exists.
5. Propose a small implementation plan.
6. Identify files that will change.
7. Implement only the requested scope.

## Never Invent Requirements

If requirements are ambiguous:

- Ask a question when the ambiguity affects architecture, security, data, or user-visible behavior.
- If the ambiguity is minor, choose the simplest reasonable behavior and document the assumption in DECISIONS.md.

Never silently create major features.

## Small Changes

Prefer small, reviewable changes.

Do not rewrite unrelated code.

Do not change the database schema unless required by the current task.

## Verification

After implementation:

1. Run relevant tests.
2. Run lint/format checks if configured.
3. Start the application when practical.
4. Test the changed workflow.
5. Report failures honestly.
6. Do not claim a feature works if it was not tested.

## Database Safety

Never:

- Drop production data.
- Delete tables casually.
- Reset a database without explicit permission.

For schema changes:

- Create/update migration or schema documentation.
- Explain the change.
- Provide safe setup instructions.

## Security

Never:

- Commit passwords/API keys.
- Hardcode database credentials.
- Store passwords in plain text.
- Trust client-side authorization.
- Expose secrets in logs.

## Documentation Synchronization

After meaningful changes, update:

- TASKS.md
- PROJECT_MEMORY.md
- DECISIONS.md if a decision changed
- API/database documentation when applicable

## Git

Prefer small commits with meaningful messages.

Before suggesting a commit, summarize:

- What changed
- Why
- Tests performed

## Agent Handoff

When finishing a task, provide:

```text
STATUS:
CHANGED:
TESTED:
NOT TESTED:
ASSUMPTIONS:
NEXT RECOMMENDED TASK:
```

## Stop Conditions

Stop and ask the user before proceeding if:

- A requirement conflicts with the PRD.
- A database destructive operation is required.
- Credentials/secrets are requested.
- A major framework change is proposed.
- The task would substantially expand the project scope.
- The agent cannot determine the intended behavior safely.
