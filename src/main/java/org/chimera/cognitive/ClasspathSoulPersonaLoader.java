package org.chimera.cognitive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class ClasspathSoulPersonaLoader implements PersonaLoader {
  private final String resourcePath;
  private final ClassLoader classLoader;

  public ClasspathSoulPersonaLoader(String resourcePath) {
    this(resourcePath, ClasspathSoulPersonaLoader.class.getClassLoader());
  }

  ClasspathSoulPersonaLoader(String resourcePath, ClassLoader classLoader) {
    if (resourcePath == null || resourcePath.isBlank()) {
      throw new IllegalArgumentException("resourcePath is required");
    }
    if (classLoader == null) {
      throw new IllegalArgumentException("classLoader is required");
    }
    this.resourcePath = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
    this.classLoader = classLoader;
  }

  @Override
  public AgentPersona loadPersona(String agentId) {
    try (InputStream stream = classLoader.getResourceAsStream(resourcePath)) {
      if (stream == null) {
        throw new IllegalStateException("SOUL resource not found: " + resourcePath);
      }
      String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      return SoulMarkdownPersonaLoader.parse(content, agentId);
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to read SOUL resource: " + resourcePath, ex);
    }
  }
}
