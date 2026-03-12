package org.chimera.judge;

import java.math.BigDecimal;
import java.util.Locale;
import org.chimera.model.GlobalStateSnapshot;
import org.chimera.model.ReviewDecision;
import org.chimera.model.ReviewOutcome;
import org.chimera.model.Task;
import org.chimera.model.TaskResult;
import org.chimera.model.TaskStatus;
import org.chimera.model.TaskType;
import org.chimera.security.BudgetGovernor;
import org.chimera.skills.BudgetExceededException;
import org.chimera.wallet.WalletTaskResourceParser;

public final class JudgeService {
  private final BudgetGovernor budgetGovernor;

  public JudgeService(BudgetGovernor budgetGovernor) {
    this.budgetGovernor = budgetGovernor;
  }

  public ReviewDecision review(
      Task task,
      TaskResult result,
      GlobalStateSnapshot snapshot,
      long expectedStateVersion,
      boolean sensitiveTopicDetected) {

    if (snapshot.version() != expectedStateVersion) {
      return new ReviewDecision(
          ReviewOutcome.REJECTED,
          TaskStatus.REJECTED,
          "OCC version mismatch. Result invalidated and must be re-planned.");
    }

    if (sensitiveTopicDetected) {
      return new ReviewDecision(
          ReviewOutcome.ESCALATED,
          TaskStatus.ESCALATED,
          "Sensitive topic detected. Mandatory HITL escalation.");
    }

    if (task.taskType() == TaskType.EXECUTE_TRANSACTION) {
      try {
        budgetGovernor.assertWithinBudget(snapshot, projectedTransactionCost(task));
      } catch (BudgetExceededException ex) {
        return new ReviewDecision(ReviewOutcome.REJECTED, TaskStatus.REJECTED, ex.getMessage());
      } catch (IllegalArgumentException ex) {
        return new ReviewDecision(ReviewOutcome.REJECTED, TaskStatus.REJECTED, ex.getMessage());
      }
    }

    if (task.taskType() == TaskType.GENERATE_CONTENT) {
      ReviewDecision visionDecision = reviewCreativeConsistency(result);
      if (visionDecision != null) {
        return visionDecision;
      }
    }

    if (!result.success()) {
      return new ReviewDecision(
          ReviewOutcome.REJECTED, TaskStatus.REJECTED, "Worker result flagged as unsuccessful.");
    }

    if (result.confidenceScore() < 0.70) {
      return new ReviewDecision(
          ReviewOutcome.REJECTED,
          TaskStatus.REJECTED,
          "Confidence below threshold. Retry with refined instructions.");
    }

    if (result.confidenceScore() < 0.90) {
      return new ReviewDecision(
          ReviewOutcome.ESCALATED, TaskStatus.ESCALATED, "Requires asynchronous HITL approval.");
    }

    return new ReviewDecision(ReviewOutcome.APPROVED, TaskStatus.COMPLETE, "Auto-approved.");
  }

  private static BigDecimal projectedTransactionCost(Task task) {
    return WalletTaskResourceParser.parseAmountUsd(task.context());
  }

  private static ReviewDecision reviewCreativeConsistency(TaskResult result) {
    String trace = result.reasoningTrace();
    if (trace == null || trace.isBlank()) {
      return null;
    }
    String normalized = trace.toLowerCase(Locale.ROOT);
    if (normalized.contains("creative_consistency_passed=false")) {
      return new ReviewDecision(
          ReviewOutcome.ESCALATED,
          TaskStatus.ESCALATED,
          "Creative consistency lock failed. Requires HITL visual review.");
    }

    double score = parseCreativeConsistencyScore(normalized);
    if (!Double.isNaN(score) && score < 0.80) {
      return new ReviewDecision(
          ReviewOutcome.ESCALATED,
          TaskStatus.ESCALATED,
          "Creative consistency score below threshold. Requires HITL visual review.");
    }
    return null;
  }

  private static double parseCreativeConsistencyScore(String trace) {
    String marker = "creative_consistency_score=";
    int markerIndex = trace.indexOf(marker);
    if (markerIndex < 0) {
      return Double.NaN;
    }
    int valueStart = markerIndex + marker.length();
    int valueEnd = trace.indexOf(';', valueStart);
    String raw =
        valueEnd >= 0 ? trace.substring(valueStart, valueEnd) : trace.substring(valueStart);
    try {
      return Double.parseDouble(raw.trim());
    } catch (NumberFormatException ignored) {
      return Double.NaN;
    }
  }
}
