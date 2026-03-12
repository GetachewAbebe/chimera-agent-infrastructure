package org.chimera.security;

import java.util.Map;
import java.util.Optional;

public final class EnvironmentSecretProvider implements SecretProvider {
  private final Map<String, String> source;

  public EnvironmentSecretProvider() {
    this(System.getenv());
  }

  public EnvironmentSecretProvider(Map<String, String> source) {
    this.source = Map.copyOf(source);
  }

  @Override
  public Optional<String> getSecret(String key) {
    if (key == null || key.isBlank()) {
      return Optional.empty();
    }
    String value = source.get(key);
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(value);
  }
}
