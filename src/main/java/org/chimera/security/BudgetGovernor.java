package org.chimera.security;

import java.math.BigDecimal;
import org.chimera.model.GlobalStateSnapshot;
import org.chimera.skills.BudgetExceededException;

public final class BudgetGovernor {
  public void assertWithinBudget(GlobalStateSnapshot snapshot, BigDecimal projectedCostUsd) {
    BigDecimal nextSpend = snapshot.dailySpendUsd().add(projectedCostUsd);
    if (nextSpend.compareTo(snapshot.dailyLimitUsd()) > 0) {
      throw new BudgetExceededException(
          "Projected spend " + nextSpend + " exceeds daily limit " + snapshot.dailyLimitUsd());
    }
  }
}
