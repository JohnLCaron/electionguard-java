package com.sunya.electionguard;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public class Scheduler<T> {

  /**
   *         Schedule tasks with list of arguments
   *         :param task: the callable task to execute
   *         :param arguments: the list of lists passed to the task using starmap
   *         :param with_shared_resources: flag to use threads instead of processes
   *                                       allowing resources to be shared.  note
   *                                       when using the threadpool, execution is bound
   *                                       by default to the [global interpreter lock](https://docs.python.org/3.8/glossary.html#term-global-interpreter-lock)
   */

  List<T> schedule(
          List<T> tasks,
          boolean with_shared_resources) {

    // TODO
    return new ArrayList<T>();

    /* if (with_shared_resources) {
      return this.safe_starmap(this.__thread_pool, task, arguments);
    }
    return this.safe_starmap(this.__process_pool, task, arguments); */
  }
}
