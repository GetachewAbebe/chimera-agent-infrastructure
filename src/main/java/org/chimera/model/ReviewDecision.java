package org.chimera.model;

public record ReviewDecision(ReviewOutcome outcome, TaskStatus nextStatus, String reason) {}
