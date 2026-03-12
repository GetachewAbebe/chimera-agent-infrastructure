package org.chimera.security;

import java.util.Optional;

public interface SecretProvider {
  Optional<String> getSecret(String key);

  default String getRequiredSecret(String key) {
    return getSecret(key)
        .filter(value -> !value.isBlank())
        .orElseThrow(() -> new IllegalStateException("Missing required secret: " + key));
  }
}
