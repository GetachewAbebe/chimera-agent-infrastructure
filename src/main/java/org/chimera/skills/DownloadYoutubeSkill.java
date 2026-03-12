package org.chimera.skills;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class DownloadYoutubeSkill
    implements AgentSkill<DownloadYoutubeInput, DownloadYoutubeOutput> {
  private static final int HARD_MAX_DURATION_SECONDS = 900;

  @Override
  public String name() {
    return "skill_download_youtube";
  }

  @Override
  public DownloadYoutubeOutput execute(DownloadYoutubeInput input) {
    if (input == null) {
      throw new IllegalArgumentException("input must not be null");
    }
    if (input.url() == null || input.url().isBlank()) {
      throw new IllegalArgumentException("url is required");
    }
    if (input.maxDurationSeconds() < 1 || input.maxDurationSeconds() > HARD_MAX_DURATION_SECONDS) {
      throw new IllegalArgumentException(
          "maxDurationSeconds must be between 1 and " + HARD_MAX_DURATION_SECONDS);
    }
    if (input.preferredFormat() == null || input.preferredFormat().isBlank()) {
      throw new IllegalArgumentException("preferredFormat is required");
    }

    String format = normalizeFormat(input.preferredFormat());
    String videoId = extractVideoId(input.url());
    String localPath = "/tmp/chimera/" + videoId + "." + format;
    String checksum = sha256Hex(input.url() + "|" + format + "|" + input.maxDurationSeconds());

    return new DownloadYoutubeOutput(videoId, localPath, input.maxDurationSeconds(), checksum);
  }

  private static String normalizeFormat(String preferredFormat) {
    String normalized = preferredFormat.trim().toLowerCase(Locale.ROOT);
    if (!"mp4".equals(normalized) && !"webm".equals(normalized)) {
      throw new IllegalArgumentException("preferredFormat must be one of: mp4, webm");
    }
    return normalized;
  }

  private static String extractVideoId(String rawUrl) {
    try {
      URI uri = URI.create(rawUrl.trim());
      String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
      if (!(host.contains("youtube.com") || host.contains("youtu.be"))) {
        throw new IllegalArgumentException("Only youtube.com or youtu.be URLs are supported");
      }

      if (host.contains("youtu.be")) {
        String path = uri.getPath();
        if (path == null || path.isBlank() || "/".equals(path.trim())) {
          throw new IllegalArgumentException("Unable to parse YouTube video id");
        }
        return sanitizeVideoId(path.replaceFirst("^/+", ""));
      }

      String query = uri.getQuery();
      if (query != null) {
        for (String part : query.split("&")) {
          String[] keyValue = part.split("=", 2);
          if (keyValue.length == 2 && "v".equals(keyValue[0])) {
            return sanitizeVideoId(keyValue[1]);
          }
        }
      }

      throw new IllegalArgumentException("Unable to parse YouTube video id");
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("Invalid url format", ex);
    }
  }

  private static String sanitizeVideoId(String rawVideoId) {
    String trimmed = rawVideoId == null ? "" : rawVideoId.trim();
    if (!trimmed.matches("^[A-Za-z0-9_-]{6,64}$")) {
      throw new IllegalArgumentException("Invalid YouTube video id format");
    }
    return trimmed;
  }

  private static String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 digest not available", ex);
    }
  }
}
