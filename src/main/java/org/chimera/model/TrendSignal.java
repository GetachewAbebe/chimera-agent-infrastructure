package org.chimera.model;

import java.time.Instant;

public record TrendSignal(String topic, double score, String source, Instant observedAt) {}
