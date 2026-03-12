# Tooling Strategy: Dev MCPs vs Runtime Skills

## Developer MCP Servers

- `filesystem-mcp`: safe repository manipulation
- `git-mcp`: commit and diff introspection
- `tenxfeedbackanalytics`: telemetry and interaction quality measurement

## Runtime Skills (Agent Capabilities)

- `skill_download_youtube`
- `skill_transcribe_audio`

These are separate from MCP servers. Skills are reusable capability packages with strict I/O contracts. MCP servers are integration bridges to external systems.

## Standards

- Every skill must define input schema, output schema, failure modes, and cost profile.
- Every MCP configuration must be versioned and environment-scoped.
