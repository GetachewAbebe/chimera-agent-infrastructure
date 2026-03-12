package org.chimera.openclaw;

import org.chimera.model.Task;
import org.chimera.model.TaskStatus;

public interface AgentStatusPublisher {
  void publishStatus(Task task, TaskStatus status, String reason);
}
