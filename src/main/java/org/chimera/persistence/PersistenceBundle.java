package org.chimera.persistence;

import org.chimera.api.RateLimiter;

public record PersistenceBundle(
    TaskRepository taskRepository,
    WalletLedgerRepository walletLedgerRepository,
    TrendSignalRepository trendSignalRepository,
    DeadLetterReplayAuditRepository deadLetterReplayAuditRepository,
    RateLimiter writeRateLimiter,
    AutoCloseable closeable)
    implements AutoCloseable {
  @Override
  public void close() {
    try {
      closeable.close();
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to close persistence resource", ex);
    }
  }
}
