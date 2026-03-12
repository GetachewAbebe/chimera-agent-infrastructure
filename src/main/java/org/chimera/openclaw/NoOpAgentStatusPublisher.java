package org.chimera.openclaw;

import org.chimera.model.Task;
import org.chimera.model.TaskStatus;

public final class NoOpAgentStatusPublisher implements AgentStatusPublisher {
  @Override
  public void publishStatus(Task task, TaskStatus status, String reason) {}
}
