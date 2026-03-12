package org.chimera.tests.creative;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.chimera.creative.CreativeEngineService;
import org.chimera.mcp.McpToolClient;
import org.chimera.mcp.McpToolResult;
import org.chimera.model.TaskContext;
import org.junit.jupiter.api.Test;

class CreativeEngineServiceTest {

  @Test
  void shouldComposeTextAndMediaAndPassConsistencyLock() {
    List<String> calledTools = new ArrayList<>();
    McpToolClient toolClient =
        (toolName, arguments) -> {
          calledTools.add(toolName);
          return switch (toolName) {
            case "creative.generate_text" ->
                new McpToolResult(
                    true,
                    "text-ok",
                    Map.of("text_content", "Launch the eco capsule drop tonight."));
            case "creative.generate_image" ->
                new McpToolResult(
                    true, "image-ok", Map.of("image_url", "https://cdn.example.com/image-1.png"));
            case "creative.generate_video" ->
                new McpToolResult(
                    true, "video-ok", Map.of("video_url", "https://cdn.example.com/video-1.mp4"));
            case "creative.check_consistency" ->
                new McpToolResult(
                    true,
                    "consistency-ok",
                    Map.of("is_consistent", true, "consistency_score", 0.92));
            default -> throw new IllegalArgumentException("Unexpected tool: " + toolName);
          };
        };

    CreativeEngineService service = new CreativeEngineService(toolClient);

    var composition =
        service.compose(
            "worker-alpha",
            new TaskContext(
                "Launch sustainable fashion campaign",
                List.of("Respect persona directives"),
                List.of("news://ethiopia/fashion/trends")));

    assertThat(calledTools)
        .containsExactly(
            "creative.generate_text",
            "creative.generate_image",
            "creative.generate_video",
            "creative.check_consistency");
    assertThat(composition.textContent()).contains("eco capsule");
    assertThat(composition.mediaUrls())
        .containsExactly(
            "https://cdn.example.com/image-1.png", "https://cdn.example.com/video-1.mp4");
    assertThat(composition.consistencyPassed()).isTrue();
    assertThat(composition.consistencyScore()).isGreaterThanOrEqualTo(0.9);
    assertThat(composition.trace()).contains("creative_consistency_passed=true");
  }

  @Test
  void shouldFailConsistencyLockWhenScoreBelowThreshold() {
    McpToolClient toolClient =
        (toolName, arguments) -> {
          return switch (toolName) {
            case "creative.generate_text" ->
                new McpToolResult(true, "text-ok", Map.of("text_content", "Drop incoming"));
            case "creative.generate_image" ->
                new McpToolResult(
                    true, "image-ok", Map.of("image_url", "https://cdn.example.com/image-2.png"));
            case "creative.generate_video" ->
                new McpToolResult(
                    true, "video-ok", Map.of("video_url", "https://cdn.example.com/video-2.mp4"));
            case "creative.check_consistency" ->
                new McpToolResult(
                    true, "low-score", Map.of("is_consistent", true, "consistency_score", 0.61));
            default -> throw new IllegalArgumentException("Unexpected tool: " + toolName);
          };
        };

    CreativeEngineService service = new CreativeEngineService(toolClient);

    var composition =
        service.compose(
            "worker-alpha",
            new TaskContext(
                "Launch sustainable fashion campaign",
                List.of("Respect persona directives"),
                List.of("news://ethiopia/fashion/trends")));

    assertThat(composition.consistencyPassed()).isFalse();
    assertThat(composition.consistencyScore()).isLessThan(0.78);
    assertThat(composition.trace()).contains("creative_consistency_passed=false");
  }
}
