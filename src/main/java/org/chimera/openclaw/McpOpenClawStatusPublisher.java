package org.chimera.openclaw;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.chimera.mcp.McpToolClient;
import org.chimera.mcp.McpToolResult;
import org.chimera.model.Task;
import org.chimera.model.TaskStatus;

public final class McpOpenClawStatusPublisher implements AgentStatusPublisher {
  private static final String TOOL_NAME = "openclaw.publish_status";
  private static final int MAX_AUDIT_ENTRIES = 500;
  private static final String DEFAULT_SIGNING_SECRET = "chimera-openclaw-dev-secret";

  private final McpToolClient toolClient;
  private final ConcurrentLinkedDeque<OpenClawPublicationAttempt> auditTrail =
      new ConcurrentLinkedDeque<>();

  public McpOpenClawStatusPublisher(McpToolClient toolClient) {
    if (toolClient == null) {
      throw new IllegalArgumentException("toolClient is required");
    }
    this.toolClient = toolClient;
  }

  @Override
  public void publishStatus(Task task, TaskStatus status, String reason) {
    if (task == null || status == null) {
      return;
    }

    OpenClawAvailability availability = toAvailability(status);
    OpenClawSafetyLevel safetyLevel = toSafetyLevel(status);
    Instant updatedAt = Instant.now();
    boolean blockedByPolicy = status == TaskStatus.ESCALATED;
    String effectiveReason = reason == null || reason.isBlank() ? "state_transition" : reason;

    if (blockedByPolicy) {
      appendAudit(
          new OpenClawPublicationAttempt(
              task.tenantId(),
              task.assignedWorkerId(),
              availability,
              safetyLevel,
              false,
              "blocked_unresolved_high_risk_escalation",
              updatedAt));
      return;
    }

    String signature =
        sign(
            task.assignedWorkerId(),
            task.tenantId(),
            availability.name().toLowerCase(Locale.ROOT),
            safetyLevel.name().toLowerCase(Locale.ROOT),
            updatedAt.toString());

    boolean accepted = false;
    String auditReason = "publish_failed:null_result";
    try {
      McpToolResult result =
          toolClient.callTool(
              TOOL_NAME,
              Map.of(
                  "agent_id", task.assignedWorkerId(),
                  "tenant_id", task.tenantId(),
                  "availability", availability.name().toLowerCase(Locale.ROOT),
                  "capabilities", capabilitiesForTask(task),
                  "safety_level", safetyLevel.name().toLowerCase(Locale.ROOT),
                  "updated_at", updatedAt.toString(),
                  "signature", signature,
                  "reason", effectiveReason));
      accepted = result != null && result.success();
      auditReason =
          accepted
              ? effectiveReason
              : "publish_failed:" + (result == null ? "null_result" : result.message());
    } catch (RuntimeException ex) {
      auditReason = "publish_failed:" + ex.getMessage();
    }
    appendAudit(
        new OpenClawPublicationAttempt(
            task.tenantId(),
            task.assignedWorkerId(),
            availability,
            safetyLevel,
            accepted,
            auditReason,
            updatedAt));
  }

  public List<OpenClawPublicationAttempt> auditTrail() {
    return List.copyOf(auditTrail);
  }

  private static OpenClawAvailability toAvailability(TaskStatus status) {
    return switch (status) {
      case PENDING -> OpenClawAvailability.ONLINE;
      case IN_PROGRESS -> OpenClawAvailability.BUSY;
      case REVIEW, ESCALATED -> OpenClawAvailability.PAUSED;
      case COMPLETE -> OpenClawAvailability.ONLINE;
      case REJECTED -> OpenClawAvailability.OFFLINE;
    };
  }

  private static OpenClawSafetyLevel toSafetyLevel(TaskStatus status) {
    return switch (status) {
      case COMPLETE, PENDING, IN_PROGRESS -> OpenClawSafetyLevel.AUTONOMOUS;
      case REVIEW -> OpenClawSafetyLevel.SUPERVISED;
      case ESCALATED, REJECTED -> OpenClawSafetyLevel.RESTRICTED;
    };
  }

  private static List<String> capabilitiesForTask(Task task) {
    List<String> capabilities = new ArrayList<>();
    capabilities.add("planner_worker_judge");
    capabilities.add("mcp://resource/read");
    capabilities.add("openclaw.publish_status");
    capabilities.add("task_type:" + task.taskType().name().toLowerCase(Locale.ROOT));
    return List.copyOf(capabilities);
  }

  private static String sign(
      String agentId, String tenantId, String availability, String safetyLevel, String updatedAt) {
    String canonical =
        String.join("|", agentId, tenantId, availability, safetyLevel, updatedAt).trim();
    try {
      String secret = System.getenv().getOrDefault("CHIMERA_OPENCLAW_SIGNING_SECRET", "");
      if (secret.isBlank()) {
        secret = DEFAULT_SIGNING_SECRET;
      }
      Mac hmac = Mac.getInstance("HmacSHA256");
      hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] digest = hmac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(digest);
    } catch (Exception ex) {
      return "";
    }
  }

  private void appendAudit(OpenClawPublicationAttempt attempt) {
    auditTrail.addLast(attempt);
    while (auditTrail.size() > MAX_AUDIT_ENTRIES) {
      auditTrail.pollFirst();
    }
  }
}
