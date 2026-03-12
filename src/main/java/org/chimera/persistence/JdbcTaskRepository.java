package org.chimera.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.chimera.model.Priority;
import org.chimera.model.Task;
import org.chimera.model.TaskContext;
import org.chimera.model.TaskStatus;
import org.chimera.model.TaskType;

public final class JdbcTaskRepository implements TaskRepository {
  private final DataSource dataSource;
  private final ObjectMapper objectMapper;

  public JdbcTaskRepository(DataSource dataSource, ObjectMapper objectMapper) {
    this.dataSource = dataSource;
    this.objectMapper = objectMapper;
  }

  @Override
  public void saveAll(Collection<Task> tasks) {
    for (Task task : tasks) {
      upsert(task);
    }
  }

  @Override
  public List<Task> listByTenant(String tenantId) {
    String sql =
        "SELECT task_id, tenant_id, task_type, priority, context_json, assigned_worker_id, created_at, status "
            + "FROM api_tasks WHERE tenant_id = ? ORDER BY created_at";

    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, tenantId);
      try (ResultSet resultSet = statement.executeQuery()) {
        List<Task> tasks = new ArrayList<>();
        while (resultSet.next()) {
          tasks.add(mapTask(resultSet));
        }
        return tasks;
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to list tasks", ex);
    }
  }

  @Override
  public Optional<Task> findByTenant(String tenantId, UUID taskId) {
    String sql =
        "SELECT task_id, tenant_id, task_type, priority, context_json, assigned_worker_id, created_at, status "
            + "FROM api_tasks WHERE tenant_id = ? AND task_id = ?";

    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, tenantId);
      statement.setObject(2, taskId);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return Optional.empty();
        }
        return Optional.of(mapTask(resultSet));
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to find task by id", ex);
    }
  }

  @Override
  public Task updateStatus(String tenantId, UUID taskId, TaskStatus status) {
    String sql = "UPDATE api_tasks SET status = ? WHERE tenant_id = ? AND task_id = ?";

    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, status.name());
      statement.setString(2, tenantId);
      statement.setObject(3, taskId);
      int updated = statement.executeUpdate();
      if (updated == 0) {
        throw new IllegalArgumentException("task not found: " + taskId);
      }
      return findByTenant(tenantId, taskId)
          .orElseThrow(() -> new IllegalArgumentException("task not found: " + taskId));
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to update task status", ex);
    }
  }

  private void upsert(Task task) {
    int updated = updateTask(task);
    if (updated == 0) {
      insertTask(task);
    }
  }

  private int updateTask(Task task) {
    String sql =
        "UPDATE api_tasks SET tenant_id = ?, task_type = ?, priority = ?, context_json = ?, assigned_worker_id = ?, "
            + "created_at = ?, status = ? WHERE task_id = ?";

    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      bindTask(statement, task, 1);
      statement.setObject(8, task.taskId());
      return statement.executeUpdate();
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to update task", ex);
    }
  }

  private void insertTask(Task task) {
    String sql =
        "INSERT INTO api_tasks (task_id, tenant_id, task_type, priority, context_json, assigned_worker_id, created_at, status) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, task.taskId());
      bindTask(statement, task, 2);
      statement.executeUpdate();
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to insert task", ex);
    }
  }

  private void bindTask(PreparedStatement statement, Task task, int startIndex)
      throws SQLException {
    statement.setString(startIndex, task.tenantId());
    statement.setString(startIndex + 1, task.taskType().name());
    statement.setString(startIndex + 2, task.priority().name());
    statement.setString(startIndex + 3, serializeContext(task.context()));
    statement.setString(startIndex + 4, task.assignedWorkerId());
    statement.setTimestamp(startIndex + 5, Timestamp.from(task.createdAt()));
    statement.setString(startIndex + 6, task.status().name());
  }

  private Task mapTask(ResultSet resultSet) throws SQLException {
    try {
      return new Task(
          UUID.fromString(resultSet.getString("task_id")),
          resultSet.getString("tenant_id"),
          TaskType.valueOf(resultSet.getString("task_type")),
          Priority.valueOf(resultSet.getString("priority")),
          objectMapper.readValue(resultSet.getString("context_json"), TaskContext.class),
          resultSet.getString("assigned_worker_id"),
          resultSet.getTimestamp("created_at").toInstant(),
          TaskStatus.valueOf(resultSet.getString("status")));
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to deserialize task context", ex);
    }
  }

  private String serializeContext(TaskContext context) {
    try {
      return objectMapper.writeValueAsString(context);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize task context", ex);
    }
  }
}
