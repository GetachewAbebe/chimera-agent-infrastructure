package org.chimera.cognitive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SoulMarkdownPersonaLoader implements PersonaLoader {
  private final Path soulMarkdownPath;

  public SoulMarkdownPersonaLoader(Path soulMarkdownPath) {
    if (soulMarkdownPath == null) {
      throw new IllegalArgumentException("soulMarkdownPath is required");
    }
    this.soulMarkdownPath = soulMarkdownPath;
  }

  @Override
  public AgentPersona loadPersona(String agentId) {
    String content;
    try {
      content = Files.readString(soulMarkdownPath, StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to read SOUL markdown at " + soulMarkdownPath, ex);
    }
    return parse(content, agentId);
  }

  static AgentPersona parse(String content, String fallbackAgentId) {
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("SOUL markdown content must not be blank");
    }

    String normalized = content.replace("\r\n", "\n");
    List<String> lines = List.of(normalized.split("\n", -1));
    if (lines.isEmpty() || !lines.getFirst().trim().equals("---")) {
      throw new IllegalArgumentException("SOUL markdown must start with YAML frontmatter '---'");
    }

    int closingIndex = -1;
    for (int index = 1; index < lines.size(); index++) {
      if (lines.get(index).trim().equals("---")) {
        closingIndex = index;
        break;
      }
    }
    if (closingIndex < 0) {
      throw new IllegalArgumentException(
          "SOUL markdown frontmatter closing delimiter '---' is missing");
    }

    Frontmatter frontmatter = parseFrontmatter(lines.subList(1, closingIndex));
    String backstory = String.join("\n", lines.subList(closingIndex + 1, lines.size())).trim();
    String id =
        frontmatter.id() == null || frontmatter.id().isBlank()
            ? fallbackAgentIdOrDefault(fallbackAgentId)
            : frontmatter.id();
    String name =
        frontmatter.name() == null || frontmatter.name().isBlank()
            ? "Chimera Agent"
            : frontmatter.name();

    return new AgentPersona(
        id, name, frontmatter.voiceTraits(), frontmatter.directives(), backstory);
  }

  private static Frontmatter parseFrontmatter(List<String> lines) {
    String name = null;
    String id = null;
    List<String> voiceTraits = new ArrayList<>();
    List<String> directives = new ArrayList<>();
    List<String> targetList = null;

    for (String raw : lines) {
      String line = raw == null ? "" : raw.trim();
      if (line.isEmpty()) {
        continue;
      }

      if (line.startsWith("- ")) {
        if (targetList != null) {
          String value = line.substring(2).trim();
          if (!value.isEmpty()) {
            targetList.add(value);
          }
        }
        continue;
      }

      int separator = line.indexOf(':');
      if (separator < 0) {
        continue;
      }

      String key = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
      String value = line.substring(separator + 1).trim();
      switch (key) {
        case "name" -> {
          name = value;
          targetList = null;
        }
        case "id" -> {
          id = value;
          targetList = null;
        }
        case "voice_traits" -> {
          targetList = voiceTraits;
          if (!value.isEmpty()) {
            voiceTraits.add(value);
          }
        }
        case "directives" -> {
          targetList = directives;
          if (!value.isEmpty()) {
            directives.add(value);
          }
        }
        default -> targetList = null;
      }
    }

    return new Frontmatter(id, name, List.copyOf(voiceTraits), List.copyOf(directives));
  }

  private static String fallbackAgentIdOrDefault(String fallbackAgentId) {
    if (fallbackAgentId == null || fallbackAgentId.isBlank()) {
      return "chimera-agent";
    }
    return fallbackAgentId.trim();
  }

  private record Frontmatter(
      String id, String name, List<String> voiceTraits, List<String> directives) {}
}
