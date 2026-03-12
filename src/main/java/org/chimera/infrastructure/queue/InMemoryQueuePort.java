package org.chimera.infrastructure.queue;

import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class InMemoryQueuePort<T> implements QueuePort<T> {
  private final Queue<T> delegate = new ConcurrentLinkedQueue<>();

  @Override
  public void push(T payload) {
    delegate.offer(payload);
  }

  @Override
  public Optional<T> poll() {
    return Optional.ofNullable(delegate.poll());
  }

  @Override
  public int size() {
    return delegate.size();
  }
}
