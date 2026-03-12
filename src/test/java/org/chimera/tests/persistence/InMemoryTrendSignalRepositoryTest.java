package org.chimera.tests.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.chimera.model.TrendSignal;
import org.chimera.persistence.InMemoryTrendSignalRepository;
import org.junit.jupiter.api.Test;

class InMemoryTrendSignalRepositoryTest {

  @Test
  void shouldAppendAndQuerySignalsByDateAndTenant() {
    InMemoryTrendSignalRepository repository = new InMemoryTrendSignalRepository();

    repository.append(
        "tenant-alpha",
        new TrendSignal(
            "streetwear-collab",
            0.91,
            "news://ethiopia/fashion/trends",
            Instant.parse("2026-03-12T08:00:00Z")));
    repository.append(
        "tenant-alpha",
        new TrendSignal(
            "sustainable-capsule",
            0.85,
            "twitter://mentions/recent",
            Instant.parse("2026-03-12T08:10:00Z")));
    repository.append(
        "tenant-beta",
        new TrendSignal(
            "other-tenant-topic", 0.95, "news://other", Instant.parse("2026-03-12T09:00:00Z")));
    repository.append(
        "tenant-alpha",
        new TrendSignal(
            "previous-day", 0.99, "news://previous", Instant.parse("2026-03-11T23:59:59Z")));

    assertThat(repository.countForTenantOnDate("tenant-alpha", LocalDate.of(2026, 3, 12)))
        .isEqualTo(2);

    List<TrendSignal> topSignals =
        repository.topSignalsForTenantOnDate("tenant-alpha", LocalDate.of(2026, 3, 12), 2);
    assertThat(topSignals).hasSize(2);
    assertThat(topSignals.getFirst().topic()).isEqualTo("streetwear-collab");
    assertThat(topSignals).extracting(TrendSignal::topic).doesNotContain("previous-day");
  }
}
