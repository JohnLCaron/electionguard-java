package com.sunya.electionguard;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;

import static com.google.common.truth.Truth.assertThat;

public class TestScheduler {
  private static Random random = new Random();

  @Test
  public void test_schedule_callable_throws() {
    Scheduler<Integer> subject = new Scheduler<>();

    List<Callable<Integer>> tasks = new ArrayList<>();
    for (int i=0; i<11; i++) {
      tasks.add(new TestCallable());
    }

    List<Integer> result = subject.schedule(tasks, false);
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(List.class);
    assertThat(result.stream().reduce(Integer::sum)).isEqualTo(42 * 11);
  }

  static class TestCallable implements Callable<Integer> {
    @Override
    public Integer call() throws Exception {
      return 42;
    }
  }

  static class TestCallableBad implements Callable<Integer> {
    @Override
    public Integer call() throws Exception {
      if (random.nextBoolean()) {
        return 42;
      } else {
        throw new RuntimeException("bad");
      }
    }
  }

  @Test
  public void test_exceptions() {
    Scheduler<Integer> subject = new Scheduler<>();

    List<Callable<Integer>> tasks = new ArrayList<>();
    for (int i=0; i<11; i++) {
      tasks.add(new TestCallableBad());
    }

    List<Integer> result = subject.schedule(tasks, false);
    assertThat(result).isNull();
  }


}
