package org.chimera.skills;

import java.math.BigDecimal;
import java.util.Map;

public record SkillRequest(
    String skillName, String agentId, Map<String, Object> input, BigDecimal projectedCostUsd) {}
