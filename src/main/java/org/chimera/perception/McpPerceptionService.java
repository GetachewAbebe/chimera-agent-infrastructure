package org.chimera.perception;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.chimera.mcp.McpResourceClient;
import org.chimera.model.TrendSignal;

public final class McpPerceptionService {
  private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

  private final McpResourceClient resourceClient;
  private final SemanticRelevanceScorer relevanceScorer;

  public McpPerceptionService(McpResourceClient resourceClient, SemanticRelevanceScorer scorer) {
    if (resourceClient == null) {
      throw new IllegalArgumentException("resourceClient is required");
    }
    if (scorer == null) {
      throw new IllegalArgumentException("scorer is required");
    }
    this.resourceClient = resourceClient;
    this.relevanceScorer = scorer;
  }

  public List<TrendSignal> pollRelevantSignals(
      List<String> resourceUris, String activeGoal, double threshold, int limit) {
    if (resourceUris == null || resourceUris.isEmpty()) {
      throw new IllegalArgumentException("resourceUris must not be empty");
    }
    if (threshold < 0.0 || threshold > 1.0) {
      throw new IllegalArgumentException("threshold must be between 0.0 and 1.0");
    }
    if (limit < 1) {
      throw new IllegalArgumentException("limit must be >= 1");
    }
    if (activeGoal == null || activeGoal.isBlank()) {
      throw new IllegalArgumentException("activeGoal must not be blank");
    }

    Map<String, TrendSignal> bestByTopic = new HashMap<>();
    for (String resourceUri : resourceUris) {
      if (resourceUri == null || resourceUri.isBlank()) {
        continue;
      }

      String payload = resourceClient.readResource(resourceUri);
      for (String candidate : splitCandidates(payload)) {
        double relevance = relevanceScorer.score(activeGoal, candidate);
        if (relevance < threshold) {
          continue;
        }

        TrendSignal signal =
            new TrendSignal(toTopicSlug(candidate), relevance, resourceUri, Instant.now());
        TrendSignal current = bestByTopic.get(signal.topic());
        if (current == null || signal.score() > current.score()) {
          bestByTopic.put(signal.topic(), signal);
        }
      }
    }

    List<TrendSignal> ranked = new ArrayList<>(bestByTopic.values());
    ranked.sort(Comparator.comparingDouble(TrendSignal::score).reversed());
    if (ranked.size() <= limit) {
      return ranked;
    }
    return ranked.subList(0, limit);
  }

  private static List<String> splitCandidates(String payload) {
    if (payload == null || payload.isBlank()) {
      return List.of();
    }

    List<String> candidates = new ArrayList<>();
    for (String line : payload.split("\\R")) {
      String normalized = line.trim();
      if (!normalized.isEmpty()) {
        candidates.add(normalized);
      }
    }
    return candidates;
  }

  private static String toTopicSlug(String candidate) {
    String lower = candidate.toLowerCase(Locale.ROOT).trim();
    String normalized = NON_ALPHANUMERIC.matcher(lower).replaceAll("-");
    normalized = normalized.replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
    if (normalized.isBlank()) {
      return "unknown-topic";
    }
    if (normalized.length() <= 64) {
      return normalized;
    }
    return normalized.substring(0, 64).replaceAll("-+$", "");
  }
}
