package org.chimera.wallet;

import java.util.UUID;

public final class SimulatedWalletProvider implements WalletProvider {
  @Override
  public WalletAccount ensureWallet(String agentId) {
    if (agentId == null || agentId.isBlank()) {
      throw new IllegalArgumentException("agentId must not be blank");
    }
    return new WalletAccount("simulated", "wallet-" + agentId, "sim-" + agentId);
  }

  @Override
  public WalletTransferResult transferUsd(WalletTransferRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("request is required");
    }
    String transactionId = "sim-tx-" + UUID.randomUUID();
    return new WalletTransferResult(
        true,
        "simulated",
        transactionId,
        "submitted",
        "Simulated transfer accepted",
        request.amountUsd());
  }
}
