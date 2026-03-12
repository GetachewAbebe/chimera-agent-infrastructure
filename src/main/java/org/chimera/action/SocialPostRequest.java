package org.chimera.action;

import java.util.List;

public record SocialPostRequest(
    SocialPlatform platform,
    String textContent,
    List<String> mediaUrls,
    DisclosureLevel disclosureLevel) {
  public SocialPostRequest {
    mediaUrls = mediaUrls == null ? List.of() : List.copyOf(mediaUrls);
  }
}
