package com.sunya.electionguard;

import com.google.common.collect.Iterables;

import javax.annotation.concurrent.Immutable;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/** Wraps all computations on BigInteger. */
public class Group {
  // Common constants
  public static final ElementModQ ZERO_MOD_Q = new ElementModQ(BigInteger.ZERO);
  public static final ElementModQ ONE_MOD_Q = new ElementModQ(BigInteger.ONE);
  public static final ElementModQ TWO_MOD_Q = new ElementModQ(BigInteger.TWO);

  public static final ElementModP ZERO_MOD_P = new ElementModP(BigInteger.ZERO);
  public static final ElementModP ONE_MOD_P = new ElementModP(BigInteger.ONE);
  public static final ElementModP TWO_MOD_P = new ElementModP(BigInteger.TWO);

  private static ElectionConstants primes = ElectionConstants.get(ElectionConstants.PrimeOption.Standard);
  public static ElectionConstants getPrimes() { return primes; }
  public static void setPrimes(ElectionConstants usePrimes) {
    if (!usePrimes.getPrimeOptionType().equals(ElectionConstants.PrimeOption.Standard)) {
      System.out.printf("Setting non-standard primes %s%n", usePrimes.getPrimeOptionType());
    } else if (!primes.getPrimeOptionType().equals(ElectionConstants.PrimeOption.Standard)) {
      System.out.printf("Setting standard primes%n");
    }
    primes = usePrimes;
  }
  public static void setPrimesByName(String name) {
    ElectionConstants want = ElectionConstants.getByName(name);
    if (want == null) {
      System.out.printf("Cant find non-standard prime %s%n", name);
    } else {
      setPrimes(want);
    }
  }
  public static void reset() {
    primes = ElectionConstants.STANDARD_CONSTANTS;
  }

  @Immutable
  static class ElementMod {
    final BigInteger elem;

    ElementMod(BigInteger elem) {
      this.elem = elem;
    }

    public BigInteger getBigInt() {
      return elem;
    }

    /**
     * Converts from the element to the hex String of the BigInteger bytes.
     * This is preferable to directly accessing `elem`, whose representation might change.
     */
    public String to_hex() {
      String h = elem.toString(16);
      if (h.length() % 2 == 1) {
        h = "0" + h;
      }
      return h.toUpperCase();
    }

