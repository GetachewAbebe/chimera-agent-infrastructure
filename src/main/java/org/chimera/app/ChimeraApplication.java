package org.chimera.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.chimera.action.SocialPublishingService;
import org.chimera.api.CampaignApiService;
import org.chimera.api.ChimeraHttpServer;
import org.chimera.api.DeadLetterApiService;
import org.chimera.api.RateLimiter;
import org.chimera.api.ReviewApiService;
import org.chimera.api.TelemetryApiService;
import org.chimera.cognitive.ClasspathSoulPersonaLoader;
import org.chimera.cognitive.CognitiveContextAssembler;
import org.chimera.cognitive.InMemoryMemoryRecall;
import org.chimera.creative.CreativeEngineService;
import org.chimera.infrastructure.queue.InMemoryQueuePort;
import org.chimera.infrastructure.queue.QueuePort;
import org.chimera.infrastructure.queue.RedisTaskQueuePort;
import org.chimera.infrastructure.queue.RedisUuidQueuePort;
import org.chimera.judge.JudgeService;
import org.chimera.mcp.HttpMcpResourceClient;
import org.chimera.mcp.McpResourceClient;
import org.chimera.mcp.McpToolClient;
import org.chimera.mcp.McpToolResult;
import org.chimera.model.Task;
import org.chimera.openclaw.McpOpenClawStatusPublisher;
import org.chimera.orchestrator.InMemoryQueueGovernanceMetrics;
import org.chimera.orchestrator.TaskOrchestratorService;
import org.chimera.perception.KeywordSemanticRelevanceScorer;
import org.chimera.perception.McpPerceptionService;
import org.chimera.persistence.DeadLetterReplayAuditRepository;
import org.chimera.persistence.PersistenceBootstrap;
import org.chimera.persistence.PersistenceBundle;
import org.chimera.persistence.TaskRepository;
import org.chimera.persistence.TrendSignalRepository;
import org.chimera.persistence.WalletLedgerRepository;
import org.chimera.planner.PlannerService;
import org.chimera.security.BudgetGovernor;
import org.chimera.security.EnvironmentSecretProvider;
import org.chimera.security.SecretProvider;
import org.chimera.wallet.CoinbaseAgentKitWalletProvider;
import org.chimera.wallet.HttpWalletTransport;
import org.chimera.wallet.SimulatedWalletProvider;
import org.chimera.wallet.WalletExecutionService;
import org.chimera.wallet.WalletProvider;
import org.chimera.worker.WorkerService;

public final class ChimeraApplication {
  private static final String TASK_QUEUE_KEY = "task_queue";
  private static final String REVIEW_QUEUE_KEY = "review_queue";
  private static final String DEAD_LETTER_QUEUE_KEY = "dead_letter_queue";

  private ChimeraApplication() {}

  public static void main(String[] args) {
    QueueRuntime queueRuntime = initializeQueueRuntime();
    QueuePort<Task> taskQueue = queueRuntime.taskQueue();
    McpResourceClient resourceClient = defaultResourceClient();
    McpPerceptionService perceptionService =
        new McpPerceptionService(resourceClient, new KeywordSemanticRelevanceScorer());
    CognitiveContextAssembler cognitiveContextAssembler = defaultCognitiveContextAssembler();
    McpToolClient toolClient = defaultToolClient();
    SocialPublishingService socialPublishingService = new SocialPublishingService(toolClient);
    CreativeEngineService creativeEngineService = new CreativeEngineService(toolClient);
    SecretProvider secretProvider = new EnvironmentSecretProvider();
    WalletExecutionService walletExecutionService =
        new WalletExecutionService(defaultWalletProvider(secretProvider));
    WorkerService workerService =
        new WorkerService(socialPublishingService, walletExecutionService, creativeEngineService);
    JudgeService judgeService = new JudgeService(new BudgetGovernor());

    PersistenceBundle persistenceBundle = PersistenceBootstrap.initialize();
    TaskRepository taskRepository = persistenceBundle.taskRepository();
    WalletLedgerRepository walletLedgerRepository = persistenceBundle.walletLedgerRepository();
    TrendSignalRepository trendSignalRepository = persistenceBundle.trendSignalRepository();
    DeadLetterReplayAuditRepository deadLetterReplayAuditRepository =
        persistenceBundle.deadLetterReplayAuditRepository();
    RateLimiter writeRateLimiter = persistenceBundle.writeRateLimiter();
    PlannerService planner =
        new PlannerService(
            taskQueue, perceptionService, cognitiveContextAssembler, trendSignalRepository);
    InMemoryQueueGovernanceMetrics queueGovernanceMetrics = new InMemoryQueueGovernanceMetrics();
    McpOpenClawStatusPublisher openClawStatusPublisher = new McpOpenClawStatusPublisher(toolClient);
    TaskOrchestratorService orchestratorService =
        new TaskOrchestratorService(
            taskQueue,
            queueRuntime.reviewQueue(),
            queueRuntime.deadLetterQueue(),
            taskRepository,
            workerService,
            judgeService,
            loadDefaultDailyBudget(),
            walletLedgerRepository,
            queueGovernanceMetrics,
            loadQueueMaxRetries(),
            openClawStatusPublisher);
    CampaignApiService campaignApiService =
        new CampaignApiService(planner, taskRepository, orchestratorService);
    ReviewApiService reviewApiService = new ReviewApiService(taskRepository);
    DeadLetterApiService deadLetterApiService =
        new DeadLetterApiService(
            taskRepository,
            taskQueue,
            queueRuntime.deadLetterQueue(),
            deadLetterReplayAuditRepository,
            loadReplayCooldown(),
            loadReplayMaxPerTaskPerDay());
    TelemetryApiService telemetryApiService =
        new TelemetryApiService(
            taskRepository,
            taskQueue,
            queueRuntime.reviewQueue(),
            queueRuntime.deadLetterQueue(),
            walletLedgerRepository,
            trendSignalRepository,
            queueGovernanceMetrics,
            queueRuntime.backend(),
            walletProviderName(walletExecutionService),
            loadDefaultDailyBudget());

    int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

    try (workerService;
        queueRuntime;
        persistenceBundle;
        ChimeraHttpServer server =
            new ChimeraHttpServer(
                port,
                campaignApiService,
                reviewApiService,
                writeRateLimiter,
                telemetryApiService,
                deadLetterApiService)) {
      server.start();
      System.out.println("Chimera API is running on http://localhost:" + port);
      System.out.println(
          "Headers: X-Tenant-Id, X-Role, and one of X-Api-Key or Authorization: Bearer <jwt>");
      System.out.println("POST /api/campaigns");
      System.out.println("GET /api/tasks");
      System.out.println("GET /api/telemetry");
      System.out.println("POST /api/review/{taskId}/approve");
      System.out.println("POST /api/review/{taskId}/reject");
      System.out.println("POST /api/dead-letter/{taskId}/replay");
      System.out.println("GET /health");
      System.out.println("GET /ready");
      System.out.println("GET /openapi.yaml");
      Thread.currentThread().join();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      System.err.println("Chimera API interrupted: " + ex.getMessage());
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to start Chimera API", ex);
    }
  }

