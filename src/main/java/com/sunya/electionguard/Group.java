package com.sunya.electionguard;

import com.google.common.collect.Iterables;

import javax.annotation.concurrent.Immutable;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

public class Group {
  // Q: Final[int] = pow(2, 256) - 189
  static final BigInteger Q = BigInteger.TWO.pow(256).subtract(BigInteger.valueOf(189));
  static final BigInteger P = new BigInteger("1044388881413152506691752710716624382579964249047383780384233483283953907971553643537729993126875883902173634017777416360502926082946377942955704498542097614841825246773580689398386320439747911160897731551074903967243883427132918813748016269754522343505285898816777211761912392772914485521155521641049273446207578961939840619466145806859275053476560973295158703823395710210329314709715239251736552384080845836048778667318931418338422443891025911884723433084701207771901944593286624979917391350564662632723703007964229849154756196890615252286533089643184902706926081744149289517418249153634178342075381874131646013444796894582106870531535803666254579602632453103741452569793905551901541856173251385047414840392753585581909950158046256810542678368121278509960520957624737942914600310646609792665012858397381435755902851312071248102599442308951327039250818892493767423329663783709190716162023529669217300939783171415808233146823000766917789286154006042281423733706462905243774854543127239500245873582012663666430583862778167369547603016344242729592244544608279405999759391099775667746401633668308698186721172238255007962658564443858927634850415775348839052026675785694826386930175303143450046575460843879941791946313299322976993405829119");
  static final BigInteger G = new BigInteger("14245109091294741386751154342323521003543059865261911603340669522218159898070093327838595045175067897363301047764229640327930333001123401070596314469603183633790452807428416775717923182949583875381833912370889874572112086966300498607364501764494811956017881198827400327403252039184448888877644781610594801053753235453382508543906993571248387749420874609737451803650021788641249940534081464232937193671929586747339353451021712752406225276255010281004857233043241332527821911604413582442915993833774890228705495787357234006932755876972632840760599399514028393542345035433135159511099877773857622699742816228063106927776147867040336649025152771036361273329385354927395836330206311072577683892664475070720408447257635606891920123791602538518516524873664205034698194561673019535564273204744076336022130453963648114321050173994259620611015189498335966173440411967562175734606706258335095991140827763942280037063180207172918769921712003400007923888084296685269233298371143630883011213745082207405479978418089917768242592557172834921185990876960527013386693909961093302289646193295725135238595082039133488721800071459503353417574248679728577942863659802016004283193163470835709405666994892499382890912238098413819320185166580019604608311466");
  static final BigInteger Q_MINUS_ONE = Q.subtract(BigInteger.ONE);

  // R: Final[int] = ((P - 1) * pow(Q, -1, P)) % P
  static final BigInteger P_MINUS_ONE = P.subtract(BigInteger.ONE);
  static final BigInteger QINV_MODP = mult_inv_p(Q);
  static final BigInteger R = P_MINUS_ONE.multiply(QINV_MODP).mod(P);

  @Immutable
  static class ElementMod {
    final BigInteger elem; // maybe this is just an integer?

    ElementMod(BigInteger elem) {
      this.elem = elem;
    }

    BigInteger powMod(BigInteger exponent, BigInteger mod) {
      return elem.modPow(exponent, mod);
    }

    ElementModP modInverseP() {
      return new ElementModP(elem.modInverse(P));
    }

    BigInteger getBigInt() {
      return elem;
    }

