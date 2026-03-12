package org.chimera.wallet;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.chimera.security.SecretProvider;

public final class CoinbaseAgentKitWalletProvider implements WalletProvider {
  public static final String SECRET_API_KEY_NAME = "CDP_API_KEY_NAME";
  public static final String SECRET_API_KEY_PRIVATE_KEY = "CDP_API_KEY_PRIVATE_KEY";

  private final SecretProvider secretProvider;
  private final WalletTransport walletTransport;

  public CoinbaseAgentKitWalletProvider(
      SecretProvider secretProvider, WalletTransport walletTransport) {
    if (secretProvider == null) {
      throw new IllegalArgumentException("secretProvider is required");
    }
    if (walletTransport == null) {
      throw new IllegalArgumentException("walletTransport is required");
    }
    this.secretProvider = secretProvider;
    this.walletTransport = walletTransport;
  }

  @Override
  public WalletAccount ensureWallet(String agentId) {
    if (agentId == null || agentId.isBlank()) {
      throw new IllegalArgumentException("agentId must not be blank");
    }

    Map<String, Object> response =
        walletTransport.post(
            "/wallets",
            authHeaders("/wallets", Map.of("provider", "coinbase_agentkit", "agent_id", agentId)),
            Map.of("provider", "coinbase_agentkit", "agent_id", agentId));

    String walletId = stringValue(response.get("wallet_id"), "wallet-" + agentId);
    String address = stringValue(response.get("address"), "cb-" + agentId);
    return new WalletAccount("coinbase_agentkit", walletId, address);
  }

  @Override
  public WalletTransferResult transferUsd(WalletTransferRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("request is required");
    }
    if (request.walletId() == null || request.walletId().isBlank()) {
      throw new IllegalArgumentException("walletId must not be blank");
    }
    if (request.destinationAddress() == null || request.destinationAddress().isBlank()) {
      throw new IllegalArgumentException("destinationAddress must not be blank");
    }
    if (request.amountUsd() == null || request.amountUsd().compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("amountUsd must be > 0");
    }

    Map<String, Object> response =
        walletTransport.post(
            "/transfers",
            authHeaders(
                "/transfers",
                Map.of(
                    "wallet_id", request.walletId(),
                    "destination", request.destinationAddress(),
                    "amount_usd", request.amountUsd(),
                    "memo", request.memo() == null ? "" : request.memo())),
            Map.of(
                "wallet_id", request.walletId(),
                "destination", request.destinationAddress(),
                "amount_usd", request.amountUsd(),
                "memo", request.memo() == null ? "" : request.memo()));

    boolean success = booleanValue(response.get("success"), true);
    String transactionId = stringValue(response.get("transaction_id"), "tx-unknown");
    String status = stringValue(response.get("status"), success ? "submitted" : "failed");
    String message = stringValue(response.get("message"), "");
    return new WalletTransferResult(
        success, "coinbase_agentkit", transactionId, status, message, request.amountUsd());
  }

  private Map<String, String> authHeaders(String path, Map<String, Object> payload) {
    String apiKeyName = secretProvider.getRequiredSecret(SECRET_API_KEY_NAME);
    String privateKey = secretProvider.getRequiredSecret(SECRET_API_KEY_PRIVATE_KEY);
    String timestamp = String.valueOf(Instant.now().getEpochSecond());
    String canonicalPayload = canonicalizePayload(payload);
    String signature = signRequest(privateKey, timestamp + "\n" + path + "\n" + canonicalPayload);
    return Map.of(
        "X-CDP-API-KEY-NAME", apiKeyName,
        "X-CDP-SIGNATURE", signature,
        "X-CDP-TIMESTAMP", timestamp,
        "X-CDP-KEY-FINGERPRINT", fingerprint(privateKey));
  }

  private static String canonicalizePayload(Map<String, Object> payload) {
    if (payload == null || payload.isEmpty()) {
      return "";
    }
    return payload.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(
            entry ->
                entry.getKey()
                    + "="
                    + (entry.getValue() == null ? "" : String.valueOf(entry.getValue())))
        .collect(Collectors.joining("&"));
  }

  private static String signRequest(String privateKey, String canonicalRequest) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(privateKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] digest = mac.doFinal(canonicalRequest.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(digest);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to sign Coinbase AgentKit request", ex);
    }
  }

  private static String fingerprint(String secret) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
      byte[] hash = messageDigest.digest(secret.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder();
      for (int index = 0; index < 8 && index < hash.length; index++) {
        builder.append(String.format("%02x", hash[index]));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 fingerprint unavailable", ex);
    }
  }

  private static String stringValue(Object value, String fallback) {
    if (value == null) {
      return fallback;
    }
    String normalized = String.valueOf(value);
    if (normalized.isBlank()) {
      return fallback;
    }
    return normalized;
  }

  private static boolean booleanValue(Object value, boolean fallback) {
    if (value == null) {
      return fallback;
    }
    if (value instanceof Boolean boolValue) {
      return boolValue;
    }
    return Boolean.parseBoolean(String.valueOf(value));
  }
}
