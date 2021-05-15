package com.sunya.electionguard.publish;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Conversion to/from POJOS utilities. */
public class ConvertPojos {

  @Nullable
  public static <T, U> List<U> convertList(@Nullable List<T> from, Function<T, U> converter) {
    return from == null || from.isEmpty() ? null : from.stream().map(converter).collect(Collectors.toList());
  }

  @Nullable
  public static <T, U> List<U> convertCollection(@Nullable Collection<T> from, Function<T, U> converter) {
    return from == null || from.isEmpty() ? null : from.stream().map(converter).collect(Collectors.toList());
  }

}
