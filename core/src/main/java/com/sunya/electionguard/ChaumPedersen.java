package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.core.ChaumPedersenKt;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.math.BigInteger;
import java.util.Formatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.sunya.electionguard.Group.*;
import static com.sunya.electionguard.core.ElGamalKt.ciphertextOf;

/**
 * Implements the Chaum-Pedersen publicly verifiable secret sharing scheme.
 * Uses unpublished Microsoft Note "Efficient Implementation of ElectionGuard Ballot Encryption and Proofs"
 *
 * @see <a href="https://en.wikipedia.org/wiki/Publicly_Verifiable_Secret_Sharing#Chaum-Pedersen_Protocol">Chaum-Pedersen Protocol</a>
 */
public class ChaumPedersen {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * A disjunctive Chaum-Pederson proof.
   */
  @Immutable
  public static class DisjunctiveChaumPedersenProof extends Proof {
    public final ChaumPedersenProof proof0;
    public final ChaumPedersenProof proof1;
    public final ElementModQ challenge; // c in the spec

    // 1.0
    public DisjunctiveChaumPedersenProof(ElementModP proof_zero_pad, ElementModP proof_zero_data,
                                         ElementModP proof_one_pad, ElementModP proof_one_data, ElementModQ proof_zero_challenge,
                                         ElementModQ proof_one_challenge, ElementModQ challenge, ElementModQ proof_zero_response, ElementModQ proof_one_response) {
      super("DisjunctiveChaumPedersenProof", Proof.Usage.SelectionValue);
      this.proof0 = new ChaumPedersenProof(proof_zero_pad, proof_zero_data, proof_zero_challenge, proof_zero_response);
      this.proof1 = new ChaumPedersenProof(proof_one_pad, proof_one_data, proof_one_challenge, proof_one_response);
      this.challenge = Preconditions.checkNotNull(challenge);
    }

