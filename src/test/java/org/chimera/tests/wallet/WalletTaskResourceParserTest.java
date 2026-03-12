package org.chimera.tests.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.chimera.model.TaskContext;
import org.chimera.wallet.WalletTaskResourceParser;
import org.junit.jupiter.api.Test;

class WalletTaskResourceParserTest {

  @Test
  void shouldParseDestinationAndAmountFromResources() {
    TaskContext context =
        new TaskContext(
            "Transfer creator stipend",
            List.of("Respect persona directives"),
            List.of("wallet://to/0xdef456", "wallet://amount_usd/22.75"));

    assertThat(WalletTaskResourceParser.parseDestinationAddress(context)).isEqualTo("0xdef456");
    assertThat(WalletTaskResourceParser.parseAmountUsd(context))
        .isEqualByComparingTo(new BigDecimal("22.75"));
  }

  @Test
  void shouldRejectMissingDestination() {
    TaskContext context =
        new TaskContext(
            "Transfer creator stipend",
            List.of("Respect persona directives"),
            List.of("wallet://amount_usd/10.00"));

    assertThatThrownBy(() -> WalletTaskResourceParser.parseDestinationAddress(context))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("wallet://to/");
  }

  @Test
  void shouldRejectMissingAmount() {
    TaskContext context =
        new TaskContext(
            "Transfer creator stipend",
            List.of("Respect persona directives"),
            List.of("wallet://to/0xdef456"));

    assertThatThrownBy(() -> WalletTaskResourceParser.parseAmountUsd(context))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("wallet://amount_usd/");
  }
}
