package org.chimera.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.chimera.model.TrendSignal;

public final class InMemoryTrendSignalRepository implements TrendSignalRepository {
  private final List<TenantTrendSignal> signals = new ArrayList<>();

  @Override
  public void append(String tenantId, TrendSignal signal) {
    validateTenant(tenantId);
    if (signal == null) {
      throw new IllegalArgumentException("signal is required");
    }
    synchronized (signals) {
      signals.add(new TenantTrendSignal(tenantId, signal));
    }
  }

  @Override
  public int countForTenantOnDate(String tenantId, LocalDate date) {
    validateTenant(tenantId);
    if (date == null) {
      throw new IllegalArgumentException("date is required");
    }

    DateRange dateRange = utcDateRange(date);
    synchronized (signals) {
      int count = 0;
      for (TenantTrendSignal tenantSignal : signals) {
        if (!tenantSignal.tenantId().equals(tenantId)) {
          continue;
        }

        Instant observedAt = tenantSignal.signal().observedAt();
        if (observedAt == null) {
          continue;
        }
        if (!observedAt.isBefore(dateRange.startInclusive())
            && observedAt.isBefore(dateRange.endExclusive())) {
          count++;
        }
      }
      return count;
    }
  }

  @Override
  public List<TrendSignal> topSignalsForTenantOnDate(String tenantId, LocalDate date, int limit) {
    validateTenant(tenantId);
    if (date == null) {
      throw new IllegalArgumentException("date is required");
    }
    if (limit < 1) {
      throw new IllegalArgumentException("limit must be >= 1");
    }

    DateRange dateRange = utcDateRange(date);
    synchronized (signals) {
      return signals.stream()
          .filter(tenantSignal -> tenantSignal.tenantId().equals(tenantId))
          .map(TenantTrendSignal::signal)
          .filter(signal -> signal.observedAt() != null)
          .filter(
              signal ->
                  !signal.observedAt().isBefore(dateRange.startInclusive())
                      && signal.observedAt().isBefore(dateRange.endExclusive()))
          .sorted(
              Comparator.comparingDouble(TrendSignal::score)
                  .reversed()
                  .thenComparing(
                      TrendSignal::observedAt, Comparator.nullsLast(Comparator.reverseOrder())))
          .limit(limit)
          .map(InMemoryTrendSignalRepository::copySignal)
          .toList();
    }
  }

  private static TrendSignal copySignal(TrendSignal signal) {
    return new TrendSignal(signal.topic(), signal.score(), signal.source(), signal.observedAt());
  }

  private static void validateTenant(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId must not be blank");
    }
  }

  private static DateRange utcDateRange(LocalDate date) {
    Instant startInclusive = date.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant endExclusive = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    return new DateRange(startInclusive, endExclusive);
  }

  private record DateRange(Instant startInclusive, Instant endExclusive) {}

  private record TenantTrendSignal(String tenantId, TrendSignal signal) {
    private TenantTrendSignal {
      Objects.requireNonNull(tenantId, "tenantId");
      Objects.requireNonNull(signal, "signal");
    }
  }
}
