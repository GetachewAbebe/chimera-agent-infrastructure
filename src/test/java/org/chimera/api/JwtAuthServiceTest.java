package org.chimera.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class JwtAuthServiceTest {
  private static final byte[] SECRET = "jwt-test-secret".getBytes(StandardCharsets.UTF_8);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

  @Test
  void shouldAuthenticateValidHs256BearerToken() throws Exception {
    JwtAuthService jwtAuthService = new JwtAuthService(SECRET, "chimera");
    String token =
        createHs256Token(
            SECRET,
            Map.of(
                "iss",
                "chimera",
                "tenant_id",
                "tenant-alpha",
                "roles",
                List.of("operator", "reviewer"),
                "exp",
                Instant.now().plusSeconds(120).getEpochSecond()));

    ApiKeyIdentity identity = jwtAuthService.authenticate("Bearer " + token).orElseThrow();

    assertThat(identity.tenantId()).isEqualTo("tenant-alpha");
    assertThat(identity.allowedRoles())
        .containsExactlyInAnyOrder(UserRole.OPERATOR, UserRole.REVIEWER);
  }

  @Test
  void shouldRejectExpiredToken() throws Exception {
    JwtAuthService jwtAuthService = new JwtAuthService(SECRET, null);
    String token =
        createHs256Token(
            SECRET,
            Map.of(
                "tenant_id",
                "tenant-alpha",
                "roles",
                List.of("viewer"),
                "exp",
                Instant.now().minusSeconds(5).getEpochSecond()));

    assertThat(jwtAuthService.authenticate("Bearer " + token)).isEmpty();
  }

  @Test
  void shouldRejectTokenWithInvalidHmacSignature() throws Exception {
    JwtAuthService jwtAuthService = new JwtAuthService(SECRET, null);
    String token =
        createHs256Token(
            "different-secret".getBytes(StandardCharsets.UTF_8),
            Map.of(
                "tenant_id",
                "tenant-alpha",
                "roles",
                List.of("viewer"),
                "exp",
                Instant.now().plusSeconds(120).getEpochSecond()));

    assertThat(jwtAuthService.authenticate("Bearer " + token)).isEmpty();
  }

  @Test
  void shouldAuthenticateValidRs256BearerTokenFromJwksFile() throws Exception {
    KeyPair keyPair = generateRsaKeyPair();
    Path jwksPath = Files.createTempFile("chimera-jwks", ".json");
    writeRsaJwks(jwksPath, "kid-alpha", (RSAPublicKey) keyPair.getPublic());

    JwtAuthService jwtAuthService =
        JwtAuthService.fromJwksPath(
            jwksPath, Duration.ofMillis(1), "chimera", "chimera-agent-infra");

    String token =
        createRs256Token(
            keyPair.getPrivate(),
            "kid-alpha",
            Map.of(
                "iss",
                "chimera",
                "aud",
                "chimera-agent-infra",
                "tenant_id",
                "tenant-alpha",
                "roles",
                List.of("operator", "reviewer"),
                "exp",
                Instant.now().plusSeconds(120).getEpochSecond()));

    ApiKeyIdentity identity = jwtAuthService.authenticate("Bearer " + token).orElseThrow();
    assertThat(identity.tenantId()).isEqualTo("tenant-alpha");
    assertThat(identity.allowedRoles())
        .containsExactlyInAnyOrder(UserRole.OPERATOR, UserRole.REVIEWER);
  }

  @Test
  void shouldReloadJwksFileForKeyRotation() throws Exception {
    KeyPair first = generateRsaKeyPair();
    KeyPair second = generateRsaKeyPair();
    Path jwksPath = Files.createTempFile("chimera-jwks-rotate", ".json");
    writeRsaJwks(jwksPath, "kid-one", (RSAPublicKey) first.getPublic());

    JwtAuthService jwtAuthService =
        JwtAuthService.fromJwksPath(jwksPath, Duration.ofMillis(1), "chimera", null);

    String firstToken =
        createRs256Token(
            first.getPrivate(),
            "kid-one",
            Map.of(
                "iss",
                "chimera",
                "tenant_id",
                "tenant-alpha",
                "roles",
                List.of("viewer"),
                "exp",
                Instant.now().plusSeconds(120).getEpochSecond()));
    assertThat(jwtAuthService.authenticate("Bearer " + firstToken)).isPresent();

    Thread.sleep(5);
    writeRsaJwks(jwksPath, "kid-two", (RSAPublicKey) second.getPublic());

    String secondToken =
        createRs256Token(
            second.getPrivate(),
            "kid-two",
            Map.of(
                "iss",
                "chimera",
                "tenant_id",
                "tenant-alpha",
                "roles",
                List.of("viewer"),
                "exp",
                Instant.now().plusSeconds(120).getEpochSecond()));

    assertThat(jwtAuthService.authenticate("Bearer " + secondToken)).isPresent();
    assertThat(jwtAuthService.authenticate("Bearer " + firstToken)).isEmpty();
  }

  @Test
  void shouldRejectTokenWhenAudienceMismatches() throws Exception {
    JwtAuthService jwtAuthService = new JwtAuthService(SECRET, "chimera", "chimera-api", null);
    String token =
        createHs256Token(
            SECRET,
            Map.of(
                "iss",
                "chimera",
                "aud",
                "different-audience",
                "tenant_id",
                "tenant-alpha",
                "roles",
                List.of("viewer"),
                "exp",
                Instant.now().plusSeconds(120).getEpochSecond()));

    assertThat(jwtAuthService.authenticate("Bearer " + token)).isEmpty();
  }

  @Test
  void shouldAuthenticateValidRs256BearerTokenFromRemoteJwksUrl() throws Exception {
    KeyPair keyPair = generateRsaKeyPair();
    AtomicReference<byte[]> jwksDocument =
        new AtomicReference<>(rsaJwksBytes("kid-remote", (RSAPublicKey) keyPair.getPublic()));

    HttpServer server = startJwksServer(jwksDocument);
    try {
      JwtAuthService jwtAuthService =
          JwtAuthService.fromJwksUrl(
              jwksUri(server),
              Duration.ofMillis(5),
              Duration.ofMillis(500),
              "chimera",
              "chimera-agent-infra");

      String token =
          createRs256Token(
              keyPair.getPrivate(),
              "kid-remote",
              Map.of(
                  "iss",
                  "chimera",
                  "aud",
                  "chimera-agent-infra",
                  "tenant_id",
                  "tenant-alpha",
                  "roles",
                  List.of("operator"),
                  "exp",
                  Instant.now().plusSeconds(120).getEpochSecond()));

      assertThat(jwtAuthService.authenticate("Bearer " + token)).isPresent();
    } finally {
      server.stop(0);
    }
  }

  @Test
  void shouldFailClosedWhenRemoteJwksReloadFails() throws Exception {
    KeyPair keyPair = generateRsaKeyPair();
    AtomicReference<byte[]> jwksDocument =
        new AtomicReference<>(rsaJwksBytes("kid-live", (RSAPublicKey) keyPair.getPublic()));

    HttpServer server = startJwksServer(jwksDocument);
    JwtAuthService jwtAuthService =
        JwtAuthService.fromJwksUrl(
            jwksUri(server), Duration.ofMillis(5), Duration.ofMillis(200), "chimera", null);
    String token =
        createRs256Token(
            keyPair.getPrivate(),
            "kid-live",
            Map.of(
                "iss",
                "chimera",
                "tenant_id",
                "tenant-alpha",
                "roles",
                List.of("viewer"),
                "exp",
                Instant.now().plusSeconds(120).getEpochSecond()));

    assertThat(jwtAuthService.authenticate("Bearer " + token)).isPresent();

    server.stop(0);
    Thread.sleep(15);

    assertThat(jwtAuthService.authenticate("Bearer " + token)).isEmpty();
  }

  private static String createHs256Token(byte[] secret, Map<String, Object> payload)
      throws Exception {
    Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
    String encodedHeader =
        BASE64_URL_ENCODER.encodeToString(OBJECT_MAPPER.writeValueAsBytes(header));
    String encodedPayload =
        BASE64_URL_ENCODER.encodeToString(OBJECT_MAPPER.writeValueAsBytes(payload));
    String signingInput = encodedHeader + "." + encodedPayload;

    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret, "HmacSHA256"));
    byte[] signature = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
    String encodedSignature = BASE64_URL_ENCODER.encodeToString(signature);

    return signingInput + "." + encodedSignature;
  }

  private static String createRs256Token(
      PrivateKey privateKey, String kid, Map<String, Object> payload) throws Exception {
    Map<String, Object> header = Map.of("alg", "RS256", "typ", "JWT", "kid", kid);
    String encodedHeader =
        BASE64_URL_ENCODER.encodeToString(OBJECT_MAPPER.writeValueAsBytes(header));
    String encodedPayload =
        BASE64_URL_ENCODER.encodeToString(OBJECT_MAPPER.writeValueAsBytes(payload));
    String signingInput = encodedHeader + "." + encodedPayload;

    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(privateKey);
    signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
    byte[] signed = signature.sign();
    return signingInput + "." + BASE64_URL_ENCODER.encodeToString(signed);
  }

  private static KeyPair generateRsaKeyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  private static void writeRsaJwks(Path path, String kid, RSAPublicKey publicKey) throws Exception {
    Files.write(path, rsaJwksBytes(kid, publicKey));
  }

  private static byte[] rsaJwksBytes(String kid, RSAPublicKey publicKey) throws Exception {
    Map<String, Object> jwk =
        Map.of(
            "kty",
            "RSA",
            "kid",
            kid,
            "n",
            BASE64_URL_ENCODER.encodeToString(unsignedBytes(publicKey.getModulus().toByteArray())),
            "e",
            BASE64_URL_ENCODER.encodeToString(
                unsignedBytes(publicKey.getPublicExponent().toByteArray())));
    Map<String, Object> jwks = Map.of("keys", List.of(jwk));
    return OBJECT_MAPPER.writeValueAsBytes(jwks);
  }

  private static byte[] unsignedBytes(byte[] value) {
    if (value.length > 1 && value[0] == 0) {
      byte[] stripped = new byte[value.length - 1];
      System.arraycopy(value, 1, stripped, 0, stripped.length);
      return stripped;
    }
    return value;
  }

  private static HttpServer startJwksServer(AtomicReference<byte[]> jwksDocument) throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/jwks.json",
        exchange -> {
          byte[] body = jwksDocument.get();
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    return server;
  }

  private static URI jwksUri(HttpServer server) {
    return URI.create("http://localhost:" + server.getAddress().getPort() + "/jwks.json");
  }
}
