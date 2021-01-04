package com.sunya.electionguard;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/** A scheduler for concurrent task execution. */
// TODO make a Singleton
public class Scheduler<T> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final static int DEFAULT_NTHREADS = 11;
  // TODO probably want to inject this
  private final static ListeningExecutorService service = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(DEFAULT_NTHREADS));

  /**
   * Schedule tasks concurrently, wait for results
   *
   * @param tasks: the callable task to execute
   * @return the unordered list of results of type T.
   */
  List<T> schedule(List<Callable<T>> tasks, boolean with_shared_resources) {
    List<ListenableFuture<? extends T>> futures = new ArrayList<>();
    for (Callable<T> task : tasks) {
      futures.add(service.submit(task));
    }

    // ListenableFuture<? extends V>... futures
    ListenableFuture<List<T>> results = Futures.allAsList(futures);
    try {
      return results.get();
    } catch (InterruptedException | ExecutionException e) {
      logger.atWarning().withCause(e).log("Scheduler got exception %s", e.getMessage());
      throw new RuntimeException(e);
    }
  }

  // TODO call this
  public static void shutdown() {
    service.shutdown();
  }
}
