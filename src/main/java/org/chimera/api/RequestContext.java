package org.chimera.api;

public record RequestContext(String requestId, String tenantId, UserRole role) {}
