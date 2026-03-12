package org.chimera.cognitive;

import java.util.List;

public interface MemoryRecall {
  List<String> recallRelevantMemories(String agentId, String query, int limit);
}
