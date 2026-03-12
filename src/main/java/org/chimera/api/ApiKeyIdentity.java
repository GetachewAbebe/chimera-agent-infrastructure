package org.chimera.api;

import java.util.Set;

public record ApiKeyIdentity(String tenantId, Set<UserRole> allowedRoles) {}