    // 2.0
    public DisjunctiveChaumPedersenProof(ChaumPedersenProof proof0, ChaumPedersenProof proof1, ElementModQ challenge) {
      super("DisjunctiveChaumPedersenProof2", Proof.Usage.SelectionValue);
      this.proof0 = Preconditions.checkNotNull(proof0);
      this.proof1 = Preconditions.checkNotNull(proof1);
      this.challenge = Preconditions.checkNotNull(challenge);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      DisjunctiveChaumPedersenProof that = (DisjunctiveChaumPedersenProof) o;
      return proof0.equals(that.proof0) && proof1.equals(that.proof1) && challenge.equals(that.challenge);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), proof0, proof1, challenge);
    }

    @Override
    public String toString() {
      return "DisjunctiveChaumPedersenProof{" +
              "\n proof_zero " + proof0 +
              "\n proof_one " + proof1 +
              "\n challenge=" + challenge +
              '}';
    }

    /**
     * Validates a "disjunctive" Chaum-Pedersen (zero or one) proof.
     *
     * @param message: The ciphertext message
     * @param k:       The public key of the election
     * @param qbar:    The extended base hash of the election
     */
    public boolean is_valid(ElGamal.Ciphertext message, ElementModP k, ElementModQ qbar) {
      if (this.name.endsWith("2")) {
        return is_valid2(message, k, qbar);
      } else {
        return is_valid1(message, k, qbar);
      }
    }

    // version 1
    boolean is_valid1(ElGamal.Ciphertext message, ElementModP k, ElementModQ qbar) {
      ElementModP alpha = message.pad();
      ElementModP beta = message.data();

      ChaumPedersenProof expanded0 = this.proof0.expand(message, k);
      ChaumPedersenProof expanded1 = this.proof1.expand(message, k);

      ElementModP a0 = expanded0.pad;
      ElementModP b0 = expanded0.data;
      ElementModP a1 = expanded1.pad;
      ElementModP b1 = expanded1.data;
      ElementModQ c0 = expanded0.challenge;
      ElementModQ c1 = expanded1.challenge;
      ElementModQ c = this.challenge;
      ElementModQ v0 = expanded0.response;
      ElementModQ v1 = expanded1.response;

      boolean in_bounds_alpha = alpha.is_valid_residue();
      boolean in_bounds_beta = beta.is_valid_residue();
      boolean in_bounds_a0 = a0 == null || a0.is_valid_residue();
      boolean in_bounds_b0 = b0 == null || b0.is_valid_residue();
      boolean in_bounds_a1 = a1 == null || a1.is_valid_residue();
      boolean in_bounds_b1 = b1 == null || b1.is_valid_residue();
      boolean in_bounds_c0 = c0.is_in_bounds();
      boolean in_bounds_c1 = c1.is_in_bounds();
      boolean in_bounds_v0 = v0.is_in_bounds();
      boolean in_bounds_v1 = v1.is_in_bounds();

      // LOOK 2.0 change from python 1.0, no longer check the hash
      boolean consistent_c = add_q(c0, c1).equals(c); // && c.equals(Hash.hash_elems(qbar, alpha, beta, a0, b0, a1, b1));

      boolean consistent_gv0 = g_pow_p(v0).equals(mult_p(a0, pow_p(alpha, c0)));
      boolean consistent_gv1 = g_pow_p(v1).equals(mult_p(a1, pow_p(alpha, c1)));
      boolean consistent_kv0 = pow_p(k, v0).equals(mult_p(b0, pow_p(beta, c0)));

      // consistent_gc1kv1 = mult_p(g_pow_p(c1), pow_p(k, v1)) == mult_p(b1, pow_p(beta, c1))
      boolean consistent_gc1kv1 = mult_p(g_pow_p(c1), pow_p(k, v1)).equals(mult_p(b1, pow_p(beta, c1)));

      boolean success = (in_bounds_alpha && in_bounds_beta && in_bounds_a0 && in_bounds_b0 && in_bounds_a1 &&
              in_bounds_b1 && in_bounds_c0 && in_bounds_c1 && in_bounds_v0 && in_bounds_v1 && consistent_c &&
              consistent_gv0 && consistent_gv1 && consistent_kv0 && consistent_gc1kv1);

      if (!success) {
        Formatter f = new Formatter();
        f.format("found an invalid disjunctive Chaum-Pedersen (zero or one) proof:%n");
        f.format(" in_bounds_alpha %s%n", in_bounds_alpha);
        f.format(" in_bounds_beta %s%n", in_bounds_beta);
        f.format(" in_bounds_a0 %s%n", in_bounds_a0);
        f.format(" in_bounds_b0 %s%n", in_bounds_b0);
        f.format(" in_bounds_a1  %s%n", in_bounds_a1);
        f.format(" in_bounds_b1 %s%n", in_bounds_b1);
        f.format(" in_bounds_c0 %s%n", in_bounds_c0);
        f.format(" in_bounds_c1 %s%n", in_bounds_c1);
        f.format(" in_bounds_v0 %s%n", in_bounds_v0);
        f.format(" in_bounds_v1 %s%n", in_bounds_v1);
        f.format(" consistent_c %s%n", consistent_c);
        f.format(" consistent_gv0 %s%n", consistent_gv0);
        f.format(" consistent_gv1 %s%n", consistent_gv1);
        f.format(" consistent_kv0 %s%n", consistent_kv0);
        f.format(" consistent_gc1kv1 %s%n", consistent_gc1kv1);
        f.format("  k %s%n", k.toShortString());
        f.format("  message %s%n", message);
        f.format("  qbar %s%n", qbar);
        throw new IllegalStateException(f.toString());
      }
      return success;
    }

    // version 2, from kotlin library
    boolean is_valid2(ElGamal.Ciphertext ciphertext, ElementModP publicKey, ElementModQ hashHeader) {
      ChaumPedersenKt.DisjunctiveChaumPedersenProofKnownNonce kt =
              new ChaumPedersenKt.DisjunctiveChaumPedersenProofKnownNonce(this);
      return kt.isValid(ciphertext, publicKey, hashHeader);
    }
  }

  /** A generic Chaum-Pedersen Zero Knowledge proof. */
  @Immutable
  public static class ChaumPedersenProof extends Proof {
    @Nullable
    public final ElementModP pad; // a in the spec
    @Nullable
    public final ElementModP data; // b in the spec
    public final ElementModQ challenge; // c in the spec
    public final ElementModQ response; // v in the spec

    public ChaumPedersenProof(ElementModP pad, ElementModP data, ElementModQ challenge, ElementModQ response) {
      super("ChaumPedersenProof", Proof.Usage.SecretValue);
      this.pad = pad;
      this.data = data;
      this.challenge = Preconditions.checkNotNull(challenge);
      this.response = Preconditions.checkNotNull(response);
    }

    public ChaumPedersenProof(ElementModQ challenge, ElementModQ response) {
      super("ChaumPedersenProof2", Proof.Usage.SecretValue);
      this.pad = null;
      this.data = null;
      this.challenge = Preconditions.checkNotNull(challenge);
      this.response = Preconditions.checkNotNull(response);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      ChaumPedersenProof that = (ChaumPedersenProof) o;
      return challenge.equals(that.challenge) && response.equals(that.response);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), pad, data, challenge, response);
    }

    @Override
    public String toString() {
      return "ChaumPedersenProof{" +
              "\n   pad      =" + (pad == null ? "null" : pad.toShortString()) +
              "\n   data     =" + (data == null ? "null" : data.toShortString()) +
              "\n   challenge=" + challenge +
              "\n   response =" + response +
              '}';
    }

    ChaumPedersenProof expand(ElGamal.Ciphertext ciphertext, ElementModP publicKey) {
      if (this.pad != null) {
        return this;
      }
        // version 2.0
      //             g = context.G_MOD_P,
      //            gx = ciphertext.pad,
      //            h = publicKey.key,
      //            hx = ciphertext.data,
      //     val negC = -c
      //    val a = (g powP r) * (gx powP negC)
      //    val b = (h powP r) * (hx powP negC)
      ElementModQ negC = Group.negate_q(this.challenge);
      Group.g_pow_p(this.response);
      ElementModP a = Group.mult_p(Group.g_pow_p(this.response), Group.pow_p(ciphertext.pad(), negC));
      ElementModP b = Group.mult_p(Group.pow_p(publicKey, this.response), Group.pow_p(ciphertext.data(), negC));
      return new ChaumPedersenProof(a, b, this.challenge, this.response);
    }

    /**
     * Validates a Chaum-Pedersen proof.
     * e.g.
     * - The given value 𝑣𝑖 is in the set Z𝑞
     * - The given values 𝑎𝑖 and 𝑏𝑖 are both in the set Z𝑞^𝑟
     * - The challenge value 𝑐 satisfies 𝑐 = 𝐻(𝑄, (𝐴, 𝐵), (𝑎 , 𝑏 ), 𝑀 ).
     * - that the equations 𝑔^𝑣𝑖 = 𝑎𝑖𝐾^𝑐𝑖 mod 𝑝 and 𝐴^𝑣𝑖 = 𝑏𝑖𝑀𝑖^𝑐𝑖 mod 𝑝 are satisfied.
     * <p>
     *
     * @param message: The ciphertext message
     * @param k:       The public key corresponding to the private key used to encrypt
     *                 (e.g. the Guardian public election key)
     * @param m:       The value being checked for validity
     * @param extBaseHash:       The extended base hash of the election
     */
    public boolean is_valid(ElGamal.Ciphertext message, ElementModP k, ElementModP m, ElementModQ extBaseHash) {
      if (name.endsWith("2")) {
        return isValidVer2(message, k, m, extBaseHash);
      } else {
        return isValidVer1(message, k, m, extBaseHash);
      }
    }

    boolean isValidVer2(ElGamal.Ciphertext ciphertext, ElementModP publicKey, ElementModP m, ElementModQ qbar) {
      ChaumPedersenKt.GenericChaumPedersenProof kt =
                      new ChaumPedersenKt.GenericChaumPedersenProof(this.challenge, this.response);
      // ElementModP g,
      //            ElementModP gx, // Ki = public key
      //            ElementModP h, // pad
      //            ElementModP hx, // Mi = partial_decrypt = pad ^ si
      //            List<Object> hashHeader,
      //            List<Object> hashFooter,
      //            boolean checkC
      return kt.isValid(
              getPrimes().generatorP,
              publicKey,
              ciphertext.pad(),
              m,
              List.of(qbar, publicKey, ciphertext.pad(), ciphertext.data()),
              List.of(m),
              true);
    }

    boolean isValidVer1(ElGamal.Ciphertext message, ElementModP k, ElementModP m, ElementModQ extBaseHash) {
      ChaumPedersenProof expanded = this.expand(message, k);
      ElementModP A = message.pad();
      ElementModP B = message.data();
      ElementModP a = expanded.pad;
      ElementModP b = expanded.data;
      ElementModQ c = expanded.challenge;
      ElementModQ v = expanded.response;

      boolean in_bounds_alpha = A.is_valid_residue();
      boolean in_bounds_beta = B.is_valid_residue();
      boolean in_bounds_k = k.is_valid_residue();
      boolean in_bounds_m = m.is_valid_residue();
      boolean in_bounds_a = a.is_valid_residue();
      boolean in_bounds_b = b.is_valid_residue();
      boolean in_bounds_c = c.is_in_bounds();
      boolean in_bounds_v = v.is_in_bounds();
      boolean in_bounds_q = extBaseHash.is_in_bounds();

      boolean same_c = c.equals(Hash.hash_elems(extBaseHash, A, B, a, b, m));
      boolean consistent_gv = (in_bounds_v && in_bounds_a && in_bounds_c &&
              // The equation 𝑔^𝑣𝑖 = 𝑎𝑖𝐾^𝑐𝑖
              g_pow_p(v).equals(mult_p(a, pow_p(k, c))));

      // The equation 𝐴^𝑣𝑖 = 𝑏𝑖𝑀𝑖^𝑐𝑖 mod 𝑝
      boolean temp = pow_p(A, v).equals(mult_p(b, pow_p(m, c)));
      boolean consistent_av = (in_bounds_alpha && in_bounds_b && in_bounds_c && in_bounds_v && temp);

      boolean success = in_bounds_alpha && in_bounds_beta && in_bounds_k && in_bounds_m && in_bounds_a && in_bounds_b
              && in_bounds_c && in_bounds_v && in_bounds_q && same_c && consistent_gv && consistent_av;

      if (!success) {
        String err = "found an invalid Chaum-Pedersen proof " +
                String.format("%n in_bounds_alpha %s%n", in_bounds_alpha) +
                String.format(" in_bounds_beta %s%n", in_bounds_beta) +
                String.format(" in_bounds_k %s%n", in_bounds_k) +
                String.format(" in_bounds_m %s%n", in_bounds_m) +
                String.format(" in_bounds_a %s%n", in_bounds_a) +
                String.format(" in_bounds_b %s%n", in_bounds_b) +
                String.format(" in_bounds_c %s%n", in_bounds_c) +
                String.format(" in_bounds_v %s%n", in_bounds_v) +
                String.format(" in_bounds_q %s%n", in_bounds_q) +
                String.format(" same_c %s%n", same_c) +
                String.format(" consistent_gv %s%n", consistent_gv) +
                String.format(" consistent_av %s%n", consistent_av) +
                String.format("%n pad %s%n", a.toShortString()) +
                String.format(" data %s%n", b.toShortString()) +
                String.format(" challenge %s%n", this.challenge) +
                String.format(" response %s%n", this.response);
                //String.format(" g_pow_p(v) %s%n", g_pow_p(v).toShortString()) +
                //String.format(" pow_p(k, c) %s%n", pow_p(k, c).toShortString()) +
                //String.format(" mult_p(a, pow_p(k, c)) %s%n", mult_p(a, pow_p(k, c)).toShortString());
        logger.atWarning().log(err);
        throw new IllegalStateException(err);
      }
      return success;
    }
  }

  /** A constant Chaum-Pederson proof */
  @Immutable
  public static class ConstantChaumPedersenProof extends Proof {
    @Nullable
    public final ElementModP pad; // a in the spec
    @Nullable
    public final ElementModP data; // b in the spec
    public final ElementModQ challenge; // c in the spec
    public final ElementModQ response; // v in the spec
    public final int constant; // constant value

    public ConstantChaumPedersenProof(ElementModP pad, ElementModP data, ElementModQ challenge, ElementModQ response, int constant) {
      super("ConstantChaumPedersenProof", Proof.Usage.SelectionLimit);
      this.pad = Preconditions.checkNotNull(pad);
      this.data = Preconditions.checkNotNull(data);
      this.challenge = Preconditions.checkNotNull(challenge);
      this.response = Preconditions.checkNotNull(response);
      this.constant = constant;
    }

    public ConstantChaumPedersenProof(ElementModQ challenge, ElementModQ response, int constant) {
      super("ConstantChaumPedersenProof2", Proof.Usage.SelectionLimit);
      this.pad = null;
      this.data = null;
      this.challenge = Preconditions.checkNotNull(challenge);
      this.response = Preconditions.checkNotNull(response);
      this.constant = constant;
    }

    @Override
    public String toString() {
      return "ConstantChaumPedersenProof{" +
              "\n   pad      =" + (pad == null ? "null" : pad.toShortString()) +
              "\n   data     =" + (data == null ? "null" : data.toShortString()) +
              "\n   challenge=" + challenge +
              "\n   response =" + response +
              "\n   constant =" + constant +
              '}';
    }

    public boolean is_valid(ElGamal.Ciphertext message, ElementModP k, ElementModQ qbar) {
      if (this.name.endsWith("2")) {
        return is_valid2(message, k, qbar);
      } else {
        return is_valid1(message, k, qbar);
      }
    }

    /**
     * Validates a "constant" Chaum-Pedersen proof.
     * e.g. that the equations 𝑔𝑉 = 𝑎𝐴𝐶 mod 𝑝 and 𝑔𝐿𝐾𝑣 = 𝑏𝐵𝐶 mod 𝑝 are satisfied.
     * <p>
     *
     * @param message:            The ciphertext message
     * @param publicKey:                  The public key of the election
     * @param extendedHash:       The extended base hash of the election
     */
    boolean is_valid1(ElGamal.Ciphertext message, ElementModP publicKey, ElementModQ extendedHash) {
      ElementModP alpha = message.pad();
      ElementModP beta = message.data();
      ElementModP a = this.pad;
      ElementModP b = this.data;
      ElementModQ c = this.challenge;
      ElementModQ v = this.response;
      int constant = this.constant;
      boolean in_bounds_alpha = alpha.is_valid_residue();
      boolean in_bounds_beta = beta.is_valid_residue();
      boolean in_bounds_a = a.is_valid_residue();
      boolean in_bounds_b = b.is_valid_residue();
      boolean in_bounds_c = c.is_in_bounds();
      boolean in_bounds_v = v.is_in_bounds();
      ElementModQ constant_q;
      boolean in_bounds_constant;
      Optional<ElementModQ> tmp = int_to_q(BigInteger.valueOf(constant));
      if (tmp.isEmpty()) {
        constant_q = ZERO_MOD_Q;
        in_bounds_constant = false;
      } else {
        constant_q = tmp.get();
        in_bounds_constant = true;
      }

      // this is an arbitrary constant check to verify that decryption will be performant
      // in some use cases this value may need to be increased
      boolean sane_constant = 0 <= constant && constant < 1_000_000_000;
      //     ElementModQ c = Hash.hash_elems(crypto_extended_base_hash, alpha, beta, a, b); // sha256(𝑄', A, B, a, b)
      boolean same_c = c.equals(Hash.hash_elems(extendedHash, alpha, beta, a, b));
      boolean consistent_gv = in_bounds_v && in_bounds_a && in_bounds_alpha && in_bounds_c &&
              // The equation 𝑔^𝑉 = 𝑎𝐴^𝐶 mod 𝑝
              g_pow_p(v).equals(mult_p(a, pow_p(alpha, c)));

      // LOOK
      // The equation 𝑔^𝐿𝐾^𝑣 = 𝑏𝐵^𝐶 mod 𝑝
      boolean consistent_kv = in_bounds_constant &&
              mult_p(g_pow_p(mult_p(c, constant_q)), pow_p(publicKey, v)).equals(mult_p(b, pow_p(beta, c)));

      boolean success = (
              in_bounds_alpha
                      && in_bounds_beta
                      && in_bounds_a
                      && in_bounds_b
                      && in_bounds_c
                      && in_bounds_v
                      && same_c
                      && in_bounds_constant
                      && sane_constant
                      && consistent_gv
                      && consistent_kv
      );

      if (!success) {
        String err = String.format("found an invalid Constant Chaum-Pedersen proof:%n" +
                        " in_bounds_alpha %s%n" +
                        " in_bounds_beta %s%n" +
                        " in_bounds_a %s%n" +
                        " in_bounds_b %s%n" +
                        " in_bounds_c %s%n" +
                        " in_bounds_v %s%n" +
                        " in_bounds_constant %s%n" +
                        " sane_constant %s%n" +
                        " same_c %s%n" +
                        " consistent_gv %s%n" +
                        " consistent_kv %s%n",
                in_bounds_alpha, in_bounds_beta, in_bounds_a, in_bounds_b, in_bounds_c, in_bounds_v, in_bounds_constant,
                sane_constant, same_c, consistent_gv, consistent_kv);
        throw new IllegalStateException(err);
      }
      return success;
    }

    // version 2, from kotlin library
    boolean is_valid2(ElGamal.Ciphertext ciphertext, ElementModP publicKey, ElementModQ hashHeader) {
      ChaumPedersenKt.ConstantChaumPedersenProofKnownNonce kt =
              new ChaumPedersenKt.ConstantChaumPedersenProofKnownNonce(
                      new ChaumPedersenKt.GenericChaumPedersenProof(this.challenge, this.response),
                      this.constant);
      return kt.isValid(ciphertextOf(ciphertext), publicKey, hashHeader, this.constant);
    }
  }

  /**
   * Produce a "disjunctive" proof that an encryption of a given plaintext is either an encrypted zero or one.
   * This is just a front-end helper for `make_disjunctive_chaum_pedersen_zero` and
   * `make_disjunctive_chaum_pedersen_one`.
   * <p>
   *
   * @param message:   An ElGamal ciphertext
   * @param r:         The nonce used creating the ElGamal ciphertext
   * @param k:         The ElGamal public key for the election
   * @param q:         A value used when generating the challenge, usually the election extended base hash (𝑄')
   * @param seed:      Used to generate other random values here
   * @param plaintext: Zero or one
   */
  static DisjunctiveChaumPedersenProof make_disjunctive_chaum_pedersen(
          ElGamal.Ciphertext message,
          ElementModQ r,
          ElementModP k,
          ElementModQ q,
          ElementModQ seed,
          int plaintext) {

    Preconditions.checkArgument(0 <= plaintext && plaintext <= 1, "make_disjunctive_chaum_pedersen only supports integers of 0 or 1");
    return (plaintext == 0) ? make_disjunctive_chaum_pedersen_zero(message, r, k, q, seed) :
            make_disjunctive_chaum_pedersen_one(message, r, k, q, seed);
  }


  /**
   * Produces a "disjunctive" proof that an encryption of zero is either an encrypted zero or one.
   * <p>
   *
   * @param message: An ElGamal ciphertext
   * @param r:       The nonce used creating the ElGamal ciphertext
   * @param k:       The ElGamal public key for the election
   * @param qbar:    A value used when generating the challenge, usually the election extended base hash (𝑄')
   * @param seed:    Used to generate other random values here
   */
  static DisjunctiveChaumPedersenProof make_disjunctive_chaum_pedersen_zero(
          ElGamal.Ciphertext message,
          ElementModQ r,
          ElementModP k,
          ElementModQ qbar,
          ElementModQ seed) {

    ElementModP alpha = message.pad();
    ElementModP beta = message.data();

    // Pick three random numbers in Q.
    Nonces nonces = new Nonces(seed, "disjoint-chaum-pedersen-proof");
    ElementModQ c1 = nonces.get(0);
    ElementModQ v = nonces.get(1);
    ElementModQ u0 = nonces.get(2);

    // Compute the NIZKP
    ElementModP a0 = g_pow_p(u0);
    ElementModP b0 = pow_p(k, u0);
    ElementModP a1 = g_pow_p(v);
    ElementModP b1 = mult_p(pow_p(k, v), g_pow_p(c1));

    ElementModQ c = Hash.hash_elems(qbar, alpha, beta, a0, b0, a1, b1);
    ElementModQ c0 = a_minus_b_q(c, c1);
    ElementModQ v0 = a_plus_bc_q(u0, c0, r);
    ElementModQ v1 = a_plus_bc_q(v, c1, r);

    return new DisjunctiveChaumPedersenProof(a0, b0, a1, b1, c0, c1, c, v0, v1);
  }


  /**
   * Produces a "disjunctive" proof that an encryption of one is either an encrypted zero or one.
   * <p>
   *
   * @param message: An ElGamal ciphertext
   * @param r:       The nonce used creating the ElGamal ciphertext
   * @param k:       The ElGamal public key for the election
   * @param qbar:    A value used when generating the challenge, usually the election extended base hash (𝑄')
   * @param seed:    Used to generate other random values here
   */
  static DisjunctiveChaumPedersenProof make_disjunctive_chaum_pedersen_one(
          ElGamal.Ciphertext message,
          ElementModQ r,
          ElementModP k,
          ElementModQ qbar,
          ElementModQ seed) {

    ElementModP alpha = message.pad();
    ElementModP beta = message.data();

    // Pick three random numbers in Q.
    Nonces nonces = new Nonces(seed, "disjoint-chaum-pedersen-proof");
    ElementModQ w = nonces.get(0);
    ElementModQ v = nonces.get(1);
    ElementModQ u1 = nonces.get(2);

    // Compute the NIZKP
    ElementModP a0 = g_pow_p(v);
    ElementModP b0 = mult_p(pow_p(k, v), g_pow_p(w));

    ElementModP a1 = g_pow_p(u1);
    ElementModP b1 = pow_p(k, u1);
    ElementModQ c = Hash.hash_elems(qbar, alpha, beta, a0, b0, a1, b1);
    ElementModQ c0 = negate_q(w);
    ElementModQ c1 = add_q(c, w);
    ElementModQ v0 = a_plus_bc_q(v, c0, r);
    ElementModQ v1 = a_plus_bc_q(u1, c1, r);

    return new DisjunctiveChaumPedersenProof(a0, b0, a1, b1, c0, c1, c, v0, v1);
  }

  /**
   * Produces a proof that a given value corresponds to a specific encryption.
   * computes: 𝑀 =𝐴^𝑠𝑖 mod 𝑝 and 𝐾𝑖 = 𝑔^𝑠𝑖 mod 𝑝
   *
   * @param message:     An ElGamal ciphertext
   * @param s:           The nonce or secret used to derive the value
   * @param m:           The value we are trying to prove, 𝑀𝑖
   * @param seed:        Used to generate other random values here
   * @param hash_header:  extended base hash (𝑄')
   */
  public static ChaumPedersenProof make_chaum_pedersen(
          ElGamal.Ciphertext message,
          ElementModQ s,
          ElementModP m,
          ElementModQ seed,
          ElementModQ hash_header) {

    ElementModP A = message.pad();
    ElementModP B = message.data();

    // Pick one random number in Q.
    ElementModQ u = new Nonces(seed, "constant-chaum-pedersen-proof").get(0);
    ElementModP a = g_pow_p(u);  // 𝑔^𝑢𝑖 mod 𝑝
    ElementModP b = pow_p(A, u);  // 𝐴^𝑢𝑖 mod 𝑝
    ElementModQ c = Hash.hash_elems(hash_header, A, B, a, b, m);  // sha256(𝑄', A, B, a𝑖, b𝑖, 𝑀𝑖)
    ElementModQ v = a_plus_bc_q(u, c, s);  // (𝑢𝑖 + 𝑐𝑖.𝑠𝑖) mod 𝑞

    return new ChaumPedersenProof(a, b, c, v);
  }

  /**
   * Produces a proof that a given encryption corresponds to a specific total value.
   * <p>
   *
   * @param message:                   An ElGamal ciphertext
   * @param constant:                  The plaintext constant value used to make the ElGamal ciphertext (L in the spec)
   * @param r:                         The aggregate nonce used creating the ElGamal ciphertext
   * @param k:                         The ElGamal public key for the election
   * @param seed:                      Used to generate other random values here
   * @param extendedHash: usually the election extended base hash (𝑄')
   */
  public static ConstantChaumPedersenProof make_constant_chaum_pedersen(
          ElGamal.Ciphertext message,
          int constant,
          ElementModQ r,
          ElementModP k,
          ElementModQ seed,
          ElementModQ extendedHash) {

    ElementModP alpha = message.pad();
    ElementModP beta = message.data();

    // Pick one random number in Q.
    ElementModQ u = new Nonces(seed, "constant-chaum-pedersen-proof").get(0);
    ElementModP a = g_pow_p(u);  // 𝑔^𝑢𝑖 mod 𝑝
    ElementModP b = pow_p(k, u);  // 𝐴^𝑢𝑖 mod 𝑝
    ElementModQ c = Hash.hash_elems(extendedHash, alpha, beta, a, b); // sha256(𝑄', A, B, a, b)
    ElementModQ v = a_plus_bc_q(u, c, r);

    ConstantChaumPedersenProof result =  new ConstantChaumPedersenProof(a, b, c, v, constant);
    /* ElGamal.Ciphertext message, ElementModP publicKey, ElementModQ extendedHash
    // if (!result.is_valid(message, k, extendedHash)) {
    //  throw new IllegalStateException("make_constant_chaum_pedersen");
    // }
    System.out.printf("***ConstantChaumPedersenProof result = %s%n", result);
    System.out.printf("    message = %s%n", message);
    System.out.printf("    extendedHash = %s%n", extendedHash);
    System.out.printf("    publicKey = %s%n", k.toShortString()); */
    return result;
  }

  /////////////////////////////////////////////////////////////////////

  static DisjunctiveChaumPedersenProof make_disjunctive_chaum_pedersen_org(
          ElGamal.Ciphertext message,
          ElementModQ r,
          ElementModP k,
          ElementModQ q,
          ElementModQ seed,
          int plaintext) {

    Preconditions.checkArgument(0 <= plaintext && plaintext <= 1, "make_disjunctive_chaum_pedersen only supports integers of 0 or 1");
    return (plaintext == 0) ? make_disjunctive_chaum_pedersen_zero_org(message, r, k, q, seed) :
            make_disjunctive_chaum_pedersen_one_org(message, r, k, q, seed);
  }


  /**
   * Produces a "disjunctive" proof that an encryption of zero is either an encrypted zero or one.
   * <p>
   *
   * @param message: An ElGamal ciphertext
   * @param r:       The nonce used creating the ElGamal ciphertext
   * @param k:       The ElGamal public key for the election
   * @param qbar:    A value used when generating the challenge, usually the election extended base hash (𝑄')
   * @param seed:    Used to generate other random values here
   */
  static DisjunctiveChaumPedersenProof make_disjunctive_chaum_pedersen_zero_org(
          ElGamal.Ciphertext message,
          ElementModQ r,
          ElementModP k,
          ElementModQ qbar,
          ElementModQ seed) {

    ElementModP alpha = message.pad();
    ElementModP beta = message.data();

    // Pick three random numbers in Q.
    Nonces nonces = new Nonces(seed, "disjoint-chaum-pedersen-proof");
    ElementModQ c1 = nonces.get(0);
    ElementModQ v1 = nonces.get(1);
    ElementModQ u0 = nonces.get(2);

    // Compute the NIZKP
    ElementModP a0 = g_pow_p(u0);
    ElementModP b0 = pow_p(k, u0);
    ElementModQ q_minus_c1 = negate_q(c1);
    ElementModP a1 = mult_p(g_pow_p(v1), pow_p(alpha, q_minus_c1));
    ElementModP b1 = mult_p(pow_p(k, v1), g_pow_p(c1), pow_p(beta, q_minus_c1));
    ElementModQ c = Hash.hash_elems(qbar, alpha, beta, a0, b0, a1, b1);
    ElementModQ c0 = a_minus_b_q(c, c1);
    ElementModQ v0 = a_plus_bc_q(u0, c0, r);

    return new DisjunctiveChaumPedersenProof(a0, b0, a1, b1, c0, c1, c, v0, v1);
  }


  /**
   * Produces a "disjunctive" proof that an encryption of one is either an encrypted zero or one.
   * <p>
   *
   * @param message: An ElGamal ciphertext
   * @param r:       The nonce used creating the ElGamal ciphertext
   * @param k:       The ElGamal public key for the election
   * @param qbar:    A value used when generating the challenge, usually the election extended base hash (𝑄')
   * @param seed:    Used to generate other random values here
   */
  static DisjunctiveChaumPedersenProof make_disjunctive_chaum_pedersen_one_org(
          ElGamal.Ciphertext message,
          ElementModQ r,
          ElementModP k,
          ElementModQ qbar,
          ElementModQ seed) {

    ElementModP alpha = message.pad();
    ElementModP beta = message.data();

    // Pick three random numbers in Q.
    Nonces nonces = new Nonces(seed, "disjoint-chaum-pedersen-proof");
    ElementModQ c0 = nonces.get(0);
    ElementModQ v0 = nonces.get(1);
    ElementModQ u1 = nonces.get(2);

    // Compute the NIZKP
    ElementModQ q_minus_c0 = negate_q(c0);
    ElementModP a0 = mult_p(g_pow_p(v0), pow_p(alpha, q_minus_c0));
    ElementModP b0 = mult_p(pow_p(k, v0), pow_p(beta, q_minus_c0));
    ElementModP a1 = g_pow_p(u1);
    ElementModP b1 = pow_p(k, u1);
    ElementModQ c = Hash.hash_elems(qbar, alpha, beta, a0, b0, a1, b1);
    ElementModQ c1 = a_minus_b_q(c, c0);
    ElementModQ v1 = a_plus_bc_q(u1, c1, r);

    return new DisjunctiveChaumPedersenProof(a0, b0, a1, b1, c0, c1, c, v0, v1);
  }
}