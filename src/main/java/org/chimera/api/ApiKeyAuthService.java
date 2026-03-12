package org.chimera.api;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class ApiKeyAuthService {
  public static final String ENV_API_KEYS = "CHIMERA_API_KEYS";

  // Format: key:tenant:role1,role2;key2:tenant2:role1
  private static final String DEFAULT_DEV_KEYS =
      "dev-tenant-alpha-key:tenant-alpha:operator,reviewer,viewer;"
          + "dev-tenant-beta-key:tenant-beta:operator,reviewer,viewer";

  private final Map<String, ApiKeyIdentity> identities;

  public ApiKeyAuthService(Map<String, ApiKeyIdentity> identities) {
    this.identities = new ConcurrentHashMap<>(identities);
  }

  public static ApiKeyAuthService fromEnvironment() {
    String raw = System.getenv().getOrDefault(ENV_API_KEYS, DEFAULT_DEV_KEYS);
    return new ApiKeyAuthService(parse(raw));
  }

  public Optional<ApiKeyIdentity> authenticate(String apiKey) {
    if (apiKey == null || apiKey.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(identities.get(apiKey));
  }

  static Map<String, ApiKeyIdentity> parse(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("API key configuration is empty");
    }

    Map<String, ApiKeyIdentity> parsed = new ConcurrentHashMap<>();

    for (String entry : raw.split(";")) {
      String trimmed = entry.trim();
      if (trimmed.isEmpty()) {
        continue;
      }

      String[] parts = trimmed.split(":", 3);
      if (parts.length != 3) {
        throw new IllegalArgumentException("Invalid CHIMERA_API_KEYS entry: " + trimmed);
      }

      String apiKey = parts[0].trim();
      String tenantId = parts[1].trim();
      Set<UserRole> roles =
          Arrays.stream(parts[2].split(","))
              .map(String::trim)
              .filter(token -> !token.isBlank())
              .map(String::toUpperCase)
              .map(UserRole::valueOf)
              .collect(Collectors.toSet());

      if (apiKey.isBlank() || tenantId.isBlank() || roles.isEmpty()) {
        throw new IllegalArgumentException("Invalid CHIMERA_API_KEYS entry: " + trimmed);
      }

      parsed.put(apiKey, new ApiKeyIdentity(tenantId, roles));
    }

    if (parsed.isEmpty()) {
      throw new IllegalArgumentException("No valid API key entries found");
    }

    return parsed;
  }
}
