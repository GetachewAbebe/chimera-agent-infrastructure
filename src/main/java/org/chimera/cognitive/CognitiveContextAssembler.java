package org.chimera.cognitive;

import java.util.ArrayList;
import java.util.List;
import org.chimera.model.TaskContext;

public final class CognitiveContextAssembler {
  private static final int DEFAULT_MEMORY_LIMIT = 3;

  private final PersonaLoader personaLoader;
  private final MemoryRecall memoryRecall;

  public CognitiveContextAssembler(PersonaLoader personaLoader, MemoryRecall memoryRecall) {
    if (personaLoader == null) {
      throw new IllegalArgumentException("personaLoader is required");
    }
    if (memoryRecall == null) {
      throw new IllegalArgumentException("memoryRecall is required");
    }
    this.personaLoader = personaLoader;
    this.memoryRecall = memoryRecall;
  }

  public TaskContext assemble(
      String agentId, String goalDescription, List<String> requiredResources) {
    AgentPersona persona = personaLoader.loadPersona(agentId);
    List<String> constraints = new ArrayList<>();
    constraints.add("Respect persona directives");
    constraints.add("Always include AI disclosure if platform supports it");
    constraints.add("Persona name: " + safe(persona.name()));
    constraints.add("Persona backstory: " + summarize(persona.backstory(), 180));
    for (String directive : persona.directives()) {
      constraints.add("Persona directive: " + directive);
    }
    for (String voiceTrait : persona.voiceTraits()) {
      constraints.add("Voice trait: " + voiceTrait);
    }

    List<String> memories =
        memoryRecall.recallRelevantMemories(agentId, goalDescription, DEFAULT_MEMORY_LIMIT);
    for (String memory : memories) {
      constraints.add("Memory recall: " + memory);
    }

    List<String> resources = requiredResources == null ? List.of() : requiredResources;
    return new TaskContext(goalDescription, List.copyOf(constraints), List.copyOf(resources));
  }

  private static String safe(String value) {
    return value == null ? "" : value.trim();
  }

  private static String summarize(String text, int maxLength) {
    String normalized = text == null ? "" : text.trim().replaceAll("\\s+", " ");
    if (normalized.length() <= maxLength) {
      return normalized;
    }
    return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
  }
}
