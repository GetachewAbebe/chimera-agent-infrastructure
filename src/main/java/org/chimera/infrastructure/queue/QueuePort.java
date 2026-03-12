package org.chimera.infrastructure.queue;

import java.util.Optional;

public interface QueuePort<T> {
  void push(T payload);

  Optional<T> poll();

  int size();
}
