package org.chimera.skills;

import java.math.BigDecimal;
import java.util.Map;

public final class BudgetAwareSkillGateway implements SkillGateway {
  private final BigDecimal dailyLimitUsd;

  public BudgetAwareSkillGateway(BigDecimal dailyLimitUsd) {
    if (dailyLimitUsd == null || dailyLimitUsd.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("dailyLimitUsd must be greater than zero");
    }
    this.dailyLimitUsd = dailyLimitUsd;
  }

  @Override
  public SkillResponse execute(SkillRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("request must not be null");
    }

    if (request.projectedCostUsd() == null
        || request.projectedCostUsd().compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("projectedCostUsd must be >= 0");
    }

    if (request.projectedCostUsd().compareTo(dailyLimitUsd) > 0) {
      throw new BudgetExceededException(
          "Projected skill cost "
              + request.projectedCostUsd()
              + " exceeds daily limit "
              + dailyLimitUsd);
    }

    return new SkillResponse(
        true,
        Map.of(
            "status", "accepted",
            "skill_name", request.skillName(),
            "agent_id", request.agentId(),
            "budget_limit_usd", dailyLimitUsd,
            "projected_cost_usd", request.projectedCostUsd()));
  }
}
