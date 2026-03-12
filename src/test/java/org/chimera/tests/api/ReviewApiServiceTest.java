package org.chimera.tests.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.chimera.api.CampaignApiService;
import org.chimera.api.CreateCampaignRequest;
import org.chimera.api.ReviewApiService;
import org.chimera.infrastructure.queue.InMemoryQueuePort;
import org.chimera.model.ReviewOutcome;
import org.chimera.model.Task;
import org.chimera.model.TaskStatus;
import org.chimera.persistence.InMemoryTaskRepository;
import org.chimera.persistence.TaskRepository;
import org.chimera.planner.PlannerService;
import org.junit.jupiter.api.Test;

class ReviewApiServiceTest {
  private static final String TENANT_ID = "tenant-alpha";

  @Test
  void shouldApproveTaskFromHumanReviewEndpointFlow() {
    TaskRepository taskRepository = new InMemoryTaskRepository();
    PlannerService plannerService = new PlannerService(new InMemoryQueuePort<>());
    CampaignApiService campaignApiService = new CampaignApiService(plannerService, taskRepository);
    ReviewApiService reviewApiService = new ReviewApiService(taskRepository);

    List<Task> created =
        campaignApiService.createCampaign(
            TENANT_ID,
            new CreateCampaignRequest(
                "Launch creator collaboration campaign",
                "worker-beta",
                List.of("twitter://mentions/recent")));

    Task task = created.getFirst();

    var decision = reviewApiService.approve(TENANT_ID, task.taskId());

    assertThat(decision.outcome()).isEqualTo(ReviewOutcome.APPROVED);
    assertThat(taskRepository.findByTenant(TENANT_ID, task.taskId())).isPresent();
    assertThat(taskRepository.findByTenant(TENANT_ID, task.taskId()).orElseThrow().status())
        .isEqualTo(TaskStatus.COMPLETE);
  }

  @Test
  void shouldRejectTaskFromHumanReviewEndpointFlow() {
    TaskRepository taskRepository = new InMemoryTaskRepository();
    PlannerService plannerService = new PlannerService(new InMemoryQueuePort<>());
    CampaignApiService campaignApiService = new CampaignApiService(plannerService, taskRepository);
    ReviewApiService reviewApiService = new ReviewApiService(taskRepository);

    List<Task> created =
        campaignApiService.createCampaign(
            TENANT_ID,
            new CreateCampaignRequest(
                "Draft high-risk financial advice post",
                "worker-gamma",
                List.of("news://finance/market")));

    Task task = created.getFirst();

    var decision = reviewApiService.reject(TENANT_ID, task.taskId());

    assertThat(decision.outcome()).isEqualTo(ReviewOutcome.REJECTED);
    assertThat(taskRepository.findByTenant(TENANT_ID, task.taskId()).orElseThrow().status())
        .isEqualTo(TaskStatus.REJECTED);
  }
}
