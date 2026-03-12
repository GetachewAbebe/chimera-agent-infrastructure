package org.chimera.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletLedgerEntry(
    UUID ledgerId,
    String tenantId,
    UUID taskId,
    String workerId,
    BigDecimal amountUsd,
    String provider,
    String transactionId,
    Instant executedAt) {
  public WalletLedgerEntry {
    ledgerId = ledgerId == null ? UUID.randomUUID() : ledgerId;
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId must not be blank");
    }
    if (taskId == null) {
      throw new IllegalArgumentException("taskId is required");
    }
    if (workerId == null || workerId.isBlank()) {
      throw new IllegalArgumentException("workerId must not be blank");
    }
    if (amountUsd == null || amountUsd.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("amountUsd must be > 0");
    }
    if (provider == null || provider.isBlank()) {
      throw new IllegalArgumentException("provider must not be blank");
    }
    if (transactionId == null || transactionId.isBlank()) {
      throw new IllegalArgumentException("transactionId must not be blank");
    }
    executedAt = executedAt == null ? Instant.now() : executedAt;
  }
}
