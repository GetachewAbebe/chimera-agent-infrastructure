package org.chimera.tests.planner;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.chimera.cognitive.AgentPersona;
import org.chimera.cognitive.CognitiveContextAssembler;
import org.chimera.cognitive.InMemoryMemoryRecall;
import org.chimera.cognitive.PersonaLoader;
import org.chimera.infrastructure.queue.InMemoryQueuePort;
import org.chimera.mcp.McpResourceClient;
import org.chimera.model.Task;
import org.chimera.model.TaskType;
import org.chimera.perception.KeywordSemanticRelevanceScorer;
import org.chimera.perception.McpPerceptionService;
import org.chimera.persistence.InMemoryTrendSignalRepository;
import org.chimera.planner.PlannerService;
import org.junit.jupiter.api.Test;

class PlannerServiceTest {

  @Test
  void shouldCreatePrimaryAndReplyTasksWhenPerceptionSignalsExist() {
    InMemoryQueuePort<Task> taskQueue = new InMemoryQueuePort<>();
    McpResourceClient resourceClient =
        uri ->
            String.join(
                System.lineSeparator(), "sustainable fashion capsule", "fashion campaign collab");
    PlannerService plannerService =
        new PlannerService(
            taskQueue,
            new McpPerceptionService(resourceClient, new KeywordSemanticRelevanceScorer()));

    List<Task> tasks =
        plannerService.decomposeGoal(
            "tenant-alpha",
            "Launch sustainable fashion campaign",
            "worker-alpha",
            List.of("news://ethiopia/fashion/trends"));

    assertThat(tasks).hasSizeGreaterThanOrEqualTo(2);
    assertThat(tasks.getFirst().taskType()).isEqualTo(TaskType.GENERATE_CONTENT);
    assertThat(tasks.stream().skip(1)).allMatch(task -> task.taskType() == TaskType.REPLY_COMMENT);
    assertThat(taskQueue.size()).isEqualTo(tasks.size());
  }

  @Test
  void shouldFallbackToSingleGenerateTaskWhenPerceptionNotConfigured() {
    InMemoryQueuePort<Task> taskQueue = new InMemoryQueuePort<>();
    PlannerService plannerService = new PlannerService(taskQueue);

    List<Task> tasks =
        plannerService.decomposeGoal(
            "tenant-alpha",
            "Launch sustainable fashion campaign",
            "worker-alpha",
            List.of("news://ethiopia/fashion/trends"));

    assertThat(tasks).hasSize(1);
    assertThat(tasks.getFirst().taskType()).isEqualTo(TaskType.GENERATE_CONTENT);
  }

  @Test
  void shouldIncludeCognitiveContextConstraintsWhenAssemblerConfigured() {
    InMemoryQueuePort<Task> taskQueue = new InMemoryQueuePort<>();
    PersonaLoader personaLoader =
        agentId ->
            new AgentPersona(
                "chimera-prime",
                "Chimera Prime",
                List.of("concise"),
                List.of("Always disclose AI nature."),
                "Persona backstory.");
    CognitiveContextAssembler assembler =
        new CognitiveContextAssembler(
            personaLoader,
            new InMemoryMemoryRecall(
                Map.of("worker-alpha", List.of("Audience prefers concise CTAs."))));
    PlannerService plannerService = new PlannerService(taskQueue, null, assembler);

    List<Task> tasks =
        plannerService.decomposeGoal(
            "tenant-alpha",
            "Launch sustainable fashion campaign",
            "worker-alpha",
            List.of("news://ethiopia/fashion/trends"));

    assertThat(tasks).hasSize(1);
    assertThat(tasks.getFirst().context().personaConstraints())
        .anyMatch(line -> line.contains("Persona name: Chimera Prime"));
    assertThat(tasks.getFirst().context().personaConstraints())
        .anyMatch(line -> line.contains("Memory recall: Audience prefers concise CTAs."));
  }

  @Test
  void shouldPersistPerceptionSignalsWhenTrendRepositoryConfigured() {
    InMemoryQueuePort<Task> taskQueue = new InMemoryQueuePort<>();
    InMemoryTrendSignalRepository trendSignalRepository = new InMemoryTrendSignalRepository();
    McpResourceClient resourceClient =
        uri ->
            String.join(
                System.lineSeparator(), "sustainable fashion capsule", "fashion campaign collab");
    PlannerService plannerService =
        new PlannerService(
            taskQueue,
            new McpPerceptionService(resourceClient, new KeywordSemanticRelevanceScorer()),
            null,
            trendSignalRepository);

    plannerService.decomposeGoal(
        "tenant-alpha",
        "Launch sustainable fashion campaign",
        "worker-alpha",
        List.of("news://ethiopia/fashion/trends"));

    int todaySignals =
        trendSignalRepository.countForTenantOnDate("tenant-alpha", LocalDate.now(ZoneOffset.UTC));
    assertThat(todaySignals).isGreaterThan(0);
    assertThat(
            trendSignalRepository.topSignalsForTenantOnDate(
                "tenant-alpha", LocalDate.now(ZoneOffset.UTC), 3))
        .isNotEmpty();
  }
}
