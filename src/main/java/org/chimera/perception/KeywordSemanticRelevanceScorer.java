package org.chimera.perception;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class KeywordSemanticRelevanceScorer implements SemanticRelevanceScorer {
  @Override
  public double score(String activeGoal, String candidateText) {
    Set<String> goalTokens = tokenize(activeGoal);
    Set<String> candidateTokens = tokenize(candidateText);
    if (goalTokens.isEmpty() || candidateTokens.isEmpty()) {
      return 0.0;
    }

    int overlap = 0;
    for (String token : goalTokens) {
      if (candidateTokens.contains(token)) {
        overlap += 1;
      }
    }

    double overlapScore = (double) overlap / (double) goalTokens.size();
    if (overlapScore > 1.0) {
      return 1.0;
    }
    return overlapScore;
  }

  private static Set<String> tokenize(String text) {
    if (text == null || text.isBlank()) {
      return Set.of();
    }

    Set<String> tokens = new HashSet<>();
    Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
        .map(String::trim)
        .filter(token -> token.length() > 2)
        .forEach(tokens::add);
    return tokens;
  }
}
