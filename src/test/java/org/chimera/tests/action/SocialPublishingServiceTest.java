package org.chimera.tests.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.chimera.action.DisclosureLevel;
import org.chimera.action.SocialActionReceipt;
import org.chimera.action.SocialPlatform;
import org.chimera.action.SocialPostRequest;
import org.chimera.action.SocialPublishingService;
import org.chimera.action.SocialReplyRequest;
import org.chimera.mcp.McpToolClient;
import org.chimera.mcp.McpToolResult;
import org.junit.jupiter.api.Test;

class SocialPublishingServiceTest {

  @Test
  void shouldUsePlatformSpecificToolForPublish() {
    RecordingToolClient toolClient = new RecordingToolClient();
    SocialPublishingService service = new SocialPublishingService(toolClient);

    SocialActionReceipt receipt =
        service.publishPost(
            new SocialPostRequest(
                SocialPlatform.TWITTER,
                "New sneaker drop is live.",
                java.util.List.of("https://cdn.example.com/drop.jpg"),
                DisclosureLevel.AUTOMATED));

    assertThat(receipt.success()).isTrue();
    assertThat(receipt.toolName()).isEqualTo("twitter.post_tweet");
    assertThat(receipt.externalId()).isEqualTo("external-123");
    assertThat(toolClient.lastToolName).isEqualTo("twitter.post_tweet");
    assertThat(toolClient.lastArguments).containsEntry("disclosure_level", "automated");
  }

  @Test
  void shouldUsePlatformSpecificToolForReply() {
    RecordingToolClient toolClient = new RecordingToolClient();
    SocialPublishingService service = new SocialPublishingService(toolClient);

    SocialActionReceipt receipt =
        service.replyToInteraction(
            new SocialReplyRequest(
                SocialPlatform.INSTAGRAM,
                "comment-42",
                "Thanks for the feedback.",
                DisclosureLevel.ASSISTED));

    assertThat(receipt.toolName()).isEqualTo("instagram.reply_comment");
    assertThat(toolClient.lastToolName).isEqualTo("instagram.reply_comment");
    assertThat(toolClient.lastArguments).containsEntry("conversation_id", "comment-42");
    assertThat(toolClient.lastArguments).containsEntry("disclosure_level", "assisted");
  }

  @Test
  void shouldRejectBlankPostText() {
    RecordingToolClient toolClient = new RecordingToolClient();
    SocialPublishingService service = new SocialPublishingService(toolClient);

    assertThatThrownBy(
            () ->
                service.publishPost(
                    new SocialPostRequest(
                        SocialPlatform.THREADS, "  ", java.util.List.of(), DisclosureLevel.NONE)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("textContent");
  }

  @Test
  void shouldForceAutomatedDisclosureWhenNoneRequested() {
    RecordingToolClient toolClient = new RecordingToolClient();
    SocialPublishingService service = new SocialPublishingService(toolClient);

    service.publishPost(
        new SocialPostRequest(
            SocialPlatform.THREADS,
            "Synthetic content sample",
            java.util.List.of(),
            DisclosureLevel.NONE));

    assertThat(toolClient.lastArguments).containsEntry("disclosure_level", "automated");
  }

  private static final class RecordingToolClient implements McpToolClient {
    private String lastToolName = "";
    private Map<String, Object> lastArguments = Map.of();

    @Override
    public McpToolResult callTool(String toolName, Map<String, Object> arguments) {
      lastToolName = toolName;
      lastArguments = Map.copyOf(arguments);
      return new McpToolResult(true, "ok", Map.of("external_id", "external-123"));
    }
  }
}
