package org.chimera.skills;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class TranscribeAudioSkill
    implements AgentSkill<TranscribeAudioInput, TranscribeAudioOutput> {
  @Override
  public String name() {
    return "skill_transcribe_audio";
  }

  @Override
  public TranscribeAudioOutput execute(TranscribeAudioInput input) {
    if (input == null) {
      throw new IllegalArgumentException("input must not be null");
    }
    if (input.audioPath() == null || input.audioPath().isBlank()) {
      throw new IllegalArgumentException("audioPath is required");
    }
    String language = normalizeLanguage(input.language());
    String fileName = fileName(input.audioPath());
    enforceSupportedExtension(fileName);

    String transcript =
        "Transcribed "
            + fileName
            + " in "
            + language
            + " using deterministic baseline model output.";
    TranscriptSegment segment = new TranscriptSegment(0, 1200, transcript);
    double confidence = input.diarization() ? 0.89 : 0.92;
    return new TranscribeAudioOutput(transcript, List.of(segment), confidence);
  }

  private static String normalizeLanguage(String language) {
    String normalized = language == null ? "en" : language.trim().toLowerCase(Locale.ROOT);
    if (normalized.isBlank()) {
      return "en";
    }
    if (!normalized.matches("^[a-z]{2}(-[a-z]{2})?$")) {
      throw new IllegalArgumentException("language must be an ISO-like tag (example: en, en-us)");
    }
    return normalized;
  }

  private static String fileName(String audioPath) {
    try {
      Path path = Path.of(audioPath.trim());
      Path fileName = path.getFileName();
      if (fileName == null) {
        throw new IllegalArgumentException("audioPath must include a file name");
      }
      return fileName.toString();
    } catch (Exception ex) {
      throw new IllegalArgumentException("audioPath format is invalid", ex);
    }
  }

  private static void enforceSupportedExtension(String fileName) {
    String lower = fileName.toLowerCase(Locale.ROOT);
    if (!(lower.endsWith(".mp3")
        || lower.endsWith(".wav")
        || lower.endsWith(".m4a")
        || lower.endsWith(".ogg"))) {
      throw new IllegalArgumentException("Unsupported audio format. Supported: mp3, wav, m4a, ogg");
    }
  }
}
