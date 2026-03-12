package org.chimera.api;

import java.util.List;
import org.chimera.model.Task;
import org.chimera.orchestrator.TaskOrchestratorService;
import org.chimera.persistence.TaskRepository;
import org.chimera.planner.PlannerService;

public final class CampaignApiService {
  private final PlannerService plannerService;
  private final TaskRepository taskRepository;
  private final TaskOrchestratorService taskOrchestratorService;

  public CampaignApiService(PlannerService plannerService, TaskRepository taskRepository) {
    this(plannerService, taskRepository, null);
  }

  public CampaignApiService(
      PlannerService plannerService,
      TaskRepository taskRepository,
      TaskOrchestratorService taskOrchestratorService) {
    this.plannerService = plannerService;
    this.taskRepository = taskRepository;
    this.taskOrchestratorService = taskOrchestratorService;
  }

  public List<Task> createCampaign(String tenantId, CreateCampaignRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("request must not be null");
    }
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId must not be blank");
    }
    if (request.goal() == null || request.goal().isBlank()) {
      throw new IllegalArgumentException("goal must not be blank");
    }

    String workerId =
        request.workerId() == null || request.workerId().isBlank()
            ? "worker-alpha"
            : request.workerId();
    List<String> resources =
        request.requiredResources() == null || request.requiredResources().isEmpty()
            ? List.of("news://ethiopia/fashion/trends")
            : request.requiredResources();

    List<Task> tasks = plannerService.decomposeGoal(tenantId, request.goal(), workerId, resources);
    taskRepository.saveAll(tasks);
    if (taskOrchestratorService != null) {
      taskOrchestratorService.processAvailableTasks(tasks.size());
    }
    return tasks.stream()
        .map(task -> taskRepository.findByTenant(tenantId, task.taskId()).orElse(task))
        .toList();
  }

  public List<Task> listTasks(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId must not be blank");
    }
    return taskRepository.listByTenant(tenantId);
  }
}
