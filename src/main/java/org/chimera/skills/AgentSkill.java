package org.chimera.skills;

public interface AgentSkill<I, O> {
  String name();

  O execute(I input);
}
