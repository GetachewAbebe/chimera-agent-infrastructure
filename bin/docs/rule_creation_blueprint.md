# Rule Creation Blueprint (Agent Intent)

This blueprint defines how to generate a functional agent rules file.

## Required Sections

1. Project context
2. Prime directive (spec-first)
3. Language/runtime constraints (Java 21, records, JUnit 5)
4. Traceability requirements
5. Governance constraints (HITL, budget, sensitive topics)

## Validation Checklist

- Rule file references `specs/` as source of truth.
- Rule file prevents direct external API calls outside MCP.
- Rule file requires plan explanation before code generation.
