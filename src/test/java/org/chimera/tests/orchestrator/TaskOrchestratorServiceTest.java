package org.chimera.tests.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.chimera.action.SocialPublishingService;
import org.chimera.api.CampaignApiService;
import org.chimera.api.CreateCampaignRequest;
import org.chimera.creative.CreativeEngineService;
import org.chimera.infrastructure.queue.InMemoryQueuePort;
import org.chimera.judge.JudgeService;
import org.chimera.mcp.McpResourceClient;
import org.chimera.mcp.McpToolClient;
import org.chimera.mcp.McpToolResult;
import org.chimera.model.Priority;
import org.chimera.model.Task;
import org.chimera.model.TaskContext;
import org.chimera.model.TaskStatus;
import org.chimera.model.TaskType;
import org.chimera.model.WalletLedgerEntry;
import org.chimera.openclaw.AgentStatusPublisher;
import org.chimera.orchestrator.InMemoryQueueGovernanceMetrics;
import org.chimera.orchestrator.TaskOrchestratorService;
import org.chimera.perception.KeywordSemanticRelevanceScorer;
import org.chimera.perception.McpPerceptionService;
import org.chimera.persistence.InMemoryTaskRepository;
import org.chimera.persistence.InMemoryWalletLedgerRepository;
import org.chimera.planner.PlannerService;
import org.chimera.security.BudgetGovernor;
import org.chimera.wallet.SimulatedWalletProvider;
import org.chimera.wallet.WalletExecutionService;
import org.chimera.worker.WorkerService;
import org.junit.jupiter.api.Test;

class TaskOrchestratorServiceTest {

  @Test
  void shouldProcessQueuedTasksIntoGovernedStatuses() {
    InMemoryQueuePort<Task> taskQueue = new InMemoryQueuePort<>();
    InMemoryQueuePort<UUID> reviewQueue = new InMemoryQueuePort<>();
    InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
    McpResourceClient resourceClient =
        uri ->
            String.join(
                System.lineSeparator(), "sustainable fashion capsule", "fashion campaign collab");
    PlannerService plannerService =
        new PlannerService(
            taskQueue,
            new McpPerceptionService(resourceClient, new KeywordSemanticRelevanceScorer()));

    McpToolClient toolClient =
        (toolName, arguments) ->
            new McpToolResult(true, "ok", Map.of("external_id", "id-" + toolName));
    WorkerService workerService = new WorkerService(new SocialPublishingService(toolClient));
    TaskOrchestratorService orchestratorService =
        new TaskOrchestratorService(
            taskQueue,
            reviewQueue,
            taskRepository,
            workerService,
            new JudgeService(new BudgetGovernor()));

    CampaignApiService campaignApiService =
        new CampaignApiService(plannerService, taskRepository, orchestratorService);

    List<Task> tasks =
        campaignApiService.createCampaign(
            "tenant-alpha",
            new CreateCampaignRequest(
                "Launch sustainable fashion campaign",
                "worker-alpha",
                List.of("news://ethiopia/fashion/trends")));

    assertThat(tasks).hasSizeGreaterThanOrEqualTo(2);
    assertThat(tasks).extracting(Task::status).contains(TaskStatus.COMPLETE, TaskStatus.ESCALATED);
    assertThat(taskQueue.size()).isZero();
    assertThat(reviewQueue.size()).isEqualTo(1);
    workerService.close();
  }

