package org.chimera.wallet;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public final class HttpWalletTransport implements WalletTransport {
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final String baseUrl;
  private final Duration timeout;

  public HttpWalletTransport(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl) {
    this(httpClient, objectMapper, baseUrl, Duration.ofSeconds(5));
  }

  public HttpWalletTransport(
      HttpClient httpClient, ObjectMapper objectMapper, String baseUrl, Duration timeout) {
    if (httpClient == null) {
      throw new IllegalArgumentException("httpClient is required");
    }
    if (objectMapper == null) {
      throw new IllegalArgumentException("objectMapper is required");
    }
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("baseUrl is required");
    }
    if (timeout == null || timeout.isNegative() || timeout.isZero()) {
      throw new IllegalArgumentException("timeout must be > 0");
    }
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.timeout = timeout;
  }

  @Override
  public Map<String, Object> post(
      String path, Map<String, String> headers, Map<String, Object> payload) {
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("path is required");
    }
    Map<String, String> requestHeaders = headers == null ? Map.of() : Map.copyOf(headers);
    Map<String, Object> requestPayload = payload == null ? Map.of() : Map.copyOf(payload);

    try {
      HttpRequest.Builder builder =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl + path))
              .timeout(timeout)
              .header("Content-Type", "application/json")
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      objectMapper.writeValueAsString(requestPayload), StandardCharsets.UTF_8));

      for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
        builder.header(entry.getKey(), entry.getValue());
      }

      HttpResponse<String> response =
          httpClient.send(
              builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() > 299) {
        throw new IllegalStateException(
            "Wallet transport request failed with status " + response.statusCode());
      }
      if (response.body() == null || response.body().isBlank()) {
        return Map.of();
      }
      return objectMapper.readValue(response.body(), MAP_TYPE);
    } catch (Exception ex) {
      throw new IllegalStateException("Wallet transport request failed", ex);
    }
  }

  public static HttpWalletTransport defaultTransport(String baseUrl) {
    return new HttpWalletTransport(HttpClient.newHttpClient(), new ObjectMapper(), baseUrl);
  }
}
