package org.chimera.openclaw;

import java.time.Instant;

public record OpenClawPublicationAttempt(
    String tenantId,
    String agentId,
    OpenClawAvailability availability,
    OpenClawSafetyLevel safetyLevel,
    boolean accepted,
    String reason,
    Instant attemptedAt) {}
