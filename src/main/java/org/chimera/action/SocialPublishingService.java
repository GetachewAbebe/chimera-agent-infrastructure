package org.chimera.action;

import java.util.List;
import java.util.Map;
import org.chimera.mcp.McpToolClient;
import org.chimera.mcp.McpToolResult;

public final class SocialPublishingService {
  private final McpToolClient toolClient;

  public SocialPublishingService(McpToolClient toolClient) {
    if (toolClient == null) {
      throw new IllegalArgumentException("toolClient is required");
    }
    this.toolClient = toolClient;
  }

  public SocialActionReceipt publishPost(SocialPostRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("request is required");
    }
    validateText(request.textContent());

    DisclosureLevel disclosure = enforceDisclosure(request.disclosureLevel());
    List<String> media = request.mediaUrls() == null ? List.of() : request.mediaUrls();

    String toolName =
        switch (request.platform()) {
          case TWITTER -> "twitter.post_tweet";
          case INSTAGRAM -> "instagram.publish_media";
          case THREADS -> "threads.publish_post";
        };

    McpToolResult result =
        toolClient.callTool(
            toolName,
            Map.of(
                "platform", request.platform().name().toLowerCase(),
                "text_content", request.textContent().trim(),
                "media_urls", media,
                "disclosure_level", disclosure.name().toLowerCase()));
    return toReceipt(toolName, result);
  }

  public SocialActionReceipt replyToInteraction(SocialReplyRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("request is required");
    }
    if (request.conversationId() == null || request.conversationId().isBlank()) {
      throw new IllegalArgumentException("conversationId must not be blank");
    }
    validateText(request.textContent());

    DisclosureLevel disclosure = enforceDisclosure(request.disclosureLevel());

    String toolName =
        switch (request.platform()) {
          case TWITTER -> "twitter.reply_tweet";
          case INSTAGRAM -> "instagram.reply_comment";
          case THREADS -> "threads.reply_post";
        };

    McpToolResult result =
        toolClient.callTool(
            toolName,
            Map.of(
                "platform", request.platform().name().toLowerCase(),
                "conversation_id", request.conversationId().trim(),
                "text_content", request.textContent().trim(),
                "disclosure_level", disclosure.name().toLowerCase()));
    return toReceipt(toolName, result);
  }

  private static SocialActionReceipt toReceipt(String toolName, McpToolResult result) {
    if (result == null) {
      throw new IllegalStateException("MCP tool result must not be null");
    }
    String externalId = stringValue(result.payload().get("external_id"));
    String message = result.message() == null ? "" : result.message();
    return new SocialActionReceipt(result.success(), toolName, externalId, message);
  }

  private static void validateText(String text) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("textContent must not be blank");
    }
  }

  private static DisclosureLevel enforceDisclosure(DisclosureLevel requested) {
    if (requested == null || requested == DisclosureLevel.NONE) {
      return DisclosureLevel.AUTOMATED;
    }
    return requested;
  }

  private static String stringValue(Object value) {
    if (value == null) {
      return "";
    }
    return String.valueOf(value);
  }
}
