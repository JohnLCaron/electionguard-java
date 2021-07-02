package com.sunya.electionguard;

import com.google.common.collect.Iterables;

import javax.annotation.concurrent.Immutable;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/** Wraps all computations on BigInteger. Could use GMP as alternate implementation. */
public class Group {
  // Q: Final[int] = pow(2, 256) - 189
  public static final BigInteger Q = BigInteger.TWO.pow(256).subtract(BigInteger.valueOf(189));
  public static final BigInteger P = new BigInteger("1044388881413152506691752710716624382579964249047383780384233483283953907971553643537729993126875883902173634017777416360502926082946377942955704498542097614841825246773580689398386320439747911160897731551074903967243883427132918813748016269754522343505285898816777211761912392772914485521155521641049273446207578961939840619466145806859275053476560973295158703823395710210329314709715239251736552384080845836048778667318931418338422443891025911884723433084701207771901944593286624979917391350564662632723703007964229849154756196890615252286533089643184902706926081744149289517418249153634178342075381874131646013444796894582106870531535803666254579602632453103741452569793905551901541856173251385047414840392753585581909950158046256810542678368121278509960520957624737942914600310646609792665012858397381435755902851312071248102599442308951327039250818892493767423329663783709190716162023529669217300939783171415808233146823000766917789286154006042281423733706462905243774854543127239500245873582012663666430583862778167369547603016344242729592244544608279405999759391099775667746401633668308698186721172238255007962658564443858927634850415775348839052026675785694826386930175303143450046575460843879941791946313299322976993405829119");
  public static final BigInteger G = new BigInteger("14245109091294741386751154342323521003543059865261911603340669522218159898070093327838595045175067897363301047764229640327930333001123401070596314469603183633790452807428416775717923182949583875381833912370889874572112086966300498607364501764494811956017881198827400327403252039184448888877644781610594801053753235453382508543906993571248387749420874609737451803650021788641249940534081464232937193671929586747339353451021712752406225276255010281004857233043241332527821911604413582442915993833774890228705495787357234006932755876972632840760599399514028393542345035433135159511099877773857622699742816228063106927776147867040336649025152771036361273329385354927395836330206311072577683892664475070720408447257635606891920123791602538518516524873664205034698194561673019535564273204744076336022130453963648114321050173994259620611015189498335966173440411967562175734606706258335095991140827763942280037063180207172918769921712003400007923888084296685269233298371143630883011213745082207405479978418089917768242592557172834921185990876960527013386693909961093302289646193295725135238595082039133488721800071459503353417574248679728577942863659802016004283193163470835709405666994892499382890912238098413819320185166580019604608311466");
  static final BigInteger Q_MINUS_ONE = Q.subtract(BigInteger.ONE);

  // R: Final[int] = ((P - 1) * pow(Q, -1, P)) % P
  static final BigInteger P_MINUS_ONE = P.subtract(BigInteger.ONE);
  public static final BigInteger R = P_MINUS_ONE.multiply(Q.modInverse(P)).mod(P);

  // Common constants
  public static final ElementModQ ZERO_MOD_Q = new ElementModQ(BigInteger.ZERO);
  public static final ElementModQ ONE_MOD_Q = new ElementModQ(BigInteger.ONE);
  public static final ElementModQ TWO_MOD_Q = new ElementModQ(BigInteger.TWO);