    /**
     * Converts from the element to the representation of bytes by first going through hex.
     * This is preferable to directly accessing `elem`, whose representation might change.
     */
    byte[] to_bytes() {
      return Utils.b16decode(this.to_hex());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ElementMod)) return false;
      ElementMod that = (ElementMod) o;
      return elem.equals(that.elem);
    }

    @Override
    public int hashCode() {
      return Objects.hash(elem);
    }

    @Override
    public String toString() {
      return "ElementMod{elem=" + elem + '}';
    }
  }

  /** Elements in the Group Z_q: integers mod q. */
  @Immutable
  public static class ElementModQ extends ElementMod {
    private ElementModQ(BigInteger elem) {
      super(elem);
    }

    /** Validates that the element is actually within the bounds of [0,Q). */
    public boolean is_in_bounds() {
      return between(BigInteger.ZERO, elem, primes.smallPrime);
    }

    @Override
    public String toString() {
      return "ElementModQ{" + elem + '}';
    }
  }

  /** Elements in the Group Z_p: integers mod p. */
  @Immutable
  public static class ElementModP extends ElementMod {
    private ElementModP(BigInteger elem) {
      super(elem);
    }

    /** Validates that the element is actually within the bounds of [0,P). */
    public boolean is_in_bounds() {
      return between(BigInteger.ZERO, elem, primes.largePrime);
    }

    /**
     * Validates that this element is in Z^r_p.
     * y ∈ Z^r_p if and only if y^q mod p = 1
     */
    public boolean is_valid_residue() {
      boolean residue = elem.modPow(primes.smallPrime, primes.largePrime).equals(BigInteger.ONE);
      return between(BigInteger.ONE, elem, primes.largePrime) && residue;
    }

    @Override
    public String toString() {
      return "ElementModP{" + elem + '}';
    }

    public String toShortString() {
      String longString = toString();
      int len = longString.length();
      int ndigitsShort = 8;
      if (len > 13 + ndigitsShort) {
        return longString.substring(0, 13 + ndigitsShort) + "..." + longString.substring(len - ndigitsShort - 1, len);
      }
      return toString();
    }
  }

  /**
   * Given a hex string representing bytes, returns an ElementModQ.
   * Returns `None` if the number is out of the allowed [0,Q) range.
   */
  public static Optional<ElementModQ> hex_to_q(String input) {
    BigInteger b = new BigInteger(input, 16);
    if (b.compareTo(primes.smallPrime) < 0) {
      return Optional.of(new ElementModQ(b));
    } else {
      return Optional.empty();
    }
  }

  /**
   * Given a hex string representing bytes, returns an ElementModQ.
   * Does not check if the number is out of the allowed [0,Q) range.
   */
  public static ElementModQ hex_to_q_unchecked(String input) {
    BigInteger b = new BigInteger(input, 16);
    return new ElementModQ(b);
  }

  /**
   * Given a hex string representing bytes, returns an ElementModP.
   * Does not check if the number is out of the allowed [0,P) range.
   */
  public static ElementModP hex_to_p_unchecked(String input) {
    BigInteger b = new BigInteger(input, 16);
    return new ElementModP(b);
  }

  /**
   * Given a BigInteger, returns an ElementModP.
   * Returns `None` if the number is out of the allowed [0,P) range.
   */
  public static Optional<ElementModP> int_to_p(BigInteger biggy) {
    if (between(BigInteger.ZERO, biggy, primes.largePrime)) {
      return Optional.of(new ElementModP(biggy));
    } else {
      return Optional.empty();
    }
  }

  /**
   * Given a BigInteger, returns an ElementModQ.
   * Returns `None` if the number is out of the allowed [0,Q) range.
   */
  public static Optional<ElementModQ> int_to_q(BigInteger biggy) {
    if (between(BigInteger.ZERO, biggy, primes.smallPrime)) {
      return Optional.of(new ElementModQ(biggy));
    } else {
      return Optional.empty();
    }
  }

  /**
   * Given a BigInteger, returns an ElementModP. Allows
   * for the input to be out-of-bounds, and thus creating an invalid
   * element (i.e., outside of [0,P)). Useful for tests or if
   * you're absolutely, positively, certain the input is in-bounds.
   */
  public static ElementModP int_to_p_unchecked(BigInteger biggy) {
    return new ElementModP(biggy);
  }

  /**
    Given a Python integer, returns an ElementModQ. Allows
    for the input to be out-of-bounds, and thus creating an invalid
    element (i.e., outside of [0,Q)). Useful for tests of it
    you're absolutely, positively, certain the input is in-bounds.
   */
  public static ElementModQ int_to_q_unchecked(BigInteger biggy) {
    return new ElementModQ(biggy);
  }

  // https://www.electionguard.vote/spec/0.95.0/9_Verifier_construction/#modular-addition
  /** Adds together one or more elements in Q, returns the sum mod Q */
  public static ElementModQ add_q(ElementModQ... elems) {
    BigInteger t = BigInteger.ZERO;
    for (ElementModQ e : elems) {
      t = t.add(e.elem).mod(primes.smallPrime);
    }
    return int_to_q_unchecked(t);
  }

  /** Compute (a-b) mod q. */
  static ElementModQ a_minus_b_q(ElementModQ a, ElementModQ b) {
    return int_to_q_unchecked(a.elem.subtract(b.elem).mod(primes.smallPrime));
  }

  /** Compute a/b mod p. */
  public static ElementModP div_p(ElementMod a, ElementMod b) {
    BigInteger inverse = b.elem.modInverse(primes.largePrime);
    BigInteger product = a.elem.multiply(inverse);
    return int_to_p_unchecked(product.mod(primes.largePrime));
  }

  /** Compute a/b mod q. */
  static ElementModQ div_q(ElementMod a, ElementMod b) {
    BigInteger inverse = b.elem.modInverse(primes.smallPrime);
    BigInteger product = a.elem.multiply(inverse);
    return int_to_q_unchecked(product.mod(primes.smallPrime));
  }

  /** Compute (Q - a) mod q. */
  static ElementModQ negate_q(ElementModQ a) {
    return int_to_q_unchecked(primes.smallPrime.subtract(a.elem));
  }

  /** Compute (a + b * c) mod q. */
  static ElementModQ a_plus_bc_q(ElementModQ a, ElementModQ b, ElementModQ c) {
    BigInteger product = b.elem.multiply(c.elem).mod(primes.smallPrime);
    BigInteger sum = a.elem.add(product);
    return int_to_q_unchecked(sum.mod(primes.smallPrime));
  }

  /** Compute the multiplicative inverse mod p. */
  static ElementModP mult_inv_p(ElementMod elem) {
    return int_to_p_unchecked(elem.elem.modInverse(primes.largePrime));
  }

  // https://www.electionguard.vote/spec/0.95.0/9_Verifier_construction/#modular-exponentiation
  /** Compute b^e mod p. */
  static ElementModP pow_p(ElementModP b, ElementModP e) {
    return int_to_p_unchecked(pow_pi(b.elem.mod(primes.largePrime), e.elem));
  }

  /** Compute b^e mod p. */
  public static ElementModP pow_p(ElementMod b, ElementMod e) {
    return int_to_p_unchecked(pow_pi(b.elem.mod(primes.largePrime), e.elem));
  }

  /** Compute b^e mod p. */
  static public BigInteger pow_pi(BigInteger b, BigInteger e) {
    return b.modPow(e, primes.largePrime);
  }

  /** Compute b^e mod q. */
  public static ElementModQ pow_q(BigInteger b, BigInteger e) {
    return int_to_q_unchecked(b.modPow(e, primes.smallPrime));
  }

  // https://www.electionguard.vote/spec/0.95.0/9_Verifier_construction/#modular-multiplication
  /**
   * Compute the product, mod p, of all elements.
   * @param elems: Zero or more elements in [0,P).
   */
  public static ElementModP mult_p(Collection<ElementModP> elems) {
    return mult_p(Iterables.toArray(elems, ElementModP.class));
  }

  static ElementModP mult_p(ElementModP... elems) {
    BigInteger product = BigInteger.ONE;
    for (ElementModP x : elems) {
      product = product.multiply(x.elem).mod(primes.largePrime);
    }
    return int_to_p_unchecked(product);
  }

  public static ElementModP mult_p(ElementMod p1, ElementMod p2) {
    BigInteger product = p1.elem.multiply(p2.elem).mod(primes.largePrime);
    return int_to_p_unchecked(product);
  }

  public static BigInteger mult_pi(BigInteger... elems) {
    BigInteger product = BigInteger.ONE;
    for (BigInteger x : elems) {
      product = product.multiply(x).mod(primes.largePrime);
    }
    return product;
  }

  /**
   * Compute the product, mod q, of all elements.
   * @param elems Zero or more elements in [0,Q).
   */
  public static ElementModQ mult_q(ElementModQ... elems) {
    BigInteger product = BigInteger.ONE;
    for (ElementMod x : elems) {
      product = product.multiply(x.elem).mod(primes.smallPrime);
    }
    return int_to_q_unchecked(product);
  }

  /** Compute g^e mod p. */
  public static ElementModP g_pow_p(ElementMod e) {
    return int_to_p_unchecked(pow_pi(primes.generator, e.elem));
  }

  /** Generate random number between 0 and Q. */
  public static ElementModQ rand_q() {
    BigInteger random = Utils.randbelow(primes.smallPrime);
    return int_to_q_unchecked(random);
  }

  /** Generate random number between start and Q. */
  static ElementModQ rand_range_q(ElementMod start) {
    BigInteger random = Utils.randbetween(start.getBigInt(), primes.smallPrime);
    return int_to_q_unchecked(random);
  }

  // is lower <= x < upper, ie is x in [lower, upper) ?
  public static boolean between1andQ(BigInteger x) {
    if (x.compareTo(BigInteger.ONE) < 0) {
      return false;
    }
    return x.compareTo(primes.smallPrime) < 0;
  }

  // is lower <= x < upper, ie is x in [lower, upper) ?
  public static boolean between(BigInteger inclusive_lower_bound, BigInteger x, BigInteger exclusive_upper_bound) {
    if (x.compareTo(inclusive_lower_bound) < 0) {
      return false;
    }
    return x.compareTo(exclusive_upper_bound) < 0;
  }

  // is lower <= x <= upper, ie is x in [lower, upper] ?
  static boolean betweenInclusive(BigInteger inclusive_lower_bound, BigInteger x, BigInteger inclusive_upper_bound) {
    if (x.compareTo(inclusive_lower_bound) < 0) {
      return false;
    }
    return x.compareTo(inclusive_upper_bound) <= 0;
  }

  // is x >= b ?
  static boolean greaterThanEqual(BigInteger x, BigInteger b) {
    return x.compareTo(b) >= 0;
  }

  // is x < b ?
  public static boolean lessThan(BigInteger x, BigInteger b) {
    return x.compareTo(b) < 0;
  }

  /** Check if a is a divisor of b. */
  public static boolean is_divisor(BigInteger a, BigInteger b) {
    return a.mod(b).equals(BigInteger.ZERO);
  }

}
