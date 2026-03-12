package org.chimera.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.chimera.model.ReviewDecision;
import org.chimera.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ChimeraHttpServer implements AutoCloseable {
  private static final String HEADER_TENANT = "X-Tenant-Id";
  private static final String HEADER_ROLE = "X-Role";
  private static final String HEADER_REQUEST_ID = "X-Request-Id";
  private static final String HEADER_API_KEY = "X-Api-Key";
  private static final String HEADER_AUTHORIZATION = "Authorization";
  private static final String HEADER_ORIGIN = "Origin";
  private static final String HEADER_VARY = "Vary";
  private static final String HEADER_AC_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
  private static final String HEADER_AC_ALLOW_HEADERS = "Access-Control-Allow-Headers";
  private static final String HEADER_AC_ALLOW_METHODS = "Access-Control-Allow-Methods";
  private static final String HEADER_AC_MAX_AGE = "Access-Control-Max-Age";
  private static final String ENV_CORS_ALLOWED_ORIGINS = "CHIMERA_CORS_ALLOWED_ORIGINS";
  private static final String DEFAULT_CORS_ALLOWED_ORIGINS =
      "http://localhost:5173,http://127.0.0.1:5173,http://localhost:4173,http://127.0.0.1:4173";
  private static final String ALLOWED_CORS_HEADERS =
      "Content-Type, X-Api-Key, Authorization, X-Tenant-Id, X-Role, X-Request-Id";
  private static final String ALLOWED_CORS_METHODS = "GET,POST,HEAD,OPTIONS";
  private static final String CORS_MAX_AGE_SECONDS = "600";
  private static final Logger LOGGER = LoggerFactory.getLogger(ChimeraHttpServer.class);

  private final HttpServer server;
  private final ObjectMapper objectMapper = defaultObjectMapper();
  private final CampaignApiService campaignApiService;
  private final ReviewApiService reviewApiService;
  private final DeadLetterApiService deadLetterApiService;
  private final TelemetryApiService telemetryApiService;
  private final ApiKeyAuthService apiKeyAuthService;
  private final JwtAuthService jwtAuthService;
  private final RateLimiter writeRateLimiter;
  private final List<String> corsAllowedOrigins;

  public ChimeraHttpServer(
      int port, CampaignApiService campaignApiService, ReviewApiService reviewApiService)
      throws IOException {
    this(
        port,
        campaignApiService,
        reviewApiService,
        ApiKeyAuthService.fromEnvironment(),
        JwtAuthService.fromEnvironment(),
        new RequestRateLimiter(30, java.time.Duration.ofMinutes(1)),
        null,
        null);
  }

  public ChimeraHttpServer(
      int port,
      CampaignApiService campaignApiService,
      ReviewApiService reviewApiService,
      RateLimiter writeRateLimiter)
      throws IOException {
    this(port, campaignApiService, reviewApiService, writeRateLimiter, null);
  }

  public ChimeraHttpServer(
      int port,
      CampaignApiService campaignApiService,
      ReviewApiService reviewApiService,
      RateLimiter writeRateLimiter,
      TelemetryApiService telemetryApiService)
      throws IOException {
    this(port, campaignApiService, reviewApiService, writeRateLimiter, telemetryApiService, null);
  }

  public ChimeraHttpServer(
      int port,
      CampaignApiService campaignApiService,
      ReviewApiService reviewApiService,
      RateLimiter writeRateLimiter,
      TelemetryApiService telemetryApiService,
      DeadLetterApiService deadLetterApiService)
      throws IOException {
    this(
        port,
        campaignApiService,
        reviewApiService,
        ApiKeyAuthService.fromEnvironment(),
        JwtAuthService.fromEnvironment(),
        writeRateLimiter,
        telemetryApiService,
        deadLetterApiService);
  }

  public ChimeraHttpServer(
      int port,
      CampaignApiService campaignApiService,
      ReviewApiService reviewApiService,
      ApiKeyAuthService apiKeyAuthService,
      RateLimiter writeRateLimiter)
      throws IOException {
    this(
        port,
        campaignApiService,
        reviewApiService,
        apiKeyAuthService,
        writeRateLimiter,
        null,
        null);
  }

  public ChimeraHttpServer(
      int port,
      CampaignApiService campaignApiService,
      ReviewApiService reviewApiService,
      ApiKeyAuthService apiKeyAuthService,
      RateLimiter writeRateLimiter,
      TelemetryApiService telemetryApiService)
      throws IOException {
    this(
        port,
        campaignApiService,
        reviewApiService,
        apiKeyAuthService,
        writeRateLimiter,
        telemetryApiService,
        null);
  }

  public ChimeraHttpServer(
      int port,
      CampaignApiService campaignApiService,
      ReviewApiService reviewApiService,
      ApiKeyAuthService apiKeyAuthService,
      RateLimiter writeRateLimiter,
      TelemetryApiService telemetryApiService,
      DeadLetterApiService deadLetterApiService)
      throws IOException {
    this(
        port,
        campaignApiService,
        reviewApiService,
        apiKeyAuthService,
        JwtAuthService.fromEnvironment(),
        writeRateLimiter,
        telemetryApiService,
        deadLetterApiService);
  }

  public ChimeraHttpServer(
      int port,
      CampaignApiService campaignApiService,
      ReviewApiService reviewApiService,
      ApiKeyAuthService apiKeyAuthService,
      JwtAuthService jwtAuthService,
      RateLimiter writeRateLimiter)
      throws IOException {
    this(
        port,
        campaignApiService,
        reviewApiService,
        apiKeyAuthService,
        jwtAuthService,
        writeRateLimiter,
        null,
        null);
  }

  public ChimeraHttpServer(
      int port,
      CampaignApiService campaignApiService,
      ReviewApiService reviewApiService,
      ApiKeyAuthService apiKeyAuthService,
      JwtAuthService jwtAuthService,
      RateLimiter writeRateLimiter,
      TelemetryApiService telemetryApiService)
      throws IOException {
    this(
        port,
        campaignApiService,
        reviewApiService,
        apiKeyAuthService,
        jwtAuthService,
        writeRateLimiter,
        telemetryApiService,
        null);
  }

  public ChimeraHttpServer(
      int port,
      CampaignApiService campaignApiService,
      ReviewApiService reviewApiService,
      ApiKeyAuthService apiKeyAuthService,
      JwtAuthService jwtAuthService,
      RateLimiter writeRateLimiter,
      TelemetryApiService telemetryApiService,
      DeadLetterApiService deadLetterApiService)
      throws IOException {
    this.campaignApiService = campaignApiService;
    this.reviewApiService = reviewApiService;
    this.deadLetterApiService = deadLetterApiService;
    this.telemetryApiService = telemetryApiService;
    this.apiKeyAuthService = apiKeyAuthService;
    this.jwtAuthService = jwtAuthService;
    this.writeRateLimiter = writeRateLimiter;
    this.corsAllowedOrigins = parseCorsAllowedOrigins();
    this.server = HttpServer.create(new InetSocketAddress(port), 0);
    this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    this.server.createContext("/api/campaigns", this::handleCampaigns);
    this.server.createContext("/api/tasks", this::handleTasks);
    this.server.createContext("/api/telemetry", this::handleTelemetry);
    this.server.createContext("/api/review", this::handleReview);
    this.server.createContext("/api/dead-letter", this::handleDeadLetter);
    this.server.createContext("/health", this::handleHealth);
    this.server.createContext("/ready", this::handleReady);
    this.server.createContext("/openapi.yaml", this::handleOpenApi);
  }

  public void start() {
    server.start();
  }

  public int port() {
    return server.getAddress().getPort();
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private void handleCampaigns(HttpExchange exchange) throws IOException {
    if (handleCorsPreflight(exchange)) {
      return;
    }

    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendJson(exchange, 405, new ApiError("method_not_allowed", "Use POST /api/campaigns"), null);
      return;
    }

    try {
      RequestContext context = authorize(exchange, UserRole.OPERATOR);
      enforceRateLimit(context, exchange.getRequestURI().getPath());
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      CreateCampaignRequest request = objectMapper.readValue(body, CreateCampaignRequest.class);
      List<Task> tasks = campaignApiService.createCampaign(context.tenantId(), request);
      sendJson(exchange, 201, tasks, context.requestId());
    } catch (ApiException ex) {
      sendJson(exchange, ex.statusCode(), ex.apiError(), null);
    } catch (IllegalArgumentException ex) {
      sendJson(exchange, 400, new ApiError("invalid_request", ex.getMessage()), null);
    } catch (IOException ex) {
      sendJson(exchange, 400, new ApiError("invalid_json", ex.getMessage()), null);
    } catch (Exception ex) {
      sendJson(exchange, 500, new ApiError("internal_error", ex.getMessage()), null);
    }
  }

  private void handleTasks(HttpExchange exchange) throws IOException {
    if (handleCorsPreflight(exchange)) {
      return;
    }

    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendJson(exchange, 405, new ApiError("method_not_allowed", "Use GET /api/tasks"), null);
      return;
    }

    try {
      RequestContext context =
          authorize(exchange, UserRole.OPERATOR, UserRole.REVIEWER, UserRole.VIEWER);
      sendJson(
          exchange, 200, campaignApiService.listTasks(context.tenantId()), context.requestId());
    } catch (ApiException ex) {
      sendJson(exchange, ex.statusCode(), ex.apiError(), null);
    } catch (Exception ex) {
      sendJson(exchange, 500, new ApiError("internal_error", ex.getMessage()), null);
    }
  }

  private void handleReview(HttpExchange exchange) throws IOException {
    if (handleCorsPreflight(exchange)) {
      return;
    }

    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendJson(
          exchange,
          405,
          new ApiError("method_not_allowed", "Use POST /api/review/{taskId}/approve|reject"),
          null);
      return;
    }

    String[] parts = exchange.getRequestURI().getPath().split("/");
    if (parts.length != 5) {
      sendJson(
          exchange,
          404,
          new ApiError("not_found", "Expected /api/review/{taskId}/approve|reject"),
          null);
      return;
    }

    String taskIdRaw = parts[3];
    String action = parts[4];

    try {
      RequestContext context = authorize(exchange, UserRole.REVIEWER);
      enforceRateLimit(context, exchange.getRequestURI().getPath());
      UUID taskId = UUID.fromString(taskIdRaw);

      ReviewDecision decision;
      if ("approve".equalsIgnoreCase(action)) {
        decision = reviewApiService.approve(context.tenantId(), taskId);
      } else if ("reject".equalsIgnoreCase(action)) {
        decision = reviewApiService.reject(context.tenantId(), taskId);
      } else {
        sendJson(
            exchange, 404, new ApiError("not_found", "Unknown review action: " + action), null);
        return;
      }

      sendJson(exchange, 200, decision, context.requestId());
    } catch (ApiException ex) {
      sendJson(exchange, ex.statusCode(), ex.apiError(), null);
    } catch (IllegalArgumentException ex) {
      sendJson(exchange, 400, new ApiError("invalid_request", ex.getMessage()), null);
    }
  }

  private void handleTelemetry(HttpExchange exchange) throws IOException {
    if (handleCorsPreflight(exchange)) {
      return;
    }

    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendJson(exchange, 405, new ApiError("method_not_allowed", "Use GET /api/telemetry"), null);
      return;
    }

    if (telemetryApiService == null) {
      sendJson(
          exchange,
          503,
          new ApiError("service_unavailable", "Telemetry service is not configured"),
          null);
      return;
    }

    try {
      RequestContext context =
          authorize(exchange, UserRole.OPERATOR, UserRole.REVIEWER, UserRole.VIEWER);
      sendJson(
          exchange, 200, telemetryApiService.snapshot(context.tenantId()), context.requestId());
    } catch (ApiException ex) {
      sendJson(exchange, ex.statusCode(), ex.apiError(), null);
    } catch (IllegalArgumentException ex) {
      sendJson(exchange, 400, new ApiError("invalid_request", ex.getMessage()), null);
    } catch (Exception ex) {
      sendJson(exchange, 500, new ApiError("internal_error", ex.getMessage()), null);
    }
  }

  private void handleDeadLetter(HttpExchange exchange) throws IOException {
    if (handleCorsPreflight(exchange)) {
      return;
    }

    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendJson(
          exchange,
          405,
          new ApiError("method_not_allowed", "Use POST /api/dead-letter/{taskId}/replay"),
          null);
      return;
    }

    if (deadLetterApiService == null) {
      sendJson(
          exchange,
          503,
          new ApiError("service_unavailable", "Dead-letter service is not configured"),
          null);
      return;
    }

    String[] parts = exchange.getRequestURI().getPath().split("/");
    if (parts.length != 5) {
      sendJson(
          exchange,
          404,
          new ApiError("not_found", "Expected /api/dead-letter/{taskId}/replay"),
          null);
      return;
    }

    String taskIdRaw = parts[3];
    String action = parts[4];
    if (!"replay".equalsIgnoreCase(action)) {
      sendJson(
          exchange, 404, new ApiError("not_found", "Unknown dead-letter action: " + action), null);
      return;
    }

    try {
      RequestContext context = authorize(exchange, UserRole.OPERATOR);
      enforceRateLimit(context, exchange.getRequestURI().getPath());
      UUID taskId = UUID.fromString(taskIdRaw);
      Task replayed = deadLetterApiService.replay(context.tenantId(), taskId);
      sendJson(exchange, 200, replayed, context.requestId());
    } catch (ApiException ex) {
      sendJson(exchange, ex.statusCode(), ex.apiError(), null);
    } catch (IllegalStateException ex) {
      sendJson(exchange, 409, new ApiError("invalid_state", ex.getMessage()), null);
    } catch (IllegalArgumentException ex) {
      int status = ex.getMessage() != null && ex.getMessage().contains("not found") ? 404 : 400;
      sendJson(exchange, status, new ApiError("invalid_request", ex.getMessage()), null);
    } catch (Exception ex) {
      sendJson(exchange, 500, new ApiError("internal_error", ex.getMessage()), null);
    }
  }

  private void handleHealth(HttpExchange exchange) throws IOException {
    if (handleCorsPreflight(exchange)) {
      return;
    }

    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())
        && !"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendJson(exchange, 405, new ApiError("method_not_allowed", "Use GET /health"), null);
      return;
    }

    sendJson(exchange, 200, Map.of("status", "up"), null);
  }

  private void handleReady(HttpExchange exchange) throws IOException {
    if (handleCorsPreflight(exchange)) {
      return;
    }

    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())
        && !"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendJson(exchange, 405, new ApiError("method_not_allowed", "Use GET /ready"), null);
      return;
    }

    sendJson(exchange, 200, Map.of("status", "ready"), null);
  }

  private void handleOpenApi(HttpExchange exchange) throws IOException {
    if (handleCorsPreflight(exchange)) {
      return;
    }

    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())
        && !"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendJson(exchange, 405, new ApiError("method_not_allowed", "Use GET /openapi.yaml"), null);
      return;
    }

    try (InputStream stream = getClass().getClassLoader().getResourceAsStream("openapi.yaml")) {
      if (stream == null) {
        sendJson(exchange, 404, new ApiError("not_found", "openapi.yaml not found"), null);
        return;
      }

      byte[] payload = stream.readAllBytes();
      applyCorsHeaders(exchange);
      exchange.getResponseHeaders().set("Content-Type", "application/yaml");
      exchange.sendResponseHeaders(200, payload.length);
      exchange.getResponseBody().write(payload);
      exchange.close();
    }
  }

  private RequestContext authorize(HttpExchange exchange, UserRole... allowedRoles) {
    RequestContext context = extractContext(exchange);
    boolean allowed = Arrays.stream(allowedRoles).anyMatch(role -> role == context.role());
    if (!allowed) {
      throw new ApiException(
          403, "forbidden", "Role " + context.role() + " is not allowed to access this endpoint");
    }
    return context;
  }

  private RequestContext extractContext(HttpExchange exchange) {
    ApiKeyIdentity identity = authenticateIdentity(exchange);

    String tenantId = exchange.getRequestHeaders().getFirst(HEADER_TENANT);
    if (tenantId == null || tenantId.isBlank()) {
      throw new ApiException(401, "unauthorized", "Missing required header " + HEADER_TENANT);
    }

    if (!tenantId.matches("^[a-zA-Z0-9_-]{2,64}$")) {
      throw new ApiException(401, "unauthorized", "Invalid tenant identifier format");
    }

    String roleRaw = exchange.getRequestHeaders().getFirst(HEADER_ROLE);
    UserRole role;
    try {
      role = UserRole.parse(roleRaw);
    } catch (Exception ex) {
      throw new ApiException(401, "unauthorized", "Invalid role header");
    }

    if (!identity.tenantId().equals(tenantId)) {
      throw new ApiException(
          403, "forbidden", "Authenticated identity is not authorized for tenant " + tenantId);
    }
    if (!identity.allowedRoles().contains(role)) {
      throw new ApiException(
          403,
          "forbidden",
          "Authenticated identity is not authorized to assume role "
              + role
              + " for tenant "
              + tenantId);
    }

    String requestId = exchange.getRequestHeaders().getFirst(HEADER_REQUEST_ID);
    if (requestId == null || requestId.isBlank()) {
      requestId = UUID.randomUUID().toString();
    }

    return new RequestContext(requestId, tenantId, role);
  }

  private ApiKeyIdentity authenticateIdentity(HttpExchange exchange) {
    String authorization = exchange.getRequestHeaders().getFirst(HEADER_AUTHORIZATION);
    if (authorization != null && !authorization.isBlank()) {
      if (!jwtAuthService.isConfigured()) {
        throw new ApiException(401, "unauthorized", "Bearer authentication is not configured");
      }
      return jwtAuthService
          .authenticate(authorization)
          .orElseThrow(
              () -> new ApiException(401, "unauthorized", "Missing or invalid bearer token"));
    }

    String apiKey = exchange.getRequestHeaders().getFirst(HEADER_API_KEY);
    return apiKeyAuthService
        .authenticate(apiKey)
        .orElseThrow(
            () -> new ApiException(401, "unauthorized", "Missing or invalid " + HEADER_API_KEY));
  }

  private void sendJson(HttpExchange exchange, int statusCode, Object body, String requestId)
      throws IOException {
    byte[] payload = objectMapper.writeValueAsBytes(body);
    applyCorsHeaders(exchange);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    String requestIdValue = requestId == null ? UUID.randomUUID().toString() : requestId;
    exchange.getResponseHeaders().set(HEADER_REQUEST_ID, requestIdValue);
    exchange.sendResponseHeaders(statusCode, payload.length);
    exchange.getResponseBody().write(payload);
    audit(exchange, requestIdValue, statusCode);
    exchange.close();
  }

  private void enforceRateLimit(RequestContext context, String path) {
    String key = context.tenantId() + ":" + path;
    if (!writeRateLimiter.allow(key)) {
      throw new ApiException(
          429,
          "rate_limited",
          "Rate limit exceeded for tenant " + context.tenantId() + " on path " + path);
    }
  }

  private void audit(HttpExchange exchange, String requestId, int statusCode) {
    String tenant = exchange.getRequestHeaders().getFirst(HEADER_TENANT);
    String role = exchange.getRequestHeaders().getFirst(HEADER_ROLE);
    LOGGER.info(
        "audit request_id={} method={} path={} status={} tenant={} role={}",
        requestId,
        exchange.getRequestMethod(),
        exchange.getRequestURI().getPath(),
        statusCode,
        tenant == null ? "anonymous" : tenant,
        role == null ? "unknown" : role);
  }

  private static ObjectMapper defaultObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return mapper;
  }

  private boolean handleCorsPreflight(HttpExchange exchange) throws IOException {
    if (!"OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
      return false;
    }

    String origin = exchange.getRequestHeaders().getFirst(HEADER_ORIGIN);
    if (origin != null && !origin.isBlank() && !isOriginAllowed(origin)) {
      sendJson(exchange, 403, new ApiError("cors_forbidden", "Origin is not allowed"), null);
      return true;
    }

    applyCorsHeaders(exchange);
    exchange.sendResponseHeaders(204, -1);
    exchange.close();
    return true;
  }

  private void applyCorsHeaders(HttpExchange exchange) {
    String origin = exchange.getRequestHeaders().getFirst(HEADER_ORIGIN);
    if (origin == null || origin.isBlank()) {
      return;
    }
    if (!isOriginAllowed(origin)) {
      return;
    }
    exchange.getResponseHeaders().set(HEADER_AC_ALLOW_ORIGIN, origin);
    exchange.getResponseHeaders().set(HEADER_AC_ALLOW_HEADERS, ALLOWED_CORS_HEADERS);
    exchange.getResponseHeaders().set(HEADER_AC_ALLOW_METHODS, ALLOWED_CORS_METHODS);
    exchange.getResponseHeaders().set(HEADER_AC_MAX_AGE, CORS_MAX_AGE_SECONDS);
    exchange.getResponseHeaders().set(HEADER_VARY, HEADER_ORIGIN);
  }

  private boolean isOriginAllowed(String origin) {
    if (origin == null || origin.isBlank()) {
      return false;
    }
    return corsAllowedOrigins.contains("*") || corsAllowedOrigins.contains(origin.trim());
  }

  private static List<String> parseCorsAllowedOrigins() {
    String raw =
        System.getenv().getOrDefault(ENV_CORS_ALLOWED_ORIGINS, DEFAULT_CORS_ALLOWED_ORIGINS);
    return Arrays.stream(raw.split(","))
        .map(String::trim)
        .filter(token -> !token.isBlank())
        .toList();
  }
}
