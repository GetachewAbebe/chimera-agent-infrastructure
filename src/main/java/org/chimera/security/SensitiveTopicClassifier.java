package org.chimera.security;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SensitiveTopicClassifier {
  private static final List<Pattern> SENSITIVE_PATTERNS =
      List.of(
          Pattern.compile("\\bpolitic(s|al)?\\b"),
          Pattern.compile("\\belection(s)?\\b"),
          Pattern.compile("\\bhealth\\b"),
          Pattern.compile("\\bmedical\\b"),
          Pattern.compile("\\bprescription\\b"),
          Pattern.compile("\\bfinance\\b"),
          Pattern.compile("\\bfinancial\\b"),
          Pattern.compile("\\binvest(ment|ing)?\\b"),
          Pattern.compile("\\blegal\\b"),
          Pattern.compile("\\blawsuit\\b"),
          Pattern.compile("\\btax(es)?\\b"));

  public boolean containsSensitiveTopic(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }
    String normalized = text.toLowerCase(Locale.ROOT);
    return SENSITIVE_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).find());
  }
}
