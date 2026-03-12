# Commit Progression and Git Hygiene

Recommended commit sequence:

1. `chore: initialize project structure and maven baseline`
2. `docs: add specs and architecture source of truth`
3. `feat: add planner-worker-judge domain skeleton`
4. `test: add red-phase contract tests for trend fetch and skill budget`
5. `chore: add automation, CI workflow, and governance policies`
6. `docs: add security, frontend contracts, and trajectory planning`

Commit quality rules:

- One logical change per commit
- Clear, imperative commit message
- Reference impacted rubric category in commit body
- Avoid bundling refactors with behavior changes
