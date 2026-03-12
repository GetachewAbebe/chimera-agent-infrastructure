package org.chimera.creative;

import java.util.List;

public record CreativeComposition(
    String textContent,
    List<String> mediaUrls,
    boolean consistencyPassed,
    double consistencyScore,
    String trace) {
  public CreativeComposition {
    textContent = textContent == null ? "" : textContent;
    mediaUrls = mediaUrls == null ? List.of() : List.copyOf(mediaUrls);
    trace = trace == null ? "" : trace;
  }
}
