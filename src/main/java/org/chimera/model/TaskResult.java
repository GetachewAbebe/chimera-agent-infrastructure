package org.chimera.model;

import java.time.Instant;
import java.util.UUID;

public record TaskResult(
    UUID taskId,
    String workerId,
    boolean success,
    double confidenceScore,
    String payload,
    String reasoningTrace,
    Instant completedAt) {}
