package org.chimera.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.chimera.model.WalletLedgerEntry;

public interface WalletLedgerRepository {
  void append(WalletLedgerEntry entry);

  BigDecimal sumForTenantOnDate(String tenantId, LocalDate date);

  int countForTenantOnDate(String tenantId, LocalDate date);
}
