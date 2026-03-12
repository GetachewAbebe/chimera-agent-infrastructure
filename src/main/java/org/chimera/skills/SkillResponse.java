package org.chimera.skills;

import java.util.Map;

public record SkillResponse(boolean accepted, Map<String, Object> output) {}
