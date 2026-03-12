package org.chimera.model;

import java.util.List;

public record TaskContext(
    String goalDescription, List<String> personaConstraints, List<String> requiredResources) {}
