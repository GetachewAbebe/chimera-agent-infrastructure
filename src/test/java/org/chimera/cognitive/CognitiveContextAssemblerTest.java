package org.chimera.cognitive;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.chimera.model.TaskContext;
import org.junit.jupiter.api.Test;

class CognitiveContextAssemblerTest {

  @Test
  void shouldInjectPersonaAndMemoryConstraintsIntoTaskContext() {
    PersonaLoader personaLoader =
        agentId ->
            new AgentPersona(
                "chimera-prime",
                "Chimera Prime",
                List.of("concise"),
                List.of("Always disclose AI nature."),
                "Mission-aligned influencer persona.");
    MemoryRecall memoryRecall =
        new InMemoryMemoryRecall(
            Map.of(
                "worker-alpha",
                List.of("Audience prefers concise CTAs.", "Trend hooks convert better.")));

    CognitiveContextAssembler assembler =
        new CognitiveContextAssembler(personaLoader, memoryRecall);

    TaskContext context =
        assembler.assemble(
            "worker-alpha",
            "Launch sustainability campaign",
            List.of("news://ethiopia/fashion/trends"));

    assertThat(context.goalDescription()).isEqualTo("Launch sustainability campaign");
    assertThat(context.personaConstraints())
        .anyMatch(line -> line.contains("Persona name: Chimera Prime"));
    assertThat(context.personaConstraints())
        .anyMatch(line -> line.contains("Persona directive: Always disclose AI nature."));
    assertThat(context.personaConstraints())
        .anyMatch(line -> line.contains("Memory recall: Audience prefers concise CTAs."));
    assertThat(context.requiredResources()).contains("news://ethiopia/fashion/trends");
  }
}
