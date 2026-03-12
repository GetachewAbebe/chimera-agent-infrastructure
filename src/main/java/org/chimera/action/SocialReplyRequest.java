package org.chimera.action;

public record SocialReplyRequest(
    SocialPlatform platform,
    String conversationId,
    String textContent,
    DisclosureLevel disclosureLevel) {}
