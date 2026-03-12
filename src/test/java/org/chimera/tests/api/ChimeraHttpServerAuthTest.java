package org.chimera.tests.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.chimera.api.ApiKeyAuthService;
import org.chimera.api.ApiKeyIdentity;
import org.chimera.api.CampaignApiService;
import org.chimera.api.ChimeraHttpServer;
import org.chimera.api.DeadLetterApiService;
import org.chimera.api.JwtAuthService;
import org.chimera.api.RequestRateLimiter;
import org.chimera.api.ReviewApiService;
import org.chimera.api.TelemetryApiService;
import org.chimera.api.UserRole;
import org.chimera.infrastructure.queue.InMemoryQueuePort;
import org.chimera.model.Task;
import org.chimera.persistence.InMemoryTaskRepository;
import org.chimera.persistence.InMemoryWalletLedgerRepository;
import org.chimera.planner.PlannerService;
import org.junit.jupiter.api.Test;

class ChimeraHttpServerAuthTest {
  private static final String ALPHA_KEY = "alpha-test-key";
  private static final String BETA_KEY = "beta-test-key";
  private static final byte[] JWT_SECRET =
      "jwt-integration-secret".getBytes(StandardCharsets.UTF_8);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  void shouldRequireAuthenticationTenantAndRoleHeaders() throws Exception {
    try (ChimeraHttpServer server = startServer(30)) {
      HttpResponse<String> response =
          httpClient.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(baseUrl(server) + "/api/campaigns"))
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString("{\"goal\":\"x\"}"))
                  .timeout(Duration.ofSeconds(5))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(401);
      assertThat(response.body()).contains("unauthorized");
    }
  }

  @Test
  void shouldRejectInsufficientEndpointRole() throws Exception {
    try (ChimeraHttpServer server = startServer(30)) {
      HttpResponse<String> response =
          httpClient.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(baseUrl(server) + "/api/campaigns"))
                  .header("Content-Type", "application/json")
                  .header("X-Api-Key", ALPHA_KEY)
                  .header("X-Tenant-Id", "tenant-alpha")
                  .header("X-Role", "viewer")
                  .POST(
                      HttpRequest.BodyPublishers.ofString(
                          "{\"goal\":\"Launch secure campaign\",\"workerId\":\"worker-1\"}"))
                  .timeout(Duration.ofSeconds(5))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(403);
      assertThat(response.body()).contains("forbidden");
    }
  }

  @Test
  void shouldRejectTenantMismatchForApiKey() throws Exception {
    try (ChimeraHttpServer server = startServer(30)) {
      HttpResponse<String> response =
          httpClient.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(baseUrl(server) + "/api/tasks"))
                  .header("X-Api-Key", ALPHA_KEY)
                  .header("X-Tenant-Id", "tenant-beta")
                  .header("X-Role", "viewer")
                  .GET()
                  .timeout(Duration.ofSeconds(5))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(403);
      assertThat(response.body()).contains("not authorized for tenant");
    }
  }

  @Test
  void shouldScopeTaskListingByTenant() throws Exception {
    try (ChimeraHttpServer server = startServer(30)) {
      String createBody =
          "{\"goal\":\"Tenant scoped campaign\",\"workerId\":\"worker-1\",\"requiredResources\":[\"news://ethiopia/fashion/trends\"]}";

      HttpResponse<String> createResponse =
          httpClient.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(baseUrl(server) + "/api/campaigns"))
                  .header("Content-Type", "application/json")
                  .header("X-Api-Key", ALPHA_KEY)
                  .header("X-Tenant-Id", "tenant-alpha")
                  .header("X-Role", "operator")
                  .POST(HttpRequest.BodyPublishers.ofString(createBody))
                  .timeout(Duration.ofSeconds(5))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      assertThat(createResponse.statusCode()).isEqualTo(201);
      assertThat(createResponse.body()).contains("tenant-alpha");

      HttpResponse<String> alphaList =
          httpClient.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(baseUrl(server) + "/api/tasks"))
                  .header("X-Api-Key", ALPHA_KEY)
                  .header("X-Tenant-Id", "tenant-alpha")
                  .header("X-Role", "viewer")
                  .GET()
                  .timeout(Duration.ofSeconds(5))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      HttpResponse<String> betaList =
          httpClient.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(baseUrl(server) + "/api/tasks"))
                  .header("X-Api-Key", BETA_KEY)
                  .header("X-Tenant-Id", "tenant-beta")
                  .header("X-Role", "viewer")
                  .GET()
                  .timeout(Duration.ofSeconds(5))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      assertThat(alphaList.statusCode()).isEqualTo(200);
      assertThat(alphaList.body()).contains("tenant-alpha");
      assertThat(betaList.statusCode()).isEqualTo(200);
      assertThat(betaList.body()).isEqualTo("[]");
    }
  }

  @Test
  void shouldReturnTenantTelemetryForAuthorizedViewer() throws Exception {
    try (ChimeraHttpServer server = startServer(30)) {
      HttpResponse<String> response =
          httpClient.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(baseUrl(server) + "/api/telemetry"))
                  .header("X-Api-Key", ALPHA_KEY)
                  .header("X-Tenant-Id", "tenant-alpha")
                  .header("X-Role", "viewer")
                  .GET()
                  .timeout(Duration.ofSeconds(5))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).contains("tenant-alpha");
      assertThat(response.body()).contains("taskQueueDepth");
      assertThat(response.body()).contains("reviewQueueDepth");
      assertThat(response.body()).contains("deadLetterQueueDepth");
      assertThat(response.body()).contains("walletProvider");
      assertThat(response.body()).contains("trendSignalsToday");
      assertThat(response.body()).contains("topTrendTopicsToday");
      assertThat(response.body()).contains("workerP95LatencyMs");
      assertThat(response.body()).contains("workerP50LatencyMs");
      assertThat(response.body()).contains("retryAttemptsToday");
      assertThat(response.body()).contains("deadLetteredTasksToday");
    }
  }

  @Test
  void shouldHandleCorsPreflightWithoutAuthentication() throws Exception {
    try (ChimeraHttpServer server = startServer(30)) {
      HttpResponse<String> response =
          httpClient.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(baseUrl(server) + "/api/campaigns"))
                  .header("Origin", "http://127.0.0.1:4173")
                  .header("Access-Control-Request-Method", "POST")
                  .header(
                      "Access-Control-Request-Headers", "content-type,x-api-key,x-tenant-id,x-role")
                  .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                  .timeout(Duration.ofSeconds(5))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(204);
      assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
          .contains("http://127.0.0.1:4173");
      assertThat(response.headers().firstValue("Access-Control-Allow-Methods").orElse(""))
          .contains("POST");
      assertThat(response.headers().firstValue("Access-Control-Allow-Headers").orElse(""))
          .contains("X-Api-Key");
    }
  }

  @Test
  void shouldAuthorizeCampaignWriteWithBearerToken() throws Exception {
    try (ChimeraHttpServer server = startServer(30)) {
      String token =
          createBearerToken("tenant-alpha", List.of("operator"), Instant.now().plusSeconds(120));
      String body =
          "{\"goal\":\"JWT campaign\",\"workerId\":\"worker-1\",\"requiredResources\":[\"news://ethiopia/fashion/trends\"]}";

      HttpResponse<String> response =
          httpClient.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(baseUrl(server) + "/api/campaigns"))
                  .header("Content-Type", "application/json")
                  .header("Authorization", "Bearer " + token)
                  .header("X-Tenant-Id", "tenant-alpha")
                  .header("X-Role", "operator")
                  .POST(HttpRequest.BodyPublishers.ofString(body))
                  .timeout(Duration.ofSeconds(5))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(201);
      assertThat(response.body()).contains("tenant-alpha");
    }
  }

  @Test
  void shouldRejectBearerTokenWithInvalidSignature() throws Exception {
    try (ChimeraHttpServer server = startServer(30)) {
      String token =
          createBearerTokenWithSecret(
              "tenant-alpha",
              List.of("operator"),
              Instant.now().plusSeconds(120),
              "wrong-secret".getBytes(StandardCharsets.UTF_8));

      HttpResponse<String> response =
          httpClient.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(baseUrl(server) + "/api/campaigns"))
                  .header("Content-Type", "application/json")
                  .header("Authorization", "Bearer " + token)
                  .header("X-Tenant-Id", "tenant-alpha")
                  .header("X-Role", "operator")
                  .POST(HttpRequest.BodyPublishers.ofString("{\"goal\":\"x\"}"))
                  .timeout(Duration.ofSeconds(5))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(401);
      assertThat(response.body()).contains("invalid bearer token");
    }
  }

  @Test
  void shouldEnforceRateLimitOnCampaignWrites() throws Exception {
    try (ChimeraHttpServer server = startServer(1)) {
      String body =
          "{\"goal\":\"Rate limited campaign\",\"workerId\":\"worker-1\",\"requiredResources\":[\"news://ethiopia/fashion/trends\"]}";

      HttpResponse<String> first =
          httpClient.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(baseUrl(server) + "/api/campaigns"))
                  .header("Content-Type", "application/json")
                  .header("X-Api-Key", ALPHA_KEY)
                  .header("X-Tenant-Id", "tenant-alpha")
                  .header("X-Role", "operator")
                  .POST(HttpRequest.BodyPublishers.ofString(body))
                  .timeout(Duration.ofSeconds(5))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      HttpResponse<String> second =
          httpClient.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(baseUrl(server) + "/api/campaigns"))
                  .header("Content-Type", "application/json")
                  .header("X-Api-Key", ALPHA_KEY)
                  .header("X-Tenant-Id", "tenant-alpha")
                  .header("X-Role", "operator")
                  .POST(HttpRequest.BodyPublishers.ofString(body))
                  .timeout(Duration.ofSeconds(5))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      assertThat(first.statusCode()).isEqualTo(201);
      assertThat(second.statusCode()).isEqualTo(429);
      assertThat(second.body()).contains("rate_limited");
    }
  }

  @Test
  void shouldReplayRejectedTaskWithOperatorRole() throws Exception {
    try (ChimeraHttpServer server = startServer(30)) {
      String createBody =
          "{\"goal\":\"Replay candidate campaign\",\"workerId\":\"worker-1\",\"requiredResources\":[\"news://ethiopia/fashion/trends\"]}";

      HttpResponse<String> createResponse =
          httpClient.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(baseUrl(server) + "/api/campaigns"))
                  .header("Content-Type", "application/json")
                  .header("X-Api-Key", ALPHA_KEY)
                  .header("X-Tenant-Id", "tenant-alpha")
                  .header("X-Role", "operator")
                  .POST(HttpRequest.BodyPublishers.ofString(createBody))
                  .timeout(Duration.ofSeconds(5))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(createResponse.statusCode()).isEqualTo(201);

      String taskId = OBJECT_MAPPER.readTree(createResponse.body()).get(0).get("taskId").asText();

      HttpResponse<String> rejectResponse =
          httpClient.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(baseUrl(server) + "/api/review/" + taskId + "/reject"))
                  .header("X-Api-Key", ALPHA_KEY)
                  .header("X-Tenant-Id", "tenant-alpha")
                  .header("X-Role", "reviewer")
                  .POST(HttpRequest.BodyPublishers.noBody())
                  .timeout(Duration.ofSeconds(5))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(rejectResponse.statusCode()).isEqualTo(200);

      HttpResponse<String> replayResponse =
          httpClient.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(baseUrl(server) + "/api/dead-letter/" + taskId + "/replay"))
                  .header("X-Api-Key", ALPHA_KEY)
                  .header("X-Tenant-Id", "tenant-alpha")
                  .header("X-Role", "operator")
                  .POST(HttpRequest.BodyPublishers.noBody())
                  .timeout(Duration.ofSeconds(5))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      assertThat(replayResponse.statusCode()).isEqualTo(200);
      assertThat(replayResponse.body()).contains("\"status\":\"PENDING\"");
    }
  }

  @Test
  void shouldRejectDeadLetterReplayForViewerRole() throws Exception {
    try (ChimeraHttpServer server = startServer(30)) {
      String taskId = UUID.randomUUID().toString();

      HttpResponse<String> replayResponse =
          httpClient.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(baseUrl(server) + "/api/dead-letter/" + taskId + "/replay"))
                  .header("X-Api-Key", ALPHA_KEY)
                  .header("X-Tenant-Id", "tenant-alpha")
                  .header("X-Role", "viewer")
                  .POST(HttpRequest.BodyPublishers.noBody())
                  .timeout(Duration.ofSeconds(5))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      assertThat(replayResponse.statusCode()).isEqualTo(403);
      assertThat(replayResponse.body()).contains("forbidden");
    }
  }

  @Test
  void shouldRunEndToEndCampaignReviewReplayJourneyAcrossAuthModes() throws Exception {
    try (ChimeraHttpServer server = startServer(60)) {
      String operatorToken =
          createBearerToken("tenant-alpha", List.of("operator"), Instant.now().plusSeconds(120));
      String createBody =
          "{\"goal\":\"E2E campaign journey\",\"workerId\":\"worker-1\",\"requiredResources\":[\"news://ethiopia/fashion/trends\"]}";

      HttpResponse<String> createResponse =
          httpClient.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(baseUrl(server) + "/api/campaigns"))
                  .header("Content-Type", "application/json")
                  .header("Authorization", "Bearer " + operatorToken)
                  .header("X-Tenant-Id", "tenant-alpha")
                  .header("X-Role", "operator")
                  .POST(HttpRequest.BodyPublishers.ofString(createBody))
                  .timeout(Duration.ofSeconds(5))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(createResponse.statusCode()).isEqualTo(201);
      String taskId = OBJECT_MAPPER.readTree(createResponse.body()).get(0).get("taskId").asText();

      HttpResponse<String> listResponse =
          httpClient.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(baseUrl(server) + "/api/tasks"))
                  .header("X-Api-Key", ALPHA_KEY)
                  .header("X-Tenant-Id", "tenant-alpha")
                  .header("X-Role", "viewer")
                  .GET()
                  .timeout(Duration.ofSeconds(5))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(listResponse.statusCode()).isEqualTo(200);
      assertThat(listResponse.body()).contains(taskId);

      HttpResponse<String> rejectResponse =
          httpClient.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(baseUrl(server) + "/api/review/" + taskId + "/reject"))
                  .header("X-Api-Key", ALPHA_KEY)
                  .header("X-Tenant-Id", "tenant-alpha")
                  .header("X-Role", "reviewer")
                  .POST(HttpRequest.BodyPublishers.noBody())
                  .timeout(Duration.ofSeconds(5))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(rejectResponse.statusCode()).isEqualTo(200);
      assertThat(rejectResponse.body()).contains("\"nextStatus\":\"REJECTED\"");

      HttpResponse<String> firstReplayResponse =
          httpClient.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(baseUrl(server) + "/api/dead-letter/" + taskId + "/replay"))
                  .header("Authorization", "Bearer " + operatorToken)
                  .header("X-Tenant-Id", "tenant-alpha")
                  .header("X-Role", "operator")
                  .POST(HttpRequest.BodyPublishers.noBody())
                  .timeout(Duration.ofSeconds(5))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(firstReplayResponse.statusCode()).isEqualTo(200);
      assertThat(firstReplayResponse.body()).contains("\"status\":\"PENDING\"");

      HttpResponse<String> secondRejectResponse =
          httpClient.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(baseUrl(server) + "/api/review/" + taskId + "/reject"))
                  .header("X-Api-Key", ALPHA_KEY)
                  .header("X-Tenant-Id", "tenant-alpha")
                  .header("X-Role", "reviewer")
                  .POST(HttpRequest.BodyPublishers.noBody())
                  .timeout(Duration.ofSeconds(5))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(secondRejectResponse.statusCode()).isEqualTo(200);

      HttpResponse<String> secondReplayResponse =
          httpClient.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(baseUrl(server) + "/api/dead-letter/" + taskId + "/replay"))
                  .header("Authorization", "Bearer " + operatorToken)
                  .header("X-Tenant-Id", "tenant-alpha")
                  .header("X-Role", "operator")
                  .POST(HttpRequest.BodyPublishers.noBody())
                  .timeout(Duration.ofSeconds(5))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(secondReplayResponse.statusCode()).isEqualTo(429);
      assertThat(secondReplayResponse.body()).contains("replay_cooldown_active");

      HttpResponse<String> telemetryResponse =
          httpClient.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(baseUrl(server) + "/api/telemetry"))
                  .header("X-Api-Key", ALPHA_KEY)
                  .header("X-Tenant-Id", "tenant-alpha")
                  .header("X-Role", "viewer")
                  .GET()
                  .timeout(Duration.ofSeconds(5))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(telemetryResponse.statusCode()).isEqualTo(200);
      assertThat(telemetryResponse.body()).contains("taskQueueDepth");
      assertThat(telemetryResponse.body()).contains("deadLetterQueueDepth");
    }
  }

  private ChimeraHttpServer startServer(int campaignWritesPerMinute) throws IOException {
    InMemoryQueuePort<Task> taskQueue = new InMemoryQueuePort<>();
    InMemoryQueuePort<UUID> reviewQueue = new InMemoryQueuePort<>();
    InMemoryQueuePort<UUID> deadLetterQueue = new InMemoryQueuePort<>();
    PlannerService planner = new PlannerService(taskQueue);
    InMemoryTaskRepository repository = new InMemoryTaskRepository();
    InMemoryWalletLedgerRepository walletLedgerRepository = new InMemoryWalletLedgerRepository();
    CampaignApiService campaignApiService = new CampaignApiService(planner, repository);
    ReviewApiService reviewApiService = new ReviewApiService(repository);
    DeadLetterApiService deadLetterApiService =
        new DeadLetterApiService(repository, taskQueue, deadLetterQueue);
    TelemetryApiService telemetryApiService =
        new TelemetryApiService(
            repository,
            taskQueue,
            reviewQueue,
            deadLetterQueue,
            walletLedgerRepository,
            new org.chimera.orchestrator.InMemoryQueueGovernanceMetrics(),
            "in-memory",
            "simulated",
            new java.math.BigDecimal("500.00"));

    ApiKeyAuthService authService =
        new ApiKeyAuthService(
            Map.of(
                ALPHA_KEY,
                new ApiKeyIdentity(
                    "tenant-alpha", Set.of(UserRole.OPERATOR, UserRole.REVIEWER, UserRole.VIEWER)),
                BETA_KEY,
                new ApiKeyIdentity(
                    "tenant-beta", Set.of(UserRole.OPERATOR, UserRole.REVIEWER, UserRole.VIEWER))));

    ChimeraHttpServer server =
        new ChimeraHttpServer(
            0,
            campaignApiService,
            reviewApiService,
            authService,
            new JwtAuthService(JWT_SECRET, "chimera-test"),
            new RequestRateLimiter(campaignWritesPerMinute, Duration.ofMinutes(1)),
            telemetryApiService,
            deadLetterApiService);
    server.start();
    return server;
  }

  private static String createBearerToken(String tenantId, List<String> roles, Instant expiresAt)
      throws Exception {
    return createBearerTokenWithSecret(tenantId, roles, expiresAt, JWT_SECRET);
  }

  private static String createBearerTokenWithSecret(
      String tenantId, List<String> roles, Instant expiresAt, byte[] secret) throws Exception {
    Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
    Map<String, Object> payload =
        Map.of(
            "iss",
            "chimera-test",
            "tenant_id",
            tenantId,
            "roles",
            roles,
            "exp",
            expiresAt.getEpochSecond());

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

  private String baseUrl(ChimeraHttpServer server) {
    return "http://localhost:" + server.port();
  }
}
