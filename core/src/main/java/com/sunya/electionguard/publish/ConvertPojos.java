package com.sunya.electionguard.publish;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/** Static utilities for conversion to/from Pojos. */
public class ConvertPojos {

  @Nullable
  public static <T, U> List<U> convertList(@Nullable List<T> from, Function<T, U> converter) {
    return from == null || from.isEmpty() ? null : from.stream().map(converter).toList();
  }

  public static <T, U> List<U> convertCollection(@Nullable Collection<T> from, Function<T, U> converter) {
    return from == null || from.isEmpty() ? new ArrayList<>() : from.stream().map(converter).toList();
  }
}
