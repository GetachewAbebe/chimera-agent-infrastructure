package org.chimera.tests.judge;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.chimera.judge.JudgeService;
import org.chimera.model.GlobalStateSnapshot;
import org.chimera.model.Priority;
import org.chimera.model.ReviewOutcome;
import org.chimera.model.Task;
import org.chimera.model.TaskContext;
import org.chimera.model.TaskResult;
import org.chimera.model.TaskStatus;
import org.chimera.model.TaskType;
import org.chimera.security.BudgetGovernor;
import org.junit.jupiter.api.Test;

class JudgeServiceTest {

  @Test
  void shouldRejectTransactionWhenProjectedSpendExceedsLimit() {
    JudgeService judgeService = new JudgeService(new BudgetGovernor());
    Task task =
        Task.pending(
            "tenant-alpha",
            TaskType.EXECUTE_TRANSACTION,
            Priority.HIGH,
            new TaskContext(
                "Transfer funds",
                List.of("Respect persona directives"),
                List.of("wallet://amount_usd/5.50")),
            "worker-alpha");
    TaskResult result =
        new TaskResult(
            task.taskId(), task.assignedWorkerId(), true, 0.95, "tx-1", "ok", Instant.now());

    var decision =
        judgeService.review(
            task,
            result,
            new GlobalStateSnapshot(12L, false, new BigDecimal("97.00"), new BigDecimal("100.00")),
            12L,
            false);

    assertThat(decision.outcome()).isEqualTo(ReviewOutcome.REJECTED);
    assertThat(decision.nextStatus()).isEqualTo(TaskStatus.REJECTED);
    assertThat(decision.reason()).contains("exceeds daily limit");
  }

  @Test
  void shouldRejectTransactionWhenAmountResourceMissing() {
    JudgeService judgeService = new JudgeService(new BudgetGovernor());
    Task task =
        Task.pending(
            "tenant-alpha",
            TaskType.EXECUTE_TRANSACTION,
            Priority.HIGH,
            new TaskContext(
                "Transfer funds",
                List.of("Respect persona directives"),
                List.of("wallet://to/0xabc")),
            "worker-alpha");
    TaskResult result =
        new TaskResult(
            task.taskId(), task.assignedWorkerId(), true, 0.95, "tx-1", "ok", Instant.now());

    var decision =
        judgeService.review(
            task,
            result,
            new GlobalStateSnapshot(9L, false, BigDecimal.ZERO, new BigDecimal("100.00")),
            9L,
            false);

    assertThat(decision.outcome()).isEqualTo(ReviewOutcome.REJECTED);
    assertThat(decision.reason()).contains("wallet://amount_usd/");
  }

  @Test
  void shouldEscalateGenerateContentWhenCreativeConsistencyLockFails() {
    JudgeService judgeService = new JudgeService(new BudgetGovernor());
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
    TaskResult result =
        new TaskResult(
            task.taskId(),
            task.assignedWorkerId(),
            false,
            0.62,
            "creative-consistency-lock-failed",
            "Creative consistency lock failed prior to publish. "
                + "creative_consistency_passed=false; creative_consistency_score=0.62",
            Instant.now());

    var decision =
        judgeService.review(
            task,
            result,
            new GlobalStateSnapshot(4L, false, BigDecimal.ZERO, new BigDecimal("100.00")),
            4L,
            false);

    assertThat(decision.outcome()).isEqualTo(ReviewOutcome.ESCALATED);
    assertThat(decision.nextStatus()).isEqualTo(TaskStatus.ESCALATED);
    assertThat(decision.reason()).contains("visual review");
  }

  @Test
  void shouldEscalateGenerateContentWhenConsistencyScoreIsLow() {
    JudgeService judgeService = new JudgeService(new BudgetGovernor());
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
    TaskResult result =
        new TaskResult(
            task.taskId(),
            task.assignedWorkerId(),
            true,
            0.93,
            "tweet-123",
            "Executed twitter.post_tweet for publish workflow. "
                + "creative_consistency_passed=true; creative_consistency_score=0.74",
            Instant.now());

    var decision =
        judgeService.review(
            task,
            result,
            new GlobalStateSnapshot(4L, false, BigDecimal.ZERO, new BigDecimal("100.00")),
            4L,
            false);

    assertThat(decision.outcome()).isEqualTo(ReviewOutcome.ESCALATED);
    assertThat(decision.nextStatus()).isEqualTo(TaskStatus.ESCALATED);
    assertThat(decision.reason()).contains("consistency score below threshold");
  }
}