  private static McpResourceClient defaultResourceClient() {
    McpResourceClient fallbackClient = staticResourceClient();
    String endpoint = System.getenv().getOrDefault("CHIMERA_MCP_RESOURCE_ENDPOINT", "").trim();
    if (endpoint.isBlank()) {
      return fallbackClient;
    }

    HttpMcpResourceClient liveClient =
        new HttpMcpResourceClient(
            java.net.http.HttpClient.newHttpClient(),
            java.net.URI.create(endpoint),
            loadMcpResourceTimeout(),
            System.getenv().getOrDefault("CHIMERA_MCP_RESOURCE_AUTHORIZATION", ""));

    return resourceUri -> {
      try {
        String payload = liveClient.readResource(resourceUri);
        if (payload != null && !payload.isBlank()) {
          return payload;
        }
      } catch (Exception ex) {
        System.err.println(
            "MCP resource adapter unavailable, using static fallback: " + ex.getMessage());
      }
      return fallbackClient.readResource(resourceUri);
    };
  }

  private static McpResourceClient staticResourceClient() {
    Map<String, String> payloads =
        Map.of(
            "news://ethiopia/fashion/trends",
            String.join(
                System.lineSeparator(),
                "Sustainable Ethiopian streetwear",
                "Ethical textile collab drops",
                "Creator wardrobe challenge"),
            "twitter://mentions/recent",
            String.join(
                System.lineSeparator(),
                "streetwear collab request",
                "creator collab comments",
                "sneaker capsule discussion"));
    return resourceUri -> payloads.getOrDefault(resourceUri, "");
  }

  private static McpToolClient defaultToolClient() {
    return (toolName, arguments) ->
        new McpToolResult(
            true,
            "Simulated MCP tool invocation",
            Map.of(
                "external_id",
                toolName + "-" + UUID.randomUUID(),
                "echo_args_count",
                arguments == null ? 0 : arguments.size()));
  }

  private static CognitiveContextAssembler defaultCognitiveContextAssembler() {
    return new CognitiveContextAssembler(
        new ClasspathSoulPersonaLoader("soul/SOUL.md"),
        new InMemoryMemoryRecall(
            Map.of(
                "worker-alpha",
                java.util.List.of(
                    "Audience responded best to concise sustainability hooks.",
                    "Short CTA endings improved engagement in recent campaigns.",
                    "Trend-aware replies convert better than generic comments."),
                "worker-beta",
                java.util.List.of(
                    "Visual-first copy performs better for lifestyle audiences.",
                    "Use transparent AI disclosure wording when asked directly."))));
  }

  private static BigDecimal loadDefaultDailyBudget() {
    String budget = System.getenv().getOrDefault("CHIMERA_DAILY_BUDGET_USD", "500.00");
    try {
      return new BigDecimal(budget);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("CHIMERA_DAILY_BUDGET_USD must be a valid decimal", ex);
    }
  }

