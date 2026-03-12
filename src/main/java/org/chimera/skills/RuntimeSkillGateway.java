package org.chimera.skills;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RuntimeSkillGateway implements SkillGateway {
  private final BudgetAwareSkillGateway budgetGuard;
  private final DownloadYoutubeSkill downloadYoutubeSkill;
  private final TranscribeAudioSkill transcribeAudioSkill;

  public RuntimeSkillGateway(BigDecimal dailyLimitUsd) {
    this(
        new BudgetAwareSkillGateway(dailyLimitUsd),
        new DownloadYoutubeSkill(),
        new TranscribeAudioSkill());
  }

  RuntimeSkillGateway(
      BudgetAwareSkillGateway budgetGuard,
      DownloadYoutubeSkill downloadYoutubeSkill,
      TranscribeAudioSkill transcribeAudioSkill) {
    if (budgetGuard == null) {
      throw new IllegalArgumentException("budgetGuard is required");
    }
    if (downloadYoutubeSkill == null) {
      throw new IllegalArgumentException("downloadYoutubeSkill is required");
    }
    if (transcribeAudioSkill == null) {
      throw new IllegalArgumentException("transcribeAudioSkill is required");
    }
    this.budgetGuard = budgetGuard;
    this.downloadYoutubeSkill = downloadYoutubeSkill;
    this.transcribeAudioSkill = transcribeAudioSkill;
  }

  @Override
  public SkillResponse execute(SkillRequest request) {
    budgetGuard.execute(request);

    return switch (request.skillName()) {
      case "skill_download_youtube" -> executeDownloadYoutube(request.input());
      case "skill_transcribe_audio" -> executeTranscribeAudio(request.input());
      default -> throw new IllegalArgumentException("Unknown skill: " + request.skillName());
    };
  }

  private SkillResponse executeDownloadYoutube(Map<String, Object> input) {
    DownloadYoutubeInput parsed =
        new DownloadYoutubeInput(
            requiredString(input, "url"),
            readInt(input, "max_duration_seconds", 900),
            readString(input, "preferred_format", "mp4"));
    DownloadYoutubeOutput output = downloadYoutubeSkill.execute(parsed);
    return new SkillResponse(
        true,
        Map.of(
            "video_id",
            output.videoId(),
            "local_path",
            output.localPath(),
            "duration_seconds",
            output.durationSeconds(),
            "checksum_sha256",
            output.checksumSha256()));
  }

  private SkillResponse executeTranscribeAudio(Map<String, Object> input) {
    TranscribeAudioInput parsed =
        new TranscribeAudioInput(
            requiredString(input, "audio_path"),
            readString(input, "language", "en"),
            readBoolean(input, "diarization", false));
    TranscribeAudioOutput output = transcribeAudioSkill.execute(parsed);
    List<Map<String, Object>> segments =
        output.segments().stream()
            .map(
                segment -> {
                  Map<String, Object> segmentMap = new LinkedHashMap<>();
                  segmentMap.put("start_ms", segment.startMs());
                  segmentMap.put("end_ms", segment.endMs());
                  segmentMap.put("text", segment.text());
                  return segmentMap;
                })
            .toList();

    return new SkillResponse(
        true,
        Map.of(
            "transcript",
            output.transcript(),
            "segments",
            segments,
            "confidence",
            output.confidence()));
  }

  private static String requiredString(Map<String, Object> input, String key) {
    String value = readString(input, key, null);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(key + " is required");
    }
    return value;
  }

  private static String readString(Map<String, Object> input, String key, String fallback) {
    if (input == null || !input.containsKey(key) || input.get(key) == null) {
      return fallback;
    }
    String value = String.valueOf(input.get(key)).trim();
    return value.isBlank() ? fallback : value;
  }

  private static int readInt(Map<String, Object> input, String key, int fallback) {
    if (input == null || !input.containsKey(key) || input.get(key) == null) {
      return fallback;
    }
    Object value = input.get(key);
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(value).trim());
    } catch (Exception ex) {
      throw new IllegalArgumentException(key + " must be an integer");
    }
  }

  private static boolean readBoolean(Map<String, Object> input, String key, boolean fallback) {
    if (input == null || !input.containsKey(key) || input.get(key) == null) {
      return fallback;
    }
    Object value = input.get(key);
    if (value instanceof Boolean bool) {
      return bool;
    }
    String raw = String.valueOf(value).trim();
    if ("true".equalsIgnoreCase(raw)) {
      return true;
    }
    if ("false".equalsIgnoreCase(raw)) {
      return false;
    }
    throw new IllegalArgumentException(key + " must be true or false");
  }
}
