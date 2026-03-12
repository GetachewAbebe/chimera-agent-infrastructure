package org.chimera.perception;

public interface SemanticRelevanceScorer {
  double score(String activeGoal, String candidateText);
}