  @Test
  void shouldRecordLedgerEntryForApprovedTransaction() {
    InMemoryQueuePort<Task> taskQueue = new InMemoryQueuePort<>();
    InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
    InMemoryWalletLedgerRepository walletLedgerRepository = new InMemoryWalletLedgerRepository();
    WorkerService workerService =
        new WorkerService(null, new WalletExecutionService(new SimulatedWalletProvider()));
    TaskOrchestratorService orchestratorService =
        new TaskOrchestratorService(
            taskQueue,
            null,
            taskRepository,
            workerService,
            new JudgeService(new BudgetGovernor()),
            new BigDecimal("500.00"),
            walletLedgerRepository);

    Task task = transactionTask("tenant-alpha", "worker-alpha", "12.00");
    taskRepository.saveAll(List.of(task));
    taskQueue.push(task);

    List<Task> processed = orchestratorService.processAvailableTasks(1);

    assertThat(processed).singleElement().extracting(Task::status).isEqualTo(TaskStatus.COMPLETE);
    assertThat(
            walletLedgerRepository.sumForTenantOnDate(
                "tenant-alpha", LocalDate.now(ZoneOffset.UTC)))
        .isEqualByComparingTo("12.00");
    assertThat(
            walletLedgerRepository.countForTenantOnDate(
                "tenant-alpha", LocalDate.now(ZoneOffset.UTC)))
        .isEqualTo(1);
    workerService.close();
  }

  @Test
  void shouldRejectTransactionWhenDailyBudgetAlreadyConsumed() {
    InMemoryQueuePort<Task> taskQueue = new InMemoryQueuePort<>();
    InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
    InMemoryWalletLedgerRepository walletLedgerRepository = new InMemoryWalletLedgerRepository();
    WorkerService workerService =
        new WorkerService(null, new WalletExecutionService(new SimulatedWalletProvider()));
    TaskOrchestratorService orchestratorService =
        new TaskOrchestratorService(
            taskQueue,
            null,
            taskRepository,
            workerService,
            new JudgeService(new BudgetGovernor()),
            new BigDecimal("500.00"),
            walletLedgerRepository);

    walletLedgerRepository.append(
        new WalletLedgerEntry(
            null,
            "tenant-alpha",
            UUID.randomUUID(),
            "worker-alpha",
            new BigDecimal("500.00"),
            "simulated",
            "sim-tx-prior",
            Instant.now()));

    Task task = transactionTask("tenant-alpha", "worker-alpha", "10.00");
    taskRepository.saveAll(List.of(task));
    taskQueue.push(task);

    List<Task> processed = orchestratorService.processAvailableTasks(1);

    assertThat(processed).singleElement().extracting(Task::status).isEqualTo(TaskStatus.REJECTED);
    assertThat(
            walletLedgerRepository.countForTenantOnDate(
                "tenant-alpha", LocalDate.now(ZoneOffset.UTC)))
        .isEqualTo(1);
    workerService.close();
  }

  @Test
  void shouldRetryThenDeadLetterAfterMaxAttempts() {
    InMemoryQueuePort<Task> taskQueue = new InMemoryQueuePort<>();
    InMemoryQueuePort<UUID> deadLetterQueue = new InMemoryQueuePort<>();
    InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
    InMemoryWalletLedgerRepository walletLedgerRepository = new InMemoryWalletLedgerRepository();
    InMemoryQueueGovernanceMetrics queueGovernanceMetrics = new InMemoryQueueGovernanceMetrics();

    McpToolClient failingToolClient =
        (toolName, arguments) -> {
          throw new IllegalStateException("simulated publish failure");
        };
    WorkerService workerService = new WorkerService(new SocialPublishingService(failingToolClient));
    TaskOrchestratorService orchestratorService =
        new TaskOrchestratorService(
            taskQueue,
            null,
            deadLetterQueue,
            taskRepository,
            workerService,
            new JudgeService(new BudgetGovernor()),
            new BigDecimal("500.00"),
            walletLedgerRepository,
            queueGovernanceMetrics,
            1);

    Task task =
        Task.pending(
            "tenant-alpha",
            TaskType.GENERATE_CONTENT,
            Priority.HIGH,
            new TaskContext("Publish new launch teaser", List.of("Respect persona"), List.of()),
            "worker-alpha");
    taskRepository.saveAll(List.of(task));
    taskQueue.push(task);

    List<Task> firstPass = orchestratorService.processAvailableTasks(1);
    List<Task> secondPass = orchestratorService.processAvailableTasks(1);

    assertThat(firstPass).singleElement().extracting(Task::status).isEqualTo(TaskStatus.PENDING);
    assertThat(secondPass).singleElement().extracting(Task::status).isEqualTo(TaskStatus.REJECTED);
    assertThat(taskQueue.size()).isZero();
    assertThat(deadLetterQueue.size()).isEqualTo(1);

    var governanceSnapshot =
        queueGovernanceMetrics.snapshot("tenant-alpha", LocalDate.now(ZoneOffset.UTC));
    assertThat(governanceSnapshot.retryAttempts()).isEqualTo(1);
    assertThat(governanceSnapshot.deadLetteredTasks()).isEqualTo(1);
    workerService.close();
  }

