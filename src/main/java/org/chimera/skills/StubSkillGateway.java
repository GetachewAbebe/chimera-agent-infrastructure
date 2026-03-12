package org.chimera.skills;

import java.math.BigDecimal;

public final class StubSkillGateway implements SkillGateway {
  private final SkillGateway delegate;

  public StubSkillGateway() {
    this(new RuntimeSkillGateway(new BigDecimal("50.00")));
  }

  StubSkillGateway(SkillGateway delegate) {
    if (delegate == null) {
      throw new IllegalArgumentException("delegate is required");
    }
    this.delegate = delegate;
  }

  @Override
  public SkillResponse execute(SkillRequest request) {
    return delegate.execute(request);
  }
}
