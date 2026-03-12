package org.chimera.mcp;

import java.util.Map;

public record McpToolResult(boolean success, String message, Map<String, Object> payload) {
  public McpToolResult {
    payload = payload == null ? Map.of() : Map.copyOf(payload);
  }
}
