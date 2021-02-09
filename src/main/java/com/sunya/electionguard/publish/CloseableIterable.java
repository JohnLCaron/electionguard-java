package com.sunya.electionguard.publish;

public interface CloseableIterable<T> extends Iterable<T> {

  CloseableIterator<T> iterator();

}
