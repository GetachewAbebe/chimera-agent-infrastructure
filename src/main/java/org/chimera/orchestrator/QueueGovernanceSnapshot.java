package org.chimera.orchestrator;

public record QueueGovernanceSnapshot(
    int retryAttempts,
    int deadLetteredTasks,
    long workerP50LatencyMs,
    long workerP95LatencyMs,
    int successfulExecutions,
    int failedExecutions) {}
