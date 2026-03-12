package org.chimera.tests.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.chimera.api.CampaignApiService;
import org.chimera.api.CreateCampaignRequest;
import org.chimera.infrastructure.queue.InMemoryQueuePort;
import org.chimera.model.Task;
import org.chimera.persistence.InMemoryTaskRepository;
import org.chimera.persistence.TaskRepository;
import org.chimera.planner.PlannerService;
import org.junit.jupiter.api.Test;

class CampaignApiServiceTest {
  private static final String TENANT_ID = "tenant-alpha";

  @Test
  void shouldCreateCampaignAndPersistPlannedTasks() {
    TaskRepository taskRepository = new InMemoryTaskRepository();
    PlannerService plannerService = new PlannerService(new InMemoryQueuePort<>());
    CampaignApiService campaignApiService = new CampaignApiService(plannerService, taskRepository);

    List<Task> created =
        campaignApiService.createCampaign(
            TENANT_ID,
            new CreateCampaignRequest(
                "Promote sustainable fashion capsule",
                "worker-alpha",
                List.of("news://ethiopia/fashion/trends")));

    assertThat(created).isNotEmpty();
    assertThat(campaignApiService.listTasks(TENANT_ID)).hasSize(created.size());
    assertThat(campaignApiService.listTasks(TENANT_ID).getFirst().context().goalDescription())
        .contains("sustainable fashion");
    assertThat(campaignApiService.listTasks(TENANT_ID).getFirst().tenantId()).isEqualTo(TENANT_ID);
  }
}
