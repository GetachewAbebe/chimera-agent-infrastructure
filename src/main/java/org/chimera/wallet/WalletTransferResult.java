package org.chimera.wallet;

import java.math.BigDecimal;

public record WalletTransferResult(
    boolean success,
    String provider,
    String transactionId,
    String status,
    String message,
    BigDecimal amountUsd) {}