  @Test
  void shouldEscalateLegalSensitiveTopicForMandatoryHitl() {
    InMemoryQueuePort<Task> taskQueue = new InMemoryQueuePort<>();
    InMemoryQueuePort<UUID> reviewQueue = new InMemoryQueuePort<>();
    InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
    WorkerService workerService =
        new WorkerService(
            new SocialPublishingService(
                (toolName, arguments) ->
                    new McpToolResult(true, "ok", Map.of("external_id", "id-" + toolName))));
    TaskOrchestratorService orchestratorService =
        new TaskOrchestratorService(
            taskQueue,
            reviewQueue,
            taskRepository,
            workerService,
            new JudgeService(new BudgetGovernor()));

    Task task =
        Task.pending(
            "tenant-alpha",
            TaskType.GENERATE_CONTENT,
            Priority.HIGH,
            new TaskContext(
                "Provide legal claim advice for a creator contract dispute",
                List.of("Respect persona"),
                List.of("news://ethiopia/fashion/trends")),
            "worker-alpha");
    taskRepository.saveAll(List.of(task));
    taskQueue.push(task);

    List<Task> processed = orchestratorService.processAvailableTasks(1);

    assertThat(processed).singleElement().extracting(Task::status).isEqualTo(TaskStatus.ESCALATED);
    assertThat(reviewQueue.size()).isEqualTo(1);
    workerService.close();
  }

  @Test
  void shouldEscalateWhenCreativeConsistencyLockFails() {
    InMemoryQueuePort<Task> taskQueue = new InMemoryQueuePort<>();
    InMemoryQueuePort<UUID> reviewQueue = new InMemoryQueuePort<>();
    InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();

    McpToolClient toolClient =
        (toolName, arguments) ->
            switch (toolName) {
              case "creative.generate_text" ->
                  new McpToolResult(
                      true, "text-ok", Map.of("text_content", "Capsule drop tonight"));
              case "creative.generate_image" ->
                  new McpToolResult(
                      true, "image-ok", Map.of("image_url", "https://cdn.example.com/look.png"));
              case "creative.generate_video" ->
                  new McpToolResult(
                      true, "video-ok", Map.of("video_url", "https://cdn.example.com/reel.mp4"));
              case "creative.check_consistency" ->
                  new McpToolResult(
                      true,
                      "consistency-low",
                      Map.of("is_consistent", true, "consistency_score", 0.63));
              case "twitter.post_tweet" ->
                  new McpToolResult(true, "ok", Map.of("external_id", "tweet-123"));
              default -> throw new IllegalArgumentException("Unexpected tool: " + toolName);
            };

    WorkerService workerService =
        new WorkerService(
            new SocialPublishingService(toolClient), null, new CreativeEngineService(toolClient));
    TaskOrchestratorService orchestratorService =
        new TaskOrchestratorService(
            taskQueue,
            reviewQueue,
            taskRepository,
            workerService,
            new JudgeService(new BudgetGovernor()));

    Task task =
        Task.pending(
            "tenant-alpha",
            TaskType.GENERATE_CONTENT,
            Priority.HIGH,
            new TaskContext(
                "Launch sustainable fashion campaign",
                List.of("Respect persona directives"),
                List.of("news://ethiopia/fashion/trends")),
            "worker-alpha");
    taskRepository.saveAll(List.of(task));
    taskQueue.push(task);

    List<Task> processed = orchestratorService.processAvailableTasks(1);

    assertThat(processed).singleElement().extracting(Task::status).isEqualTo(TaskStatus.ESCALATED);
    assertThat(reviewQueue.size()).isEqualTo(1);
    workerService.close();
  }

