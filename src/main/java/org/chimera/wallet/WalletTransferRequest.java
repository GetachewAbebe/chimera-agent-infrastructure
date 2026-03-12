package org.chimera.wallet;

import java.math.BigDecimal;

public record WalletTransferRequest(
    String walletId, String destinationAddress, BigDecimal amountUsd, String memo) {}
