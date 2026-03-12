package org.chimera.wallet;

import java.math.BigDecimal;
import java.util.List;
import org.chimera.model.TaskContext;

public final class WalletTaskResourceParser {
  private static final String RESOURCE_DESTINATION_PREFIX = "wallet://to/";
  private static final String RESOURCE_AMOUNT_PREFIX = "wallet://amount_usd/";

  private WalletTaskResourceParser() {}

  public static String parseDestinationAddress(TaskContext context) {
    for (String resource : resources(context)) {
      if (resource != null && resource.startsWith(RESOURCE_DESTINATION_PREFIX)) {
        String destination = resource.substring(RESOURCE_DESTINATION_PREFIX.length()).trim();
        if (!destination.isBlank()) {
          return destination;
        }
      }
    }
    throw new IllegalArgumentException(
        "Transaction context missing destination resource: "
            + RESOURCE_DESTINATION_PREFIX
            + "<address>");
  }

  public static BigDecimal parseAmountUsd(TaskContext context) {
    for (String resource : resources(context)) {
      if (resource != null && resource.startsWith(RESOURCE_AMOUNT_PREFIX)) {
        String raw = resource.substring(RESOURCE_AMOUNT_PREFIX.length()).trim();
        if (raw.isBlank()) {
          continue;
        }
        BigDecimal amount = new BigDecimal(raw);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
          throw new IllegalArgumentException("wallet amount must be > 0");
        }
        return amount;
      }
    }
    throw new IllegalArgumentException(
        "Transaction context missing amount resource: " + RESOURCE_AMOUNT_PREFIX + "<decimal>");
  }

  private static List<String> resources(TaskContext context) {
    if (context == null) {
      throw new IllegalArgumentException("context is required");
    }
    return context.requiredResources() == null ? List.of() : context.requiredResources();
  }
}
