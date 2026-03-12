package org.chimera.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.chimera.contracts.McpTrendFetcher;
import org.chimera.contracts.TrendFetcher;
import org.chimera.model.TrendSignal;
import org.junit.jupiter.api.Test;

class trendFetcherTest {

  @Test
  void trendSignalContractShouldExposeRequiredFields() {
    TrendSignal signal =
        new TrendSignal(
            "addis-fashion-week", 0.95, "news://ethiopia/fashion/trends", java.time.Instant.now());

    assertThat(signal.topic()).isNotBlank();
    assertThat(signal.score()).isBetween(0.0, 1.0);
    assertThat(signal.source()).startsWith("news://");
    assertThat(signal.observedAt()).isNotNull();
  }

  @Test
  void shouldReturnAtLeastOneTrendForConfiguredNiche() {
    TrendFetcher fetcher = new McpTrendFetcher();
    List<TrendSignal> trends = fetcher.fetchTopTrends("fashion", 5);

    assertThat(trends).isNotEmpty();
    assertThat(trends).hasSizeLessThanOrEqualTo(5);
    assertThat(trends).allMatch(signal -> signal.score() >= 0.0 && signal.score() <= 1.0);
  }
}
