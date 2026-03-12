package org.chimera.tests.perception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.chimera.mcp.McpResourceClient;
import org.chimera.model.TrendSignal;
import org.chimera.perception.KeywordSemanticRelevanceScorer;
import org.chimera.perception.McpPerceptionService;
import org.junit.jupiter.api.Test;

class McpPerceptionServiceTest {

  @Test
  void shouldReturnOnlySignalsAboveThreshold() {
    McpResourceClient resourceClient =
        resourceUri ->
            Map.of(
                    "news://ethiopia/fashion/trends",
                    """
                    Gen Z sneaker drop drives streetwear demand
                    Textile innovation cuts waste
                    """,
                    "twitter://mentions/recent",
                    """
                    Fans ask for sneaker restock
                    Football transfer drama
                    """)
                .getOrDefault(resourceUri, "");

    McpPerceptionService service =
        new McpPerceptionService(resourceClient, new KeywordSemanticRelevanceScorer());

    List<TrendSignal> signals =
        service.pollRelevantSignals(
            List.of("news://ethiopia/fashion/trends", "twitter://mentions/recent"),
            "fashion sneaker campaign",
            0.20,
            10);

    assertThat(signals).isNotEmpty();
    assertThat(signals).allMatch(signal -> signal.score() >= 0.20);
    assertThat(signals)
        .allMatch(
            signal ->
                signal.source().startsWith("news://") || signal.source().startsWith("twitter://"));
  }

  @Test
  void shouldEnforceThresholdBounds() {
    McpResourceClient resourceClient = resourceUri -> "line";
    McpPerceptionService service =
        new McpPerceptionService(resourceClient, new KeywordSemanticRelevanceScorer());

    assertThatThrownBy(
            () ->
                service.pollRelevantSignals(
                    List.of("news://ethiopia/fashion/trends"), "fashion", 1.10, 5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("threshold");
  }
}
