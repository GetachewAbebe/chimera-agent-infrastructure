package org.chimera.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.chimera.model.WalletLedgerEntry;

public final class InMemoryWalletLedgerRepository implements WalletLedgerRepository {
  private final List<WalletLedgerEntry> entries = new CopyOnWriteArrayList<>();

  @Override
  public void append(WalletLedgerEntry entry) {
    if (entry == null) {
      throw new IllegalArgumentException("entry is required");
    }
    entries.add(entry);
  }

  @Override
  public BigDecimal sumForTenantOnDate(String tenantId, LocalDate date) {
    validateTenantAndDate(tenantId, date);
    return entries.stream()
        .filter(entry -> entry.tenantId().equals(tenantId))
        .filter(entry -> toUtcDate(entry).equals(date))
        .map(WalletLedgerEntry::amountUsd)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  @Override
  public int countForTenantOnDate(String tenantId, LocalDate date) {
    validateTenantAndDate(tenantId, date);
    return Math.toIntExact(
        entries.stream()
            .filter(entry -> entry.tenantId().equals(tenantId))
            .filter(entry -> toUtcDate(entry).equals(date))
            .count());
  }

  private static LocalDate toUtcDate(WalletLedgerEntry entry) {
    return entry.executedAt().atOffset(ZoneOffset.UTC).toLocalDate();
  }

  private static void validateTenantAndDate(String tenantId, LocalDate date) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId must not be blank");
    }
    if (date == null) {
      throw new IllegalArgumentException("date is required");
    }
  }
}
