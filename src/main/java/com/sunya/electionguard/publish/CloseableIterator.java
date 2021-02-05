package com.sunya.electionguard.publish;

import java.io.Closeable;
import java.util.Iterator;

/**
 * An iterator that must be closed.
 *
 * try (CloseableIterator iter = getIterator()) {
 *   // do stuff
 * }
 */
public interface CloseableIterator<T> extends Iterator<T>, Closeable {
}
