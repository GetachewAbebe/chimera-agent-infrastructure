package org.chimera.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class JwtAuthService {
  public static final String ENV_JWT_HS256_SECRET = "CHIMERA_JWT_HS256_SECRET";
  public static final String ENV_JWT_ISSUER = "CHIMERA_JWT_ISSUER";
  public static final String ENV_JWT_AUDIENCE = "CHIMERA_JWT_AUDIENCE";
  public static final String ENV_JWT_JWKS_PATH = "CHIMERA_JWT_JWKS_PATH";
  public static final String ENV_JWT_JWKS_URL = "CHIMERA_JWT_JWKS_URL";
  public static final String ENV_JWT_JWKS_HTTP_TIMEOUT_MS = "CHIMERA_JWT_JWKS_HTTP_TIMEOUT_MS";
  public static final String ENV_JWT_JWKS_REFRESH_SECONDS = "CHIMERA_JWT_JWKS_REFRESH_SECONDS";
  private static final long DEFAULT_JWKS_REFRESH_SECONDS = 60;
  private static final long DEFAULT_JWKS_HTTP_TIMEOUT_MS = 2000;

  private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final byte[] hmacSecret;
  private final String expectedIssuer;
  private final String expectedAudience;
  private final JwksKeyResolver jwksKeyResolver;

  public JwtAuthService(byte[] hmacSecret, String expectedIssuer) {
    this(hmacSecret, expectedIssuer, null, null);
  }

  JwtAuthService(
      byte[] hmacSecret,
      String expectedIssuer,
      String expectedAudience,
      JwksKeyResolver jwksKeyResolver) {
    this.hmacSecret = hmacSecret == null ? null : hmacSecret.clone();
    this.expectedIssuer = normalize(expectedIssuer);
    this.expectedAudience = normalize(expectedAudience);
    this.jwksKeyResolver = jwksKeyResolver;
  }

  public static JwtAuthService fromEnvironment() {
    String secret = System.getenv(ENV_JWT_HS256_SECRET);
    String issuer = System.getenv(ENV_JWT_ISSUER);
    String audience = System.getenv(ENV_JWT_AUDIENCE);
    String jwksUrl = System.getenv(ENV_JWT_JWKS_URL);
    String jwksPath = System.getenv(ENV_JWT_JWKS_PATH);
    JwksKeyResolver resolver = null;
    long refreshSeconds =
        parsePositiveLong(
            System.getenv(ENV_JWT_JWKS_REFRESH_SECONDS), DEFAULT_JWKS_REFRESH_SECONDS);
    if (jwksUrl != null && !jwksUrl.isBlank()) {
      long timeoutMillis =
          parsePositiveLong(
              System.getenv(ENV_JWT_JWKS_HTTP_TIMEOUT_MS), DEFAULT_JWKS_HTTP_TIMEOUT_MS);
      resolver =
          new UrlBackedJwksKeyResolver(
              URI.create(jwksUrl),
              Duration.ofSeconds(refreshSeconds),
              Duration.ofMillis(timeoutMillis));
    } else if (jwksPath != null && !jwksPath.isBlank()) {
      resolver =
          new FileBackedJwksKeyResolver(Path.of(jwksPath), Duration.ofSeconds(refreshSeconds));
    }
    byte[] hmac =
        secret == null || secret.isBlank() ? null : secret.getBytes(StandardCharsets.UTF_8);
    return new JwtAuthService(hmac, issuer, audience, resolver);
  }

  static JwtAuthService fromJwksPath(
      Path jwksPath, Duration refreshInterval, String expectedIssuer, String expectedAudience) {
    return new JwtAuthService(
        null,
        expectedIssuer,
        expectedAudience,
        new FileBackedJwksKeyResolver(jwksPath, refreshInterval));
  }

  static JwtAuthService fromJwksUrl(
      URI jwksUri,
      Duration refreshInterval,
      Duration requestTimeout,
      String expectedIssuer,
      String expectedAudience) {
    return new JwtAuthService(
        null,
        expectedIssuer,
        expectedAudience,
        new UrlBackedJwksKeyResolver(jwksUri, refreshInterval, requestTimeout));
  }

  public boolean isConfigured() {
    return (hmacSecret != null && hmacSecret.length > 0) || jwksKeyResolver != null;
  }

  public Optional<ApiKeyIdentity> authenticate(String authorizationHeader) {
    if (!isConfigured() || authorizationHeader == null || authorizationHeader.isBlank()) {
      return Optional.empty();
    }

    String prefix = "Bearer ";
    if (!authorizationHeader.startsWith(prefix)) {
      return Optional.empty();
    }

    String token = authorizationHeader.substring(prefix.length()).trim();
    if (token.isBlank()) {
      return Optional.empty();
    }

    try {
      String[] parts = token.split("\\.");
      if (parts.length != 3) {
        return Optional.empty();
      }

      Map<String, Object> header = decodeJson(parts[0]);
      String alg = asString(header.get("alg"));
      if (alg == null) {
        return Optional.empty();
      }

      String signingInput = parts[0] + "." + parts[1];
      byte[] providedSignature = BASE64_URL_DECODER.decode(parts[2]);
      if (!isSignatureValid(alg, header, signingInput, providedSignature)) {
        return Optional.empty();
      }

      Map<String, Object> payload = decodeJson(parts[1]);
      if (!isTimeWindowValid(payload)) {
        return Optional.empty();
      }
      if (!isIssuerValid(payload)) {
        return Optional.empty();
      }
      if (!isAudienceValid(payload)) {
        return Optional.empty();
      }

      String tenantId = parseTenantId(payload);
      Set<UserRole> roles = parseRoles(payload);
      if (tenantId == null || tenantId.isBlank() || roles.isEmpty()) {
        return Optional.empty();
      }

      return Optional.of(new ApiKeyIdentity(tenantId, roles));
    } catch (Exception ignored) {
      return Optional.empty();
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> decodeJson(String base64UrlJson) throws Exception {
    byte[] decoded = BASE64_URL_DECODER.decode(base64UrlJson);
    return OBJECT_MAPPER.readValue(decoded, Map.class);
  }

  private byte[] hmacSha256(String data) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(hmacSecret, "HmacSHA256"));
    return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
  }

  private boolean isSignatureValid(
      String algorithm, Map<String, Object> header, String signingInput, byte[] providedSignature)
      throws Exception {
    if ("HS256".equals(algorithm)) {
      if (hmacSecret == null || hmacSecret.length == 0) {
        return false;
      }
      byte[] expected = hmacSha256(signingInput);
      return java.security.MessageDigest.isEqual(expected, providedSignature);
    }

    if ("RS256".equals(algorithm)) {
      if (jwksKeyResolver == null) {
        return false;
      }
      String kid = asString(header.get("kid"));
      if (kid == null || kid.isBlank()) {
        return false;
      }
      Optional<PublicKey> key = jwksKeyResolver.resolve(kid);
      if (key.isEmpty()) {
        return false;
      }
      Signature verifier = Signature.getInstance("SHA256withRSA");
      verifier.initVerify(key.get());
      verifier.update(signingInput.getBytes(StandardCharsets.UTF_8));
      return verifier.verify(providedSignature);
    }

    return false;
  }

  private boolean isTimeWindowValid(Map<String, Object> payload) {
    long now = Instant.now().getEpochSecond();

    Long exp = parseLong(payload.get("exp"));
    if (exp != null && now >= exp) {
      return false;
    }

    Long nbf = parseLong(payload.get("nbf"));
    if (nbf != null && now < nbf) {
      return false;
    }

    return true;
  }

  private boolean isIssuerValid(Map<String, Object> payload) {
    if (expectedIssuer == null) {
      return true;
    }
    Object issuer = payload.get("iss");
    return issuer instanceof String && expectedIssuer.equals(issuer);
  }

  private boolean isAudienceValid(Map<String, Object> payload) {
    if (expectedAudience == null) {
      return true;
    }

    Object audienceClaim = payload.get("aud");
    if (audienceClaim instanceof String audience) {
      return expectedAudience.equals(audience);
    }

    if (audienceClaim instanceof Collection<?> audiences) {
      for (Object value : audiences) {
        if (value instanceof String audience && expectedAudience.equals(audience)) {
          return true;
        }
      }
    }

    return false;
  }

  private static String parseTenantId(Map<String, Object> payload) {
    Object tenantId = payload.get("tenant_id");
    if (!(tenantId instanceof String) || ((String) tenantId).isBlank()) {
      tenantId = payload.get("tenantId");
    }
    if (!(tenantId instanceof String) || ((String) tenantId).isBlank()) {
      tenantId = payload.get("tenant");
    }
    return tenantId instanceof String ? (String) tenantId : null;
  }

  private static Set<UserRole> parseRoles(Map<String, Object> payload) {
    Object rolesClaim = payload.get("roles");
    if (rolesClaim == null) {
      rolesClaim = payload.get("role");
    }

    if (rolesClaim instanceof String role) {
      return Set.of(UserRole.valueOf(role.trim().toUpperCase()));
    }

    if (!(rolesClaim instanceof Collection<?> collection)) {
      return Collections.emptySet();
    }

    Set<UserRole> roles = new HashSet<>();
    for (Object item : collection) {
      if (item instanceof String value && !value.isBlank()) {
        roles.add(UserRole.valueOf(value.trim().toUpperCase()));
      }
    }
    return roles;
  }

  private static Long parseLong(Object value) {
    if (value instanceof Number n) {
      return n.longValue();
    }
    if (value instanceof String text && !text.isBlank()) {
      return Long.parseLong(text);
    }
    return null;
  }

  private static String asString(Object value) {
    return value instanceof String text && !text.isBlank() ? text : null;
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static long parsePositiveLong(String raw, long fallback) {
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      long parsed = Long.parseLong(raw.trim());
      return parsed > 0 ? parsed : fallback;
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  interface JwksKeyResolver {
    Optional<PublicKey> resolve(String kid);
  }

  static final class FileBackedJwksKeyResolver implements JwksKeyResolver {
    private final Path jwksPath;
    private final long refreshMillis;

    private volatile long nextReloadAtMillis = 0;
    private volatile long lastModifiedMillis = -1;
    private volatile Map<String, PublicKey> publicKeys = Map.of();

    FileBackedJwksKeyResolver(Path jwksPath, Duration refreshInterval) {
      if (jwksPath == null) {
        throw new IllegalArgumentException("jwksPath is required");
      }
      if (refreshInterval == null || refreshInterval.isZero() || refreshInterval.isNegative()) {
        throw new IllegalArgumentException("refreshInterval must be positive");
      }
      this.jwksPath = jwksPath;
      this.refreshMillis = refreshInterval.toMillis();
    }

    @Override
    public Optional<PublicKey> resolve(String kid) {
      try {
        reloadIfNeeded();
        return Optional.ofNullable(publicKeys.get(kid));
      } catch (Exception ignored) {
        return Optional.empty();
      }
    }

    private synchronized void reloadIfNeeded() throws Exception {
      long now = System.currentTimeMillis();
      if (now < nextReloadAtMillis && !publicKeys.isEmpty()) {
        return;
      }

      long modifiedMillis = Files.getLastModifiedTime(jwksPath).toMillis();
      if (modifiedMillis != lastModifiedMillis || publicKeys.isEmpty()) {
        publicKeys = loadPublicKeys(jwksPath);
        lastModifiedMillis = modifiedMillis;
      }
      nextReloadAtMillis = now + refreshMillis;
    }

    private static Map<String, PublicKey> loadPublicKeys(Path path) throws Exception {
      byte[] content = Files.readAllBytes(path);
      return parseJwksPublicKeys(content);
    }
  }

  static final class UrlBackedJwksKeyResolver implements JwksKeyResolver {
    private final URI jwksUri;
    private final long refreshMillis;
    private final Duration requestTimeout;
    private final HttpClient httpClient;

    private volatile long nextReloadAtMillis = 0;
    private volatile Map<String, PublicKey> publicKeys = Map.of();

    UrlBackedJwksKeyResolver(URI jwksUri, Duration refreshInterval, Duration requestTimeout) {
      if (jwksUri == null) {
        throw new IllegalArgumentException("jwksUri is required");
      }
      String scheme = jwksUri.getScheme();
      if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
        throw new IllegalArgumentException("jwksUri must use http or https");
      }
      if (refreshInterval == null || refreshInterval.isZero() || refreshInterval.isNegative()) {
        throw new IllegalArgumentException("refreshInterval must be positive");
      }
      if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
        throw new IllegalArgumentException("requestTimeout must be positive");
      }
      this.jwksUri = jwksUri;
      this.refreshMillis = refreshInterval.toMillis();
      this.requestTimeout = requestTimeout;
      this.httpClient = HttpClient.newBuilder().connectTimeout(requestTimeout).build();
    }

    @Override
    public Optional<PublicKey> resolve(String kid) {
      try {
        reloadIfNeeded();
        return Optional.ofNullable(publicKeys.get(kid));
      } catch (Exception ignored) {
        return Optional.empty();
      }
    }

    private synchronized void reloadIfNeeded() {
      long now = System.currentTimeMillis();
      if (now < nextReloadAtMillis && !publicKeys.isEmpty()) {
        return;
      }

      try {
        publicKeys = fetchPublicKeys();
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
        // Fail closed when interrupted during JWKS refresh.
        publicKeys = Map.of();
      } catch (Exception ignored) {
        // Fail closed: if remote JWKS cannot be loaded, reject bearer auth until next refresh.
        publicKeys = Map.of();
      }
      nextReloadAtMillis = now + refreshMillis;
    }

    private Map<String, PublicKey> fetchPublicKeys() throws Exception {
      HttpRequest request = HttpRequest.newBuilder(jwksUri).GET().timeout(requestTimeout).build();
      HttpResponse<byte[]> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException("JWKS endpoint returned status " + response.statusCode());
      }
      return parseJwksPublicKeys(response.body());
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, PublicKey> parseJwksPublicKeys(byte[] content) throws Exception {
    Map<String, Object> root = OBJECT_MAPPER.readValue(content, Map.class);
    Object keysValue = root.get("keys");
    if (!(keysValue instanceof Collection<?> keys)) {
      return Map.of();
    }

    Map<String, PublicKey> parsed = new HashMap<>();
    for (Object item : keys) {
      if (!(item instanceof Map<?, ?> keyNode)) {
        continue;
      }
      String keyType = asString(keyNode.get("kty"));
      if (!"RSA".equals(keyType)) {
        continue;
      }
      String kid = asString(keyNode.get("kid"));
      String modulus = asString(keyNode.get("n"));
      String exponent = asString(keyNode.get("e"));
      if (kid == null || modulus == null || exponent == null) {
        continue;
      }
      parsed.put(kid, rsaPublicKey(modulus, exponent));
    }
    return Map.copyOf(parsed);
  }

  private static PublicKey rsaPublicKey(String n, String e) throws Exception {
    byte[] modulusBytes = BASE64_URL_DECODER.decode(n);
    byte[] exponentBytes = BASE64_URL_DECODER.decode(e);
    RSAPublicKeySpec spec =
        new RSAPublicKeySpec(new BigInteger(1, modulusBytes), new BigInteger(1, exponentBytes));
    return KeyFactory.getInstance("RSA").generatePublic(spec);
  }
}
