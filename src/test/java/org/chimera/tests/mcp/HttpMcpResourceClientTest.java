package org.chimera.tests.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.chimera.mcp.HttpMcpResourceClient;
import org.junit.jupiter.api.Test;

class HttpMcpResourceClientTest {

  @Test
  void shouldReadResourcePayloadFromHttpEndpoint() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/resource",
        exchange -> {
          String query = exchange.getRequestURI().getRawQuery();
          Map<String, String> queryParams = parseQuery(query);
          String requested = queryParams.getOrDefault("uri", "");
          String payload =
              switch (requested) {
                case "news://ethiopia/fashion/trends" ->
                    "Sustainable streetwear capsule\nCreator challenge";
                default -> "";
              };
          byte[] body = payload.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();

    try {
      URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/resource");
      HttpMcpResourceClient client =
          new HttpMcpResourceClient(
              java.net.http.HttpClient.newHttpClient(), endpoint, Duration.ofSeconds(3), "");

      String payload = client.readResource("news://ethiopia/fashion/trends");
      assertThat(payload).contains("Sustainable streetwear capsule");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void shouldFailOnNonSuccessResponse() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/resource",
        exchange -> {
          exchange.sendResponseHeaders(500, -1);
          exchange.close();
        });
    server.start();

    try {
      URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/resource");
      HttpMcpResourceClient client =
          new HttpMcpResourceClient(
              java.net.http.HttpClient.newHttpClient(), endpoint, Duration.ofSeconds(3), "");

      assertThatThrownBy(() -> client.readResource("news://ethiopia/fashion/trends"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("status 500");
    } finally {
      server.stop(0);
    }
  }

  private static Map<String, String> parseQuery(String rawQuery) {
    Map<String, String> values = new HashMap<>();
    if (rawQuery == null || rawQuery.isBlank()) {
      return values;
    }

    for (String token : rawQuery.split("&")) {
      String[] pair = token.split("=", 2);
      String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
      String value = pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
      values.put(key, value);
    }
    return values;
  }
}
