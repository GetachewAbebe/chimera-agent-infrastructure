package org.chimera.cognitive;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClasspathSoulPersonaLoaderTest {

  @Test
  void shouldLoadPersonaFromClasspathResource() {
    ClasspathSoulPersonaLoader loader = new ClasspathSoulPersonaLoader("soul/SOUL.md");

    AgentPersona persona = loader.loadPersona("worker-alpha");

    assertThat(persona.id()).isEqualTo("chimera-prime");
    assertThat(persona.name()).isEqualTo("Chimera Prime");
    assertThat(persona.voiceTraits()).isNotEmpty();
    assertThat(persona.directives()).isNotEmpty();
  }
}
