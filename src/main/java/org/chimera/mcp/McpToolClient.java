package org.chimera.mcp;

import java.util.Map;

public interface McpToolClient {
  McpToolResult callTool(String toolName, Map<String, Object> arguments);
}
