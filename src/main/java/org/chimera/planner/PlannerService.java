package org.chimera.planner;

import java.util.ArrayList;
import java.util.List;
import org.chimera.cognitive.CognitiveContextAssembler;
import org.chimera.infrastructure.queue.QueuePort;
import org.chimera.model.Priority;
import org.chimera.model.Task;
import org.chimera.model.TaskContext;
import org.chimera.model.TaskType;
import org.chimera.model.TrendSignal;
import org.chimera.perception.McpPerceptionService;
import org.chimera.persistence.TrendSignalRepository;

public final class PlannerService {
  private static final double PERCEPTION_THRESHOLD = 0.35;
  private static final int PERCEPTION_LIMIT = 3;

  private final QueuePort<Task> taskQueue;
  private final McpPerceptionService perceptionService;
  private final CognitiveContextAssembler cognitiveContextAssembler;
  private final TrendSignalRepository trendSignalRepository;

  public PlannerService(QueuePort<Task> taskQueue) {
    this(taskQueue, null, null, null);
  }

  public PlannerService(QueuePort<Task> taskQueue, McpPerceptionService perceptionService) {
    this(taskQueue, perceptionService, null, null);
  }

  public PlannerService(
      QueuePort<Task> taskQueue,
      McpPerceptionService perceptionService,
      CognitiveContextAssembler cognitiveContextAssembler) {
    this(taskQueue, perceptionService, cognitiveContextAssembler, null);
  }

  public PlannerService(
      QueuePort<Task> taskQueue,
      McpPerceptionService perceptionService,
      CognitiveContextAssembler cognitiveContextAssembler,
      TrendSignalRepository trendSignalRepository) {
    if (taskQueue == null) {
      throw new IllegalArgumentException("taskQueue is required");
    }
    this.taskQueue = taskQueue;
    this.perceptionService = perceptionService;
    this.cognitiveContextAssembler = cognitiveContextAssembler;
    this.trendSignalRepository = trendSignalRepository;
  }

  public List<Task> decomposeGoal(
      String tenantId, String goal, String workerId, List<String> requiredResources) {
    TaskContext baseContext = buildBaseContext(workerId, goal, requiredResources);

    List<TrendSignal> signals = detectSignals(requiredResources, goal);
    persistSignals(tenantId, signals);
    List<Task> tasks = new ArrayList<>();

    if (signals.isEmpty()) {
      tasks.add(
          Task.pending(tenantId, TaskType.GENERATE_CONTENT, Priority.HIGH, baseContext, workerId));
    } else {
      Task primaryTask =
          Task.pending(
              tenantId,
              TaskType.GENERATE_CONTENT,
              Priority.HIGH,
              enrichContext(baseContext, signals.getFirst(), true),
              workerId);
      tasks.add(primaryTask);

      for (int index = 1; index < signals.size(); index++) {
        tasks.add(
            Task.pending(
                tenantId,
                TaskType.REPLY_COMMENT,
                Priority.MEDIUM,
                enrichContext(baseContext, signals.get(index), false),
                workerId));
      }
    }

    tasks.forEach(taskQueue::push);
    return List.copyOf(tasks);
  }

  private void persistSignals(String tenantId, List<TrendSignal> signals) {
    if (trendSignalRepository == null || signals == null || signals.isEmpty()) {
      return;
    }
    signals.forEach(signal -> trendSignalRepository.append(tenantId, signal));
  }

  private TaskContext buildBaseContext(
      String workerId, String goal, List<String> requiredResources) {
    if (cognitiveContextAssembler != null) {
      return cognitiveContextAssembler.assemble(workerId, goal, requiredResources);
    }
    return new TaskContext(
        goal,
        List.of(
            "Respect persona directives", "Always include AI disclosure if platform supports it"),
        requiredResources);
  }

  private List<TrendSignal> detectSignals(List<String> requiredResources, String goal) {
    if (perceptionService == null) {
      return List.of();
    }
    if (requiredResources == null || requiredResources.isEmpty()) {
      return List.of();
    }
    return perceptionService.pollRelevantSignals(
        requiredResources, goal, PERCEPTION_THRESHOLD, PERCEPTION_LIMIT);
  }

  private static TaskContext enrichContext(
      TaskContext baseContext, TrendSignal signal, boolean primaryTask) {
    List<String> constraints = new ArrayList<>(baseContext.personaConstraints());
    constraints.add("Prioritize trend topic: " + signal.topic());
    constraints.add("Signal source: " + signal.source());

    List<String> resources = new ArrayList<>(baseContext.requiredResources());
    if (!resources.contains(signal.source())) {
      resources.add(signal.source());
    }
    resources.add("trend://" + signal.topic());

    String goalDescription =
        primaryTask
            ? baseContext.goalDescription() + " Focus trend: " + signal.topic()
            : "Engage around trend: " + signal.topic() + ". " + baseContext.goalDescription();
    return new TaskContext(goalDescription, List.copyOf(constraints), List.copyOf(resources));
  }
}
