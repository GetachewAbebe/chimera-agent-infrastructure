package org.chimera.action;

public record SocialActionReceipt(
    boolean success, String toolName, String externalId, String message) {}
