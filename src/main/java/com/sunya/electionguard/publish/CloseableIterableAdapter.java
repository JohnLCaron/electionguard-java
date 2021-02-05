package com.sunya.electionguard.publish;

import java.util.Iterator;

public class CloseableIterableAdapter<T> implements Iterable<T>, CloseableIterable<T> {

  public static <T> CloseableIterable <T> wrap(Iterable<T> delegate) {
    return new CloseableIterableAdapter<>(delegate);
  }

  private final Iterable<T> delegate;
  CloseableIterableAdapter(Iterable<T> delegate) {
    this.delegate = delegate;
  }

  @Override
  public CloseableIterator<T> iterator() {
    return new CloseableIteratorAdapter<>(delegate.iterator());
  }

  private static class CloseableIteratorAdapter<T> implements Iterator<T>, CloseableIterator<T> {
    Iterator<T> proxy;

    public CloseableIteratorAdapter(Iterator<T> proxy) {
      this.proxy = proxy;
    }

    @Override
    public void close() {
      // noop
    }

    @Override
    public boolean hasNext() {
      return proxy.hasNext();
    }

    @Override
    public T next() {
      return proxy.next();
    }
  }
}