  private static int loadQueueMaxRetries() {
    String raw = System.getenv().getOrDefault("CHIMERA_QUEUE_MAX_RETRIES", "2");
    try {
      int parsed = Integer.parseInt(raw);
      if (parsed < 0) {
        throw new IllegalArgumentException("CHIMERA_QUEUE_MAX_RETRIES must be >= 0");
      }
      return parsed;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("CHIMERA_QUEUE_MAX_RETRIES must be an integer", ex);
    }
  }

  private static Duration loadReplayCooldown() {
    String raw = System.getenv().getOrDefault("CHIMERA_REPLAY_COOLDOWN_SECONDS", "300");
    try {
      int parsed = Integer.parseInt(raw);
      if (parsed < 0) {
        throw new IllegalArgumentException("CHIMERA_REPLAY_COOLDOWN_SECONDS must be >= 0");
      }
      return Duration.ofSeconds(parsed);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("CHIMERA_REPLAY_COOLDOWN_SECONDS must be an integer", ex);
    }
  }

  private static int loadReplayMaxPerTaskPerDay() {
    String raw = System.getenv().getOrDefault("CHIMERA_REPLAY_MAX_PER_TASK_PER_DAY", "3");
    try {
      int parsed = Integer.parseInt(raw);
      if (parsed < 1) {
        throw new IllegalArgumentException("CHIMERA_REPLAY_MAX_PER_TASK_PER_DAY must be >= 1");
      }
      return parsed;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(
          "CHIMERA_REPLAY_MAX_PER_TASK_PER_DAY must be an integer", ex);
    }
  }

  private static Duration loadMcpResourceTimeout() {
    String raw = System.getenv().getOrDefault("CHIMERA_MCP_RESOURCE_TIMEOUT_SECONDS", "4");
    try {
      int parsed = Integer.parseInt(raw);
      if (parsed < 1) {
        throw new IllegalArgumentException("CHIMERA_MCP_RESOURCE_TIMEOUT_SECONDS must be >= 1");
      }
      return Duration.ofSeconds(parsed);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(
          "CHIMERA_MCP_RESOURCE_TIMEOUT_SECONDS must be an integer", ex);
    }
  }

  private static WalletProvider defaultWalletProvider(SecretProvider secretProvider) {
    boolean hasCoinbaseSecrets =
        secretProvider.getSecret(CoinbaseAgentKitWalletProvider.SECRET_API_KEY_NAME).isPresent()
            && secretProvider
                .getSecret(CoinbaseAgentKitWalletProvider.SECRET_API_KEY_PRIVATE_KEY)
                .isPresent();
    if (!hasCoinbaseSecrets) {
      return new SimulatedWalletProvider();
    }

    String baseUrl =
        System.getenv()
            .getOrDefault("CHIMERA_CDP_BASE_URL", "https://api.coinbase.com/agentkit/v1");
    return new CoinbaseAgentKitWalletProvider(
        secretProvider, HttpWalletTransport.defaultTransport(baseUrl));
  }

  private static QueueRuntime initializeQueueRuntime() {
    String redisUrl = System.getenv("REDIS_URL");
    if (redisUrl == null || redisUrl.isBlank()) {
      return QueueRuntime.inMemory();
    }

    try {
      ObjectMapper mapper = new ObjectMapper();
      mapper.registerModule(new JavaTimeModule());
      RedisTaskQueuePort taskQueue = new RedisTaskQueuePort(redisUrl, TASK_QUEUE_KEY, mapper);
      RedisUuidQueuePort reviewQueue = new RedisUuidQueuePort(redisUrl, REVIEW_QUEUE_KEY);
      RedisUuidQueuePort deadLetterQueue = new RedisUuidQueuePort(redisUrl, DEAD_LETTER_QUEUE_KEY);
      taskQueue.size();
      reviewQueue.size();
      deadLetterQueue.size();
      return QueueRuntime.redis(taskQueue, reviewQueue, deadLetterQueue);
    } catch (Exception ex) {
      System.err.println(
          "Redis queue runtime unavailable, falling back to in-memory queue: " + ex.getMessage());
      return QueueRuntime.inMemory();
    }
  }

  private static String walletProviderName(WalletExecutionService walletExecutionService) {
    if (walletExecutionService == null) {
      return "unknown";
    }
    return walletExecutionService.providerName();
  }

  private record QueueRuntime(
      QueuePort<Task> taskQueue,
      QueuePort<UUID> reviewQueue,
      QueuePort<UUID> deadLetterQueue,
      AutoCloseable closeable,
      String backend)
      implements AutoCloseable {
    private static QueueRuntime inMemory() {
      return new QueueRuntime(
          new InMemoryQueuePort<>(),
          new InMemoryQueuePort<>(),
          new InMemoryQueuePort<>(),
          () -> {},
          "in-memory");
    }

    private static QueueRuntime redis(
        RedisTaskQueuePort taskQueue,
        RedisUuidQueuePort reviewQueue,
        RedisUuidQueuePort deadLetterQueue) {
      return new QueueRuntime(
          taskQueue,
          reviewQueue,
          deadLetterQueue,
          () -> {
            taskQueue.close();
            reviewQueue.close();
            deadLetterQueue.close();
          },
          "redis");
    }

    @Override
    public void close() throws Exception {
      closeable.close();
    }
  }
}