    /**
     * Converts from the element to the hex String of the BigInteger bytes.
     * This is preferable to directly accessing `elem`, whose representation might change.
     */
    String to_hex() {
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
      return Utils.b16decode(this.to_hex(), false);
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

  @Immutable
  static class ElementModQ extends ElementMod {
    ElementModQ(BigInteger elem) {
      super(elem);
    }

    ElementModQ powMod(BigInteger exponent) {
      return new ElementModQ(powMod(exponent, Q));
    }

    /**
     * Validates that the element is actually within the bounds of [0,Q).
     */
    boolean is_in_bounds() {
      //         return 0 <= this.elem < Q;
      if (this.elem.compareTo(BigInteger.ZERO) < 0) {
        return false;
      }
      return this.elem.compareTo(Q) < 0;
    }

    @Override
    public String toString() {
      return "ElementModQ{'" + elem + '}';
    }
  }

  @Immutable
  static class ElementModP extends ElementMod {
    ElementModP(BigInteger elem) {
      super(elem);
    }

    ElementModP powMod(BigInteger exponent) {
      return new ElementModP(powMod(exponent, P));
    }

    /**
     * Validates that the element is actually within the bounds of [0,P).
     */
    boolean is_in_bounds() {
      //         return 0 <= this.elem < P;
      if (this.elem.compareTo(BigInteger.ZERO) < 0) {
        return false;
      }
      return this.elem.compareTo(P) < 0;
    }

    /**
     * Validates that this element is in Z^r_p.
     * Returns true if all is good, false if something's wrong.
     */
    boolean is_valid_residue() {
      boolean residue = pow_p(this, new ElementModQ(Q)).equals(ONE_MOD_P);
      return this.is_in_bounds() && residue;
    }

    @Override
    public String toString() {
      return "ElementModP{'" + elem + '}';
    }
  }

  // Common constants
  static final ElementModQ ZERO_MOD_Q = new ElementModQ(BigInteger.ZERO);
  static final ElementModQ ONE_MOD_Q = new ElementModQ(BigInteger.ONE);
  static final ElementModQ TWO_MOD_Q = new ElementModQ(BigInteger.TWO);

  static final ElementModP ZERO_MOD_P = new ElementModP(BigInteger.ZERO);
  static final ElementModP ONE_MOD_P = new ElementModP(BigInteger.ONE);
  static final ElementModP TWO_MOD_P = new ElementModP(BigInteger.TWO);

  /**
   * Given a hex string representing bytes, returns an ElementModQ.
   * Returns `None` if the number is out of the allowed
   * [0,Q) range.
   */
  static Optional<ElementModQ> hex_to_q(String input) {
    BigInteger b = new BigInteger(input, 16);
    if (b.compareTo(Q) < 0) {
      return Optional.of(new ElementModQ(b));
    } else {
      return Optional.empty();
    }
  }

  /**
   * Given a Python integer, returns an ElementModP.
   * Returns `None` if the number is out of the allowed [0,P) range.
   */
  static Optional<ElementModP> int_to_p(BigInteger biggy) {
    if (between(BigInteger.ZERO, biggy, P)) {
      return Optional.of(new ElementModP(biggy));
    } else {
      return Optional.empty();
    }
  }

  /**
   * Given a Python integer, returns an ElementModQ.
   * Returns `None` if the number is out of the allowed [0,Q) range.
   */
  static Optional<ElementModQ> int_to_q(BigInteger biggy) {
    if (between(BigInteger.ZERO, biggy, Q)) {
      return Optional.of(new ElementModQ(biggy));
    } else {
      return Optional.empty();
    }
  }

  /**
   * Given a Python integer, returns an ElementModP. Allows
   * for the input to be out-of-bounds, and thus creating an invalid
   * element (i.e., outside of [0,P)). Useful for tests or if
   * you're absolutely, positively, certain the input is in-bounds.
   */
  static ElementModP int_to_p_unchecked(BigInteger biggy) {
    return new ElementModP(biggy);
  }

  static ElementModQ int_to_q_unchecked(BigInteger biggy) {
    return new ElementModQ(biggy);
  }

  /**
   * Adds together one or more elements in Q, returns the sum mod Q
   */
  static ElementModQ add_qi(BigInteger... elems) {
    BigInteger t = BigInteger.ZERO;
    for (BigInteger e : elems) {
      t = t.add(e).mod(Q);
    }
    return new ElementModQ(t);
  }

  static ElementModQ add_q(ElementModQ... elems) {
    BigInteger t = BigInteger.ZERO;
    for (ElementModQ e : elems) {
      t = t.add(e.elem).mod(Q);
    }
    return new ElementModQ(t);
  }

  /**
   * Computes (a-b) mod q.
   */
  static ElementModQ a_minus_b_q(ElementModQ a, ElementModQ b) {
    BigInteger r1 = a.elem.subtract(b.elem);
    BigInteger r2 = r1.mod(Q);
    ElementModQ r3 = new ElementModQ(r2);

    return new ElementModQ(a.elem.subtract(b.elem).mod(Q));
  }

  static ElementModQ a_minus_b_q(BigInteger a, BigInteger b) {
    return new ElementModQ(a.subtract(b).mod(Q));
  }

  /**
   * Computes a/b mod p
   */
  static ElementModP div_p(ElementMod a, ElementMod b) {
    return div_p(a.elem, b.elem);
  }

  /**
   * Computes a/b mod p
   */
  static ElementModP div_p(BigInteger a, BigInteger b) {
    BigInteger inverse = b.modInverse(P);
    return new ElementModP(mult_pi(a, inverse));
  }

  /**
   * Computes a/b mod q
   */
  static ElementModQ div_q(ElementMod a, ElementMod b) {
    BigInteger inverse = b.elem.modInverse(Q);
    return mult_q(a, int_to_q_unchecked(inverse));
  }

  static ElementModQ div_q(BigInteger a, BigInteger b) {
    BigInteger inverse = b.modInverse(Q);
    return mult_q(a, inverse);
  }

  /**
   * Computes (Q - a) mod q.
   */
  static ElementModQ negate_q(ElementModQ a) {
    return new ElementModQ(Q.subtract(a.elem));
  }

  /**
   * Computes (a + b * c) mod q.
   */
  static ElementModQ a_plus_bc_q(ElementModQ a, ElementModQ b, ElementModQ c) {
    BigInteger product = b.elem.multiply(c.elem);
    BigInteger sum = a.elem.add(product);
    return new ElementModQ(sum.mod(Q));
  }

  static ElementModQ a_plus_bc_q(BigInteger a, BigInteger b, BigInteger c) {
    BigInteger product = b.multiply(c);
    BigInteger sum = a.add(product);
    return new ElementModQ(sum.mod(Q));
  }

  /**
   * Computes the multiplicative inverse mod p.
   */
  static ElementModP mult_inv_p(ElementMod elem) {
    return elem.modInverseP();
  }

  static public BigInteger mult_inv_p(BigInteger b) {
    return b.modInverse(P);
  }

  /**
   * Computes b^e mod p.
   *
   * @param b: An element in [0,P).
   * @param e: An element in [0,P).
   */
  static ElementModP pow_p(ElementMod b, ElementMod e) {
    return new ElementModP(b.powMod(e.elem, P));
  }

  static public BigInteger pow_p(BigInteger b, BigInteger e) {
    return b.modPow(e, P);
  }

  /**
   * Computes b^e mod q.
   * <p>
   * :param b: An element in [0,Q).
   * :param e: An element in [0,Q).
   */
  static ElementModQ pow_q(BigInteger b, BigInteger e) {
    return new ElementModQ(b.modPow(e, Q));
  }

  /**
   * Computes the product, mod p, of all elements.
   *
   * @param elems: Zero or more elements in [0,P).
   */
  static ElementModP mult_p(Collection<ElementModP> elems) {
    return mult_p(Iterables.toArray(elems, ElementModP.class));
  }

  static ElementModP mult_p(ElementModP... elems) {
    BigInteger product = BigInteger.ONE;
    for (ElementModP x : elems) {
      product = product.multiply(x.elem).mod(P);
    }
    return new ElementModP(product);
  }

  static ElementModP mult_p(ElementMod p1, ElementMod p2) {
    BigInteger product = p1.elem.multiply(p2.elem).mod(P);
    return new ElementModP(product);
  }

  public static BigInteger mult_p(Iterable<BigInteger> elems) {
    return mult_pi(Iterables.toArray(elems, BigInteger.class));
  }

  public static BigInteger mult_pi(BigInteger... elems) {
    BigInteger product = BigInteger.ONE;
    for (BigInteger x : elems) {
      product = product.multiply(x).mod(P);
    }
    return product;
  }

  /**
   * Computes the product, mod q, of all elements.
   *
   * @param elems Zero or more elements in [0,P).
   */
  static ElementModQ mult_q(ElementMod... elems) {
    BigInteger product = BigInteger.ONE;
    for (ElementMod x : elems) {
      product = product.multiply(x.elem).mod(Q);
    }
    return new ElementModQ(product);
  }

  static ElementModQ mult_q(BigInteger... elems) {
    BigInteger product = BigInteger.ONE;
    for (BigInteger x : elems) {
      product = product.multiply(x).mod(Q);
    }
    return new ElementModQ(product);
  }

  /**
   * Computes g^e mod p.
   */
  static ElementModP g_pow_p(ElementMod e) {
    return new ElementModP(pow_p(G, e.getBigInt()));
  }

  /**
   * Generate random number between 0 and Q
   */
  static ElementModQ rand_q() {
    BigInteger random = Secrets.randbelow(Q);
    return new ElementModQ(random);
  }

  /**
   * Generate random number between start and Q
   */
  static ElementModQ rand_range_q(ElementMod start) {
    BigInteger random = Secrets.randbetween(start.getBigInt(), Q);
    return new ElementModQ(random);
  }

  // is lower <= x < upper, ie is x in [lower, upper) ?
  static boolean between(BigInteger inclusive_lower_bound, BigInteger x, BigInteger exclusive_upper_bound) {
    if (x.compareTo(inclusive_lower_bound) < 0) {
      return false;
    }
    return x.compareTo(exclusive_upper_bound) < 0;
  }

  // is x >= b ?
  static boolean greaterThanEqual(BigInteger x, BigInteger b) {
    return x.compareTo(b) >= 0;
  }

  static boolean lessThan(BigInteger x, BigInteger b) {
    return x.compareTo(b) < 0;
  }
}
