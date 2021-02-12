package com.sunya.electionguard.publish;

/**
 * An Iterable that produces a CloseableIterator.
 */
public interface CloseableIterable<T> extends Iterable<T> {

  CloseableIterator<T> iterator();

}
