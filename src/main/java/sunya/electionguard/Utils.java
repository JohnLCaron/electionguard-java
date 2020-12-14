package sunya.electionguard;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

public class Utils {

  private static final List<String> truthy = ImmutableList.of("y", "yes", "t", "true", "on", "1");
  private static final List<String> falsey = ImmutableList.of("n", "no", "f", "false", "off", "0");

  /**
   * Mimic python Convert a string representation of truth to true (1) or false (0).
   * True values are 'y', 'yes', 't', 'true', 'on', and '1'; false values
   * are 'n', 'no', 'f', 'false', 'off', and '0'.  Raises ValueError if
   * 'val' is anything else.
   */
  static boolean strtobool(String val) {
    String clean = val.trim().toLowerCase();
    if (truthy.contains(clean)) return true;
    if (falsey.contains(clean)) return false;
    throw new RuntimeException("invalid truth value " + val);
  }

}
