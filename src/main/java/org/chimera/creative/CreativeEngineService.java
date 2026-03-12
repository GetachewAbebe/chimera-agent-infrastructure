package org.chimera.creative;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.chimera.mcp.McpToolClient;
import org.chimera.mcp.McpToolResult;
import org.chimera.model.TaskContext;

public final class CreativeEngineService {
  private static final double CONSISTENCY_LOCK_MIN_SCORE = 0.78;

  private final McpToolClient toolClient;

  public CreativeEngineService(McpToolClient toolClient) {
    if (toolClient == null) {
      throw new IllegalArgumentException("toolClient is required");
    }
    this.toolClient = toolClient;
  }

  public CreativeComposition compose(String workerId, TaskContext context) {
    if (workerId == null || workerId.isBlank()) {
      throw new IllegalArgumentException("workerId must not be blank");
    }
    if (context == null) {
      throw new IllegalArgumentException("context is required");
    }

    String goal = normalizeGoal(context.goalDescription());
    List<String> personaConstraints = safeList(context.personaConstraints());
    List<String> resources = safeList(context.requiredResources());

    McpToolResult textResult =
        toolClient.callTool(
            "creative.generate_text",
            Map.of(
                "worker_id", workerId,
                "goal_description", goal,
                "persona_constraints", personaConstraints,
                "required_resources", resources));
    String textContent =
        firstNonBlank(
            stringValue(textResult.payload().get("text_content")),
            stringValue(textResult.payload().get("caption")),
            fallbackCaption(goal));

    McpToolResult imageResult =
        toolClient.callTool(
            "creative.generate_image",
            Map.of(
                "worker_id", workerId,
                "prompt", textContent,
                "goal_description", goal,
                "persona_constraints", personaConstraints));
    String imageUrl =
        firstNonBlank(
            stringValue(imageResult.payload().get("image_url")),
            stringValue(imageResult.payload().get("media_url")),
            stringValue(imageResult.payload().get("asset_url")),
            "");

    McpToolResult videoResult =
        toolClient.callTool(
            "creative.generate_video",
            Map.of(
                "worker_id", workerId,
                "script", textContent,
                "goal_description", goal,
                "image_url", imageUrl,
                "required_resources", resources));
    String videoUrl =
        firstNonBlank(
            stringValue(videoResult.payload().get("video_url")),
            stringValue(videoResult.payload().get("media_url")),
            stringValue(videoResult.payload().get("asset_url")),
            "");

    List<String> mediaUrls = new ArrayList<>();
    if (!imageUrl.isBlank()) {
      mediaUrls.add(imageUrl);
    }
    if (!videoUrl.isBlank()) {
      mediaUrls.add(videoUrl);
    }

    Map<String, Object> consistencyArgs = new HashMap<>();
    consistencyArgs.put("worker_id", workerId);
    consistencyArgs.put("goal_description", goal);
    consistencyArgs.put("text_content", textContent);
    consistencyArgs.put("media_urls", List.copyOf(mediaUrls));
    consistencyArgs.put("persona_constraints", personaConstraints);
    consistencyArgs.put("required_resources", resources);

    McpToolResult consistencyResult =
        toolClient.callTool("creative.check_consistency", consistencyArgs);
    double consistencyScore =
        doubleValue(
            consistencyResult.payload().get("consistency_score"),
            consistencyResult.success() ? 0.86 : 0.45);
    boolean consistencyPassed =
        booleanValue(consistencyResult.payload().get("is_consistent"), consistencyResult.success());
    if (consistencyScore < CONSISTENCY_LOCK_MIN_SCORE) {
      consistencyPassed = false;
    }

    String trace =
        "creative_tools="
            + "text:image:video:consistency"
            + "; creative_consistency_passed="
            + consistencyPassed
            + "; creative_consistency_score="
            + String.format(Locale.ROOT, "%.2f", consistencyScore)
            + "; text_tool_message="
            + sanitize(textResult.message())
            + "; consistency_tool_message="
            + sanitize(consistencyResult.message());

    return new CreativeComposition(
        textContent, List.copyOf(mediaUrls), consistencyPassed, consistencyScore, trace);
  }

  private static String normalizeGoal(String goal) {
    if (goal == null || goal.isBlank()) {
      return "Create campaign content with clear AI disclosure.";
    }
    return goal.trim();
  }

  private static List<String> safeList(List<String> values) {
    return values == null ? List.of() : List.copyOf(values);
  }

  private static String fallbackCaption(String goal) {
    String normalized = goal == null ? "" : goal.trim();
    if (normalized.length() > 220) {
      normalized = normalized.substring(0, 220);
    }
    return normalized + " #AIAssisted";
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value.trim();
      }
    }
    return "";
  }

  private static String stringValue(Object value) {
    if (value == null) {
      return "";
    }
    return String.valueOf(value);
  }

  private static double doubleValue(Object raw, double fallback) {
    if (raw instanceof Number number) {
      return number.doubleValue();
    }
    if (raw instanceof String text && !text.isBlank()) {
      try {
        return Double.parseDouble(text.trim());
      } catch (NumberFormatException ignored) {
        return fallback;
      }
    }
    return fallback;
  }

  private static boolean booleanValue(Object raw, boolean fallback) {
    if (raw instanceof Boolean bool) {
      return bool;
    }
    if (raw instanceof String text && !text.isBlank()) {
      return Boolean.parseBoolean(text.trim());
    }
    return fallback;
  }

  private static String sanitize(String text) {
    if (text == null || text.isBlank()) {
      return "none";
    }
    return text.replace(';', ',').trim();
  }
}
