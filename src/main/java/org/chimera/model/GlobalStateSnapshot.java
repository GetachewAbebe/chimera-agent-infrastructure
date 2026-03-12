package org.chimera.model;

import java.math.BigDecimal;

public record GlobalStateSnapshot(
    long version, boolean paused, BigDecimal dailySpendUsd, BigDecimal dailyLimitUsd) {}
