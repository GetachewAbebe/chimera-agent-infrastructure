package org.chimera.persistence;

import java.time.LocalDate;
import java.util.List;
import org.chimera.model.TrendSignal;

public interface TrendSignalRepository {
  void append(String tenantId, TrendSignal signal);

  int countForTenantOnDate(String tenantId, LocalDate date);

  List<TrendSignal> topSignalsForTenantOnDate(String tenantId, LocalDate date, int limit);
}
