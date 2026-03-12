package org.chimera.tests.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.chimera.model.TaskContext;
import org.chimera.wallet.SimulatedWalletProvider;
import org.chimera.wallet.WalletExecutionService;
import org.junit.jupiter.api.Test;

class WalletExecutionServiceTest {

  @Test
  void shouldParseDestinationAndAmountFromTaskContext() {
    WalletExecutionService service = new WalletExecutionService(new SimulatedWalletProvider());

    var result =
        service.executeTransaction(
            "worker-alpha",
            new TaskContext(
                "Transfer creator stipend",
                List.of("Respect persona directives"),
                List.of("wallet://to/0xdef456", "wallet://amount_usd/22.75")));

    assertThat(result.success()).isTrue();
    assertThat(result.amountUsd()).isEqualByComparingTo(new BigDecimal("22.75"));
    assertThat(result.provider()).isEqualTo("simulated");
  }

  @Test
  void shouldRejectMissingAmountResource() {
    WalletExecutionService service = new WalletExecutionService(new SimulatedWalletProvider());

    assertThatThrownBy(
            () ->
                service.executeTransaction(
                    "worker-alpha",
                    new TaskContext(
                        "Transfer creator stipend",
                        List.of("Respect persona directives"),
                        List.of("wallet://to/0xdef456"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("wallet://amount_usd/");
  }
}
