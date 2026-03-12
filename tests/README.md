# Test Index

This top-level `tests/` directory contains the two challenge-specific TDD contract tests and serves as the submission-friendly index for the rest of the repository test suites.

Challenge-required contract tests:
- `tests/org/chimera/tests/trendFetcherTest.java`
- `tests/org/chimera/tests/skillsInterfaceTest.java`

Backend and contract tests:
- `src/test/java/org/chimera/tests/**`
- `src/test/java/org/chimera/api/**`
- `src/test/java/org/chimera/cognitive/**`
- `src/test/java/org/chimera/security/**`
- `src/test/java/org/chimera/persistence/**`

Frontend browser tests:
- `frontend/tests/e2e/dashboard.spec.ts`
- `frontend/tests/e2e/dashboard.live.spec.ts`

Load and benchmark validation:
- `scripts/load_validation.sh`
- `scripts/sustained_benchmark.sh`
- `scripts/redis_cluster_stress.sh`