  public static final ElementModP ZERO_MOD_P = new ElementModP(BigInteger.ZERO);
  public static final ElementModP ONE_MOD_P = new ElementModP(BigInteger.ONE);
  public static final ElementModP TWO_MOD_P = new ElementModP(BigInteger.TWO);

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
      return between(BigInteger.ZERO, elem, Q);
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
      return between(BigInteger.ZERO, elem, P);
    }

    /**
     * Validates that this element is in Z^r_p.
     * y ∈ Z^r_p if and only if y^q mod p = 1
     */
    public boolean is_valid_residue() {
      boolean residue = elem.modPow(Q, P).equals(BigInteger.ONE);
      return between(BigInteger.ONE, elem, P) && residue;
    }

    @Override
    public String toString() {
      return "ElementModP{" + elem + '}';
    }

    private final int ndigitsShort = 8;
    public String toShortString() {
      String longString = toString();
      int len = longString.length();
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
    if (b.compareTo(Q) < 0) {
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
    if (between(BigInteger.ZERO, biggy, P)) {
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
    if (between(BigInteger.ZERO, biggy, Q)) {
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
      t = t.add(e.elem).mod(Q);
    }
    return int_to_q_unchecked(t);
  }

  /** Compute (a-b) mod q. */
  static ElementModQ a_minus_b_q(ElementModQ a, ElementModQ b) {
    return int_to_q_unchecked(a.elem.subtract(b.elem).mod(Q));
  }

  /** Compute a/b mod p. */
  public static ElementModP div_p(ElementMod a, ElementMod b) {
    BigInteger inverse = b.elem.modInverse(P);
    BigInteger product = a.elem.multiply(inverse);
    return int_to_p_unchecked(product.mod(P));
  }

  /** Compute a/b mod q. */
  static ElementModQ div_q(ElementMod a, ElementMod b) {
    BigInteger inverse = b.elem.modInverse(Q);
    BigInteger product = a.elem.multiply(inverse);
    return int_to_q_unchecked(product.mod(Q));
  }

  /** Compute (Q - a) mod q. */
  static ElementModQ negate_q(ElementModQ a) {
    return int_to_q_unchecked(Q.subtract(a.elem));
  }

  /** Compute (a + b * c) mod q. */
  static ElementModQ a_plus_bc_q(ElementModQ a, ElementModQ b, ElementModQ c) {
    BigInteger product = b.elem.multiply(c.elem).mod(Q);
    BigInteger sum = a.elem.add(product);
    return int_to_q_unchecked(sum.mod(Q));
  }

  /** Compute the multiplicative inverse mod p. */
  static ElementModP mult_inv_p(ElementMod elem) {
    return int_to_p_unchecked(elem.elem.modInverse(P));
  }

  // https://www.electionguard.vote/spec/0.95.0/9_Verifier_construction/#modular-exponentiation
  /** Compute b^e mod p. */
  static ElementModP pow_p(ElementModP b, ElementModP e) {
    return int_to_p_unchecked(pow_pi(b.elem.mod(P), e.elem));
  }

  /** Compute b^e mod p. */
  public static ElementModP pow_p(ElementMod b, ElementMod e) {
    return int_to_p_unchecked(pow_pi(b.elem.mod(P), e.elem));
  }

  /** Compute b^e mod p. */
  static public BigInteger pow_pi(BigInteger b, BigInteger e) {
    return b.modPow(e, P);
  }

  /** Compute b^e mod q. */
  public static ElementModQ pow_q(BigInteger b, BigInteger e) {
    return int_to_q_unchecked(b.modPow(e, Q));
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
      product = product.multiply(x.elem).mod(P);
    }
    return int_to_p_unchecked(product);
  }

  public static ElementModP mult_p(ElementMod p1, ElementMod p2) {
    BigInteger product = p1.elem.multiply(p2.elem).mod(P);
    return int_to_p_unchecked(product);
  }

  public static BigInteger mult_pi(BigInteger... elems) {
    BigInteger product = BigInteger.ONE;
    for (BigInteger x : elems) {
      product = product.multiply(x).mod(P);
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
      product = product.multiply(x.elem).mod(Q);
    }
    return int_to_q_unchecked(product);
  }

  /** Compute g^e mod p. */
  public static ElementModP g_pow_p(ElementMod e) {
    return int_to_p_unchecked(pow_pi(G, e.elem));
  }

  /** Generate random number between 0 and Q. */
  public static ElementModQ rand_q() {
    BigInteger random = Utils.randbelow(Q);
    return int_to_q_unchecked(random);
  }

  /** Generate random number between start and Q. */
  static ElementModQ rand_range_q(ElementMod start) {
    BigInteger random = Utils.randbetween(start.getBigInt(), Q);
    return int_to_q_unchecked(random);
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
