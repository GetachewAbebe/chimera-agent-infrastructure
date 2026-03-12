package org.chimera.cognitive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SoulMarkdownPersonaLoaderTest {

  @Test
  void shouldParseFrontmatterAndBackstory() {
    String soulMarkdown =
        """
        ---
        name: Chimera Prime
        id: chimera-prime
        voice_traits:
          - concise
          - strategic
        directives:
          - Always disclose AI nature.
          - Escalate sensitive topics.
        ---
        This is the backstory for the persona.
        It should be captured as markdown body text.
        """;

    AgentPersona persona = SoulMarkdownPersonaLoader.parse(soulMarkdown, "worker-alpha");

    assertThat(persona.id()).isEqualTo("chimera-prime");
    assertThat(persona.name()).isEqualTo("Chimera Prime");
    assertThat(persona.voiceTraits()).containsExactly("concise", "strategic");
    assertThat(persona.directives())
        .containsExactly("Always disclose AI nature.", "Escalate sensitive topics.");
    assertThat(persona.backstory()).contains("backstory");
  }

  @Test
  void shouldRejectMissingFrontmatter() {
    assertThatThrownBy(() -> SoulMarkdownPersonaLoader.parse("No frontmatter", "worker-alpha"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("frontmatter");
  }
}
