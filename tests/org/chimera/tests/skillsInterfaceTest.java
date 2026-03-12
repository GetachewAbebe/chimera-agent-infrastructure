package org.chimera.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import org.chimera.skills.BudgetAwareSkillGateway;
import org.chimera.skills.BudgetExceededException;
import org.chimera.skills.RuntimeSkillGateway;
import org.chimera.skills.SkillGateway;
import org.chimera.skills.SkillRequest;
import org.junit.jupiter.api.Test;

class skillsInterfaceTest {

  @Test
  void shouldThrowBudgetExceededWhenProjectedSpendBreaksDailyCap() {
    SkillGateway gateway = new BudgetAwareSkillGateway(new BigDecimal("50.00"));

    SkillRequest request =
        new SkillRequest(
            "skill_download_youtube",
            "agent-001",
            Map.of("url", "https://youtube.com/watch?v=example"),
            new BigDecimal("125.00"));

    assertThatThrownBy(() -> gateway.execute(request)).isInstanceOf(BudgetExceededException.class);
  }

  @Test
  void shouldAcceptSkillRequestWhenProjectedSpendWithinLimit() {
    SkillGateway gateway = new BudgetAwareSkillGateway(new BigDecimal("50.00"));

    SkillRequest request =
        new SkillRequest(
            "skill_transcribe_audio",
            "agent-001",
            Map.of("audio_path", "/tmp/source.mp3"),
            new BigDecimal("12.50"));

    assertThat(gateway.execute(request).accepted()).isTrue();
  }

  @Test
  void shouldExecuteYoutubeDownloadSkillWithContractFields() {
    SkillGateway gateway = new RuntimeSkillGateway(new BigDecimal("500.00"));

    SkillRequest request =
        new SkillRequest(
            "skill_download_youtube",
            "agent-001",
            Map.of(
                "url", "https://youtube.com/watch?v=abc123XYz",
                "max_duration_seconds", 600,
                "preferred_format", "mp4"),
            new BigDecimal("2.50"));

    var response = gateway.execute(request);

    assertThat(response.accepted()).isTrue();
    assertThat(response.output())
        .containsKeys("video_id", "local_path", "duration_seconds", "checksum_sha256");
    assertThat(response.output().get("video_id")).isEqualTo("abc123XYz");
    assertThat(response.output().get("duration_seconds")).isEqualTo(600);
  }

  @Test
  void shouldExecuteAudioTranscriptionSkillWithContractFields() {
    SkillGateway gateway = new RuntimeSkillGateway(new BigDecimal("500.00"));

    SkillRequest request =
        new SkillRequest(
            "skill_transcribe_audio",
            "agent-001",
            Map.of(
                "audio_path", "/tmp/chimera/source.mp3",
                "language", "en",
                "diarization", false),
            new BigDecimal("1.25"));

    var response = gateway.execute(request);

    assertThat(response.accepted()).isTrue();
    assertThat(response.output()).containsKeys("transcript", "segments", "confidence");
    assertThat(response.output().get("transcript")).asString().contains("source.mp3");
    assertThat(response.output().get("segments")).isInstanceOfAny(java.util.List.class);
  }
}
