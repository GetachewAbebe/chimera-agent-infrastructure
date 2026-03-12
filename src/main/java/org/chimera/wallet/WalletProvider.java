package org.chimera.wallet;

public interface WalletProvider {
  WalletAccount ensureWallet(String agentId);

  WalletTransferResult transferUsd(WalletTransferRequest request);
}
