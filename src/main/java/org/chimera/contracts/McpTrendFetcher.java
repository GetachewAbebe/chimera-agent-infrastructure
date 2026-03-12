package org.chimera.contracts;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.chimera.model.TrendSignal;

public final class McpTrendFetcher implements TrendFetcher {
  @Override
  public List<TrendSignal> fetchTopTrends(String niche, int limit) {
    if (niche == null || niche.isBlank()) {
      throw new IllegalArgumentException("niche must not be blank");
    }
    if (limit < 1) {
      throw new IllegalArgumentException("limit must be >= 1");
    }

    // Placeholder for MCP resource reads. This keeps the contract executable until server wiring.
    List<TrendSignal> allSignals =
        List.of(
            new TrendSignal(
                "addis-fashion-week", 0.95, "news://ethiopia/fashion/trends", Instant.now()),
            new TrendSignal(
                "eco-streetwear", 0.91, "news://ethiopia/fashion/trends", Instant.now()),
            new TrendSignal("creator-collab", 0.88, "twitter://mentions/recent", Instant.now()),
            new TrendSignal(
                "ethical-textile", 0.86, "news://ethiopia/fashion/trends", Instant.now()),
            new TrendSignal(
                "gen-z-sneaker-drop", 0.83, "twitter://mentions/recent", Instant.now()));

    List<TrendSignal> nicheFiltered =
        allSignals.stream()
            .filter(
                signal ->
                    signal.topic().contains(niche.toLowerCase())
                        || "fashion".equalsIgnoreCase(niche))
            .limit(limit)
            .toList();

    if (!nicheFiltered.isEmpty()) {
      return nicheFiltered;
    }

    List<TrendSignal> fallback = new ArrayList<>();
    fallback.add(
        new TrendSignal(
            niche.toLowerCase() + "-emerging-topic",
            0.72,
            "news://global/" + niche,
            Instant.now()));
    return fallback;
  }
}
