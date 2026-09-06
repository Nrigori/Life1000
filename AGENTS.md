# Life1000 Codex Instructions

## Source of truth

Before implementing any feature, read:

- `docs/PRD.md`

`docs/PRD.md` is the authoritative product specification.

If the current task conflicts with the PRD, stop and point out the conflict instead of redesigning the product yourself.

## Development rules

- Only implement the Phase explicitly requested in the current task.
- Do not implement features from later Phases in advance.
- Do not add features that are not defined in the PRD.
- Keep the architecture simple and appropriate for a personal single-user application.
- Do not introduce unnecessary abstractions, frameworks, or infrastructure.
- Read relevant existing code before modifying it.
- Avoid modifying files unrelated to the current task.
- Preserve existing working behavior unless the task explicitly requires changing it.
- After implementation, run relevant build/tests and report the result.
- If a requirement is ambiguous, prefer the simplest implementation consistent with the PRD.

## Product principles

Life1000 helps record life, not manage life.

Do not introduce:
- deadlines
- reminders
- task progress systems
- gamification
- AI features
- social features
- complex user/permission systems
- analytics dashboards

unless the PRD is explicitly updated to require them.

## Git

Keep each development Phase logically isolated so it can be committed separately.