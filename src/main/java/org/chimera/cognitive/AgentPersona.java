package org.chimera.cognitive;

import java.util.List;

public record AgentPersona(
    String id, String name, List<String> voiceTraits, List<String> directives, String backstory) {
  public AgentPersona {
    voiceTraits = voiceTraits == null ? List.of() : List.copyOf(voiceTraits);
    directives = directives == null ? List.of() : List.copyOf(directives);
    backstory = backstory == null ? "" : backstory.trim();
  }
}
