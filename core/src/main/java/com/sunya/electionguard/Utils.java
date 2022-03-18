package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;

import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/** Static replacements for Python library methods. */
class Utils {
  public static final DateTimeFormatter dtf =
          DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

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

  static String isTrue(BigInteger val) {
    return val.equals(BigInteger.ZERO) ? "false" : "true";
  }

  static String isTrue(Integer val) {
    return val == 0 ? "false" : "true";
  }

  /** Return a random BigInteger in the range [0, n). */
  static BigInteger randbelow(BigInteger exclusive_upper_bound) {
    Preconditions.checkArgument(BigInteger.ZERO.compareTo(exclusive_upper_bound) <= 0);
    return randbetween(BigInteger.ZERO, exclusive_upper_bound);
  }

  /** Return a random BigInteger in the range [lower, upper). */
  static BigInteger randbetween(BigInteger inclusive_lower_bound, BigInteger exclusive_upper_bound) {
    Preconditions.checkArgument(inclusive_lower_bound.compareTo(exclusive_upper_bound) < 0);
    Random random = ThreadLocalRandom.current();
    int numBits = exclusive_upper_bound.bitLength();
    BigInteger candidate = new BigInteger(numBits, random);
    while (!Group.between(inclusive_lower_bound, candidate, exclusive_upper_bound)) {
      candidate = new BigInteger(numBits, random);
    }
    return candidate;
  }

  /** Decode the Base16 encoded string s. */
  static byte[] b16decode(String s) {
    return BaseEncoding.base16().decode(s.toUpperCase());
  }

  public static String to_iso_date_string(OffsetDateTime date_time) {
    return date_time.toString();
    /* LOOK Should be:
    utc_datetime = (
            date_time.astimezone(timezone.utc).replace(microsecond = 0)
            if date_time.tzinfo
            else date_time.replace(microsecond = 0)
    )
    return utc_datetime.strftime("%Y-%m-%dT%H:%M:%SZ")
    */
  }
}
