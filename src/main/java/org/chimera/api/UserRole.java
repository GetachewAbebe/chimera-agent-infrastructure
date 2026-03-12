package org.chimera.api;

public enum UserRole {
  OPERATOR,
  REVIEWER,
  VIEWER;

  public static UserRole parse(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("X-Role header is required");
    }
    return UserRole.valueOf(raw.trim().toUpperCase());
  }
}
