package org.chimera.api;

import java.util.List;

public record CreateCampaignRequest(String goal, String workerId, List<String> requiredResources) {}