  @Test
  void shouldPublishOpenClawStatusOnTaskTransitions() {
    InMemoryQueuePort<Task> taskQueue = new InMemoryQueuePort<>();
    InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
    CopyOnWriteArrayList<TaskStatus> publishedStatuses = new CopyOnWriteArrayList<>();
    AgentStatusPublisher statusPublisher = (task, status, reason) -> publishedStatuses.add(status);

    WorkerService workerService =
        new WorkerService(
            new SocialPublishingService(
                (toolName, arguments) ->
                    new McpToolResult(true, "ok", Map.of("external_id", "id-" + toolName))));
    TaskOrchestratorService orchestratorService =
        new TaskOrchestratorService(
            taskQueue,
            null,
            new InMemoryQueuePort<>(),
            taskRepository,
            workerService,
            new JudgeService(new BudgetGovernor()),
            new BigDecimal("500.00"),
            new InMemoryWalletLedgerRepository(),
            new InMemoryQueueGovernanceMetrics(),
            1,
            statusPublisher);

    Task task =
        Task.pending(
            "tenant-alpha",
            TaskType.GENERATE_CONTENT,
            Priority.HIGH,
            new TaskContext("Launch sustainable fashion campaign", List.of(), List.of()),
            "worker-alpha");
    taskRepository.saveAll(List.of(task));
    taskQueue.push(task);

    orchestratorService.processAvailableTasks(1);

    assertThat(publishedStatuses).contains(TaskStatus.IN_PROGRESS, TaskStatus.COMPLETE);
    workerService.close();
  }

  @Test
  void shouldIgnoreOpenClawPublisherFailuresDuringExecution() {
    InMemoryQueuePort<Task> taskQueue = new InMemoryQueuePort<>();
    InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
    AgentStatusPublisher statusPublisher =
        (task, status, reason) -> {
          throw new IllegalStateException("openclaw unavailable");
        };

    WorkerService workerService =
        new WorkerService(
            new SocialPublishingService(
                (toolName, arguments) ->
                    new McpToolResult(true, "ok", Map.of("external_id", "id-" + toolName))));
    TaskOrchestratorService orchestratorService =
        new TaskOrchestratorService(
            taskQueue,
            null,
            new InMemoryQueuePort<>(),
            taskRepository,
            workerService,
            new JudgeService(new BudgetGovernor()),
            new BigDecimal("500.00"),
            new InMemoryWalletLedgerRepository(),
            new InMemoryQueueGovernanceMetrics(),
            1,
            statusPublisher);

    Task task =
        Task.pending(
            "tenant-alpha",
            TaskType.GENERATE_CONTENT,
            Priority.HIGH,
            new TaskContext("Launch sustainable fashion campaign", List.of(), List.of()),
            "worker-alpha");
    taskRepository.saveAll(List.of(task));
    taskQueue.push(task);

    List<Task> processed = orchestratorService.processAvailableTasks(1);

    assertThat(processed).singleElement().extracting(Task::status).isEqualTo(TaskStatus.COMPLETE);
    workerService.close();
  }

  private static Task transactionTask(String tenantId, String workerId, String amountUsd) {
    return Task.pending(
        tenantId,
        TaskType.EXECUTE_TRANSACTION,
        Priority.HIGH,
        new TaskContext(
            "Transfer campaign budget",
            List.of("Respect persona directives"),
            List.of("wallet://to/0xabc123", "wallet://amount_usd/" + amountUsd)),
        workerId);
  }
}
