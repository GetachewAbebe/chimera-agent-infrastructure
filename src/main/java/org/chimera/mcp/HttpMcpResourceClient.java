package org.chimera.mcp;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class HttpMcpResourceClient implements McpResourceClient {
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(4);

  private final HttpClient httpClient;
  private final URI endpointUri;
  private final Duration requestTimeout;
  private final String authorizationHeader;

  public HttpMcpResourceClient(URI endpointUri) {
    this(HttpClient.newHttpClient(), endpointUri, DEFAULT_TIMEOUT, "");
  }

  public HttpMcpResourceClient(HttpClient httpClient, URI endpointUri) {
    this(httpClient, endpointUri, DEFAULT_TIMEOUT, "");
  }

  public HttpMcpResourceClient(
      HttpClient httpClient, URI endpointUri, Duration requestTimeout, String authorizationHeader) {
    if (httpClient == null) {
      throw new IllegalArgumentException("httpClient is required");
    }
    if (endpointUri == null) {
      throw new IllegalArgumentException("endpointUri is required");
    }
    if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
      throw new IllegalArgumentException("requestTimeout must be positive");
    }

    this.httpClient = httpClient;
    this.endpointUri = endpointUri;
    this.requestTimeout = requestTimeout;
    this.authorizationHeader = authorizationHeader == null ? "" : authorizationHeader.trim();
  }

  @Override
  public String readResource(String resourceUri) {
    if (resourceUri == null || resourceUri.isBlank()) {
      throw new IllegalArgumentException("resourceUri must not be blank");
    }

    URI requestUri = withResourceQuery(endpointUri, resourceUri);
    HttpRequest.Builder requestBuilder =
        HttpRequest.newBuilder(requestUri).timeout(requestTimeout).GET();
    if (!authorizationHeader.isBlank()) {
      requestBuilder.header("Authorization", authorizationHeader);
    }

    try {
      HttpResponse<String> response =
          httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        return response.body() == null ? "" : response.body();
      }
      throw new IllegalStateException(
          "MCP resource request failed with status "
              + response.statusCode()
              + " for "
              + requestUri);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("MCP resource request interrupted", ex);
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to call MCP resource endpoint", ex);
    }
  }

  private static URI withResourceQuery(URI endpointUri, String resourceUri) {
    String encoded = URLEncoder.encode(resourceUri, StandardCharsets.UTF_8);
    String separator = endpointUri.getQuery() == null ? "?" : "&";
    return URI.create(endpointUri + separator + "uri=" + encoded);
  }
}
