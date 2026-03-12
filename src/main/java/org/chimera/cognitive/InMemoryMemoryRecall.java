package org.chimera.cognitive;

import java.util.List;
import java.util.Map;

public final class InMemoryMemoryRecall implements MemoryRecall {
  private final Map<String, List<String>> memoriesByAgentId;

  public InMemoryMemoryRecall(Map<String, List<String>> memoriesByAgentId) {
    this.memoriesByAgentId = memoriesByAgentId == null ? Map.of() : Map.copyOf(memoriesByAgentId);
  }

  @Override
  public List<String> recallRelevantMemories(String agentId, String query, int limit) {
    if (limit < 1) {
      throw new IllegalArgumentException("limit must be >= 1");
    }
    if (agentId == null || agentId.isBlank()) {
      return List.of();
    }
    List<String> memories = memoriesByAgentId.getOrDefault(agentId, List.of());
    return memories.stream()
        .filter(entry -> entry != null && !entry.isBlank())
        .limit(limit)
        .toList();
  }
}
