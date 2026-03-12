package org.chimera.tests.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.chimera.security.EnvironmentSecretProvider;
import org.chimera.wallet.CoinbaseAgentKitWalletProvider;
import org.chimera.wallet.WalletTransferRequest;
import org.chimera.wallet.WalletTransport;
import org.junit.jupiter.api.Test;

class CoinbaseAgentKitWalletProviderTest {

  @Test
  void shouldUseSecretsAndTransportForWalletAndTransferCalls() {
    AtomicReference<String> lastPath = new AtomicReference<>();
    AtomicReference<Map<String, String>> lastHeaders = new AtomicReference<>();
    WalletTransport transport =
        (path, headers, payload) -> {
          lastPath.set(path);
          lastHeaders.set(headers);
          if ("/wallets".equals(path)) {
            return Map.of("wallet_id", "wallet-001", "address", "0xcoinbase001");
          }
          return Map.of("success", true, "transaction_id", "tx-001", "status", "submitted");
        };

    CoinbaseAgentKitWalletProvider provider =
        new CoinbaseAgentKitWalletProvider(
            new EnvironmentSecretProvider(
                Map.of(
                    "CDP_API_KEY_NAME", "test-key-name",
                    "CDP_API_KEY_PRIVATE_KEY", "test-private-key")),
            transport);

    var account = provider.ensureWallet("agent-001");
    assertThat(lastPath.get()).isEqualTo("/wallets");
    assertThat(lastHeaders.get()).containsEntry("X-CDP-API-KEY-NAME", "test-key-name");
    assertThat(lastHeaders.get()).containsKey("X-CDP-SIGNATURE");
    assertThat(lastHeaders.get()).containsKey("X-CDP-TIMESTAMP");
    assertThat(lastHeaders.get()).containsKey("X-CDP-KEY-FINGERPRINT");
    assertThat(lastHeaders.get()).doesNotContainKey("X-CDP-API-KEY-PRIVATE-KEY");
    assertThat(account.walletId()).isEqualTo("wallet-001");

    var transferResult =
        provider.transferUsd(
            new WalletTransferRequest(
                account.walletId(), "0xdestination999", new BigDecimal("19.25"), "creator payout"));
    assertThat(lastPath.get()).isEqualTo("/transfers");
    assertThat(lastHeaders.get()).containsKey("X-CDP-SIGNATURE");
    assertThat(transferResult.success()).isTrue();
    assertThat(transferResult.transactionId()).isEqualTo("tx-001");
  }
}
