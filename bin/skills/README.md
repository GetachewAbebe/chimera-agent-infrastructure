# Runtime Skills

Runtime skills are reusable capability packages invoked by agents.

Each skill must define:

- Input schema
- Output schema
- Error conditions
- Cost profile
- Security constraints

Runtime reference implementation:

- `src/main/java/org/chimera/skills/RuntimeSkillGateway.java`
- `src/main/java/org/chimera/skills/DownloadYoutubeSkill.java`
- `src/main/java/org/chimera/skills/TranscribeAudioSkill.java`
