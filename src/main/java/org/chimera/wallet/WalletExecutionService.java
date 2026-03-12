package org.chimera.wallet;

import org.chimera.model.TaskContext;

public final class WalletExecutionService {
  private final WalletProvider walletProvider;

  public WalletExecutionService(WalletProvider walletProvider) {
    if (walletProvider == null) {
      throw new IllegalArgumentException("walletProvider is required");
    }
    this.walletProvider = walletProvider;
  }

  public WalletTransferResult executeTransaction(String agentId, TaskContext context) {
    if (agentId == null || agentId.isBlank()) {
      throw new IllegalArgumentException("agentId must not be blank");
    }
    if (context == null) {
      throw new IllegalArgumentException("context is required");
    }

    WalletAccount walletAccount = walletProvider.ensureWallet(agentId);
    WalletTransferRequest transferRequest =
        new WalletTransferRequest(
            walletAccount.walletId(),
            WalletTaskResourceParser.parseDestinationAddress(context),
            WalletTaskResourceParser.parseAmountUsd(context),
            context.goalDescription());
    return walletProvider.transferUsd(transferRequest);
  }

  public String providerName() {
    if (walletProvider instanceof CoinbaseAgentKitWalletProvider) {
      return "coinbase_agentkit_hmac";
    }
    if (walletProvider instanceof SimulatedWalletProvider) {
      return "simulated";
    }
    return walletProvider.getClass().getSimpleName();
  }
}
