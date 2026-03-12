import type { Task } from "../types/chimera";

type TaskTableProps = {
  tasks: Task[];
  replayingTaskId?: string | null;
  onReplayDeadLetter?: (taskId: string) => void;
};

const shortId = (value: string): string => value.slice(0, 8);

export function TaskTable({ tasks, replayingTaskId, onReplayDeadLetter }: TaskTableProps) {
  return (
    <section className="panel task-table-panel">
      <div className="section-header">
        <h2>Task Ledger</h2>
        <p>Tenant-scoped task state with governance status.</p>
      </div>

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Task</th>
              <th>Type</th>
              <th>Priority</th>
              <th>Status</th>
              <th>Worker</th>
              <th>Created</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {tasks.map((task) => (
              <tr key={task.taskId}>
                <td title={task.taskId}>{shortId(task.taskId)}</td>
                <td>{task.taskType}</td>
                <td>{task.priority}</td>
                <td>
                  <span className={`status-pill status-${task.status.toLowerCase()}`}>
                    {task.status}
                  </span>
                </td>
                <td>{task.assignedWorkerId}</td>
                <td>{new Date(task.createdAt).toLocaleString()}</td>
                <td>
                  {task.status === "REJECTED" && onReplayDeadLetter ? (
                    <button
                      type="button"
                      className="button button-secondary table-action-button"
                      onClick={() => onReplayDeadLetter(task.taskId)}
                      disabled={replayingTaskId === task.taskId}
                    >
                      {replayingTaskId === task.taskId ? "Replaying..." : "Replay"}
                    </button>
                  ) : (
                    " "
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
