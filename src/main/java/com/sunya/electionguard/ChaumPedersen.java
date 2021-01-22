package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;

import javax.annotation.concurrent.Immutable;
import java.math.BigInteger;
import java.util.Formatter;
import java.util.Optional;

import static com.sunya.electionguard.Group.*;

public class ChaumPedersen {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Representation of disjunctive Chaum Pederson proof. */
  @Immutable
  public static class DisjunctiveChaumPedersenProof extends Proof {
    public final ElementModP proof_zero_pad; // a0 in the spec
    public final ElementModP proof_zero_data; // b0 in the spec
    public final ElementModP proof_one_pad; // a1 in the spec
    public final ElementModP proof_one_data; // b1 in the spec
    public final ElementModQ proof_zero_challenge; // c0 in the spec
    public final ElementModQ proof_one_challenge; // c1 in the spec
    public final ElementModQ challenge; // c in the spec
    public final ElementModQ proof_zero_response; // proof_zero_response in the spec
    public final ElementModQ proof_one_response; // proof_one_response in the spec

    public DisjunctiveChaumPedersenProof(ElementModP proof_zero_pad, ElementModP proof_zero_data,
                                         ElementModP proof_one_pad, ElementModP proof_one_data, ElementModQ proof_zero_challenge,
                                         ElementModQ proof_one_challenge, ElementModQ challenge, ElementModQ proof_zero_response, ElementModQ proof_one_response) {
      super("DisjunctiveChaumPedersenProof", Proof.Usage.SelectionValue);
      this.proof_zero_pad = Preconditions.checkNotNull(proof_zero_pad);
      this.proof_zero_data = Preconditions.checkNotNull(proof_zero_data);
      this.proof_one_pad = Preconditions.checkNotNull(proof_one_pad);
      this.proof_one_data = Preconditions.checkNotNull(proof_one_data);
      this.proof_zero_challenge = Preconditions.checkNotNull(proof_zero_challenge);
      this.proof_one_challenge = Preconditions.checkNotNull(proof_one_challenge);
      this.challenge = Preconditions.checkNotNull(challenge);
      this.proof_zero_response = Preconditions.checkNotNull(proof_zero_response);
      this.proof_one_response = Preconditions.checkNotNull(proof_one_response);
    }

    /**
     * Validates a "disjunctive" Chaum-Pedersen (zero or one) proof.
     * <p>
     * @param message: The ciphertext message
     * @param k: The public key of the election
     * @param qbar: The extended base hash of the election
     */
    boolean is_valid(ElGamal.Ciphertext message, ElementModP k, ElementModQ qbar) {
      ElementModP alpha = message.pad;
      ElementModP beta = message.data;

      ElementModP a0 = this.proof_zero_pad;
      ElementModP b0 = this.proof_zero_data;
      ElementModP a1 = this.proof_one_pad;
      ElementModP b1 = this.proof_one_data;
      ElementModQ c0 = this.proof_zero_challenge;
      ElementModQ c1 = this.proof_one_challenge;
      ElementModQ c = this.challenge;
      ElementModQ v0 = this.proof_zero_response;
      ElementModQ v1 = this.proof_one_response;

      boolean in_bounds_alpha = alpha.is_valid_residue();
      boolean in_bounds_beta = beta.is_valid_residue();
      boolean in_bounds_a0 = a0.is_valid_residue();
      boolean in_bounds_b0 = b0.is_valid_residue();
      boolean in_bounds_a1 = a1.is_valid_residue();
      boolean in_bounds_b1 = b1.is_valid_residue();
      boolean in_bounds_c0 = c0.is_in_bounds();
      boolean in_bounds_c1 = c1.is_in_bounds();
      boolean in_bounds_v0 = v0.is_in_bounds();
      boolean in_bounds_v1 = v1.is_in_bounds();

      boolean consistent_c = add_q(c0, c1).equals(c) && c.equals(Hash.hash_elems(qbar, alpha, beta, a0, b0, a1, b1));
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
        f.format("found an invalid Disjunctive Chaum-Pedersen proof:%n");
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
        f.format(" k %s%n", k);
        f.format(" proof %s%n", this);
        logger.atInfo().log(f.toString());
      }
      return success;
    }
  }

  /** Representation of a generic Chaum-Pedersen Zero Knowledge proof. */
  @Immutable
  public static class ChaumPedersenProof extends Proof {
    public final ElementModP pad; // a in the spec
    public final ElementModP data; // b in the spec
    public final ElementModQ challenge; // c in the spec
    public final ElementModQ response; // v in the spec

    public ChaumPedersenProof(ElementModP pad, ElementModP data, ElementModQ challenge, ElementModQ response) {
      super("ChaumPedersenProof", Proof.Usage.SecretValue);
      this.pad = Preconditions.checkNotNull(pad);
      this.data = Preconditions.checkNotNull(data);
      this.challenge = Preconditions.checkNotNull(challenge);
      this.response = Preconditions.checkNotNull(response);
    }

    /**
     * Validates a Chaum-Pedersen proof.
     * e.g.
     * - The given value 𝑣𝑖 is in the set Z𝑞
     * - The given values 𝑎𝑖 && 𝑏𝑖 are both in the set Z𝑞^𝑟
     * - The challenge value 𝑐 satisfies 𝑐 = 𝐻(𝑄, (𝐴, 𝐵), (𝑎 , 𝑏 ), 𝑀 ).
     * - that the equations 𝑔^𝑣𝑖 = 𝑎𝑖𝐾^𝑐𝑖 mod 𝑝 && 𝐴^𝑣𝑖 = 𝑏𝑖𝑀𝑖^𝑐𝑖 mod 𝑝 are satisfied.
     * <p>
     * @param message: The ciphertext message
     * @param k: The public key corresponding to the private key used to encrypt
     * (e.g. the Guardian public election key)
     * @param m: The value being checked for validity
     * @param q: The extended base hash of the election
     */
    boolean is_valid(ElGamal.Ciphertext message, ElementModP k, ElementModP m, ElementModQ q) {
      ElementModP alpha = message.pad;
      ElementModP beta = message.data;
      ElementModP a = this.pad;
      ElementModP b = this.data;
      ElementModQ c = this.challenge;
      ElementModQ v = this.response;
      boolean in_bounds_alpha = alpha.is_valid_residue();
      boolean in_bounds_beta = beta.is_valid_residue();
      boolean in_bounds_k = k.is_valid_residue();
      boolean in_bounds_m = m.is_valid_residue();
      boolean in_bounds_a = a.is_valid_residue();
      boolean in_bounds_b = b.is_valid_residue();
      boolean in_bounds_c = c.is_in_bounds();
      boolean in_bounds_v = v.is_in_bounds();
      boolean in_bounds_q = q.is_in_bounds();

      boolean same_c = c.equals(Hash.hash_elems(q, alpha, beta, a, b, m));
      boolean consistent_gv = (in_bounds_v && in_bounds_a && in_bounds_c &&
              // The equation 𝑔^𝑣𝑖 = 𝑎𝑖𝐾^𝑐𝑖
              g_pow_p(v).equals(mult_p(a, pow_p(k, c))));

      // The equation 𝐴^𝑣𝑖 = 𝑏𝑖𝑀𝑖^𝑐𝑖 mod 𝑝
      boolean temp = pow_p(alpha, v).equals(mult_p(b, pow_p(m, c)));
      boolean consistent_av = (in_bounds_alpha && in_bounds_b && in_bounds_c && in_bounds_v && temp);

      boolean success = in_bounds_alpha && in_bounds_beta && in_bounds_k && in_bounds_m && in_bounds_a && in_bounds_b
              && in_bounds_c && in_bounds_v && in_bounds_q && same_c && consistent_gv && consistent_av;

      if (!success) {
        logger.atWarning().log("found an invalid Chaum-Pedersen proof, " +
                String.format("in_bounds_alpha %s%n", in_bounds_alpha) +
                String.format("in_bounds_beta %s%n", in_bounds_beta) +
                String.format("in_bounds_k %s%n", in_bounds_k) +
                String.format("in_bounds_m %s%n", in_bounds_m) +
                String.format("in_bounds_a %s%n", in_bounds_a) +
                String.format("in_bounds_b %s%n", in_bounds_b) +
                String.format("in_bounds_c %s%n", in_bounds_c) +
                String.format("in_bounds_v %s%n", in_bounds_v) +
                String.format("in_bounds_q %s%n", in_bounds_q) +
                String.format("same_c %s%n", same_c) +
                String.format("consistent_gv %s%n", consistent_gv) +
                String.format("consistent_av %s%n", consistent_av) +
                String.format("k %s%n", k) +
                String.format("q %s%n", q) +
                String.format("proof %s%n", this));
      }
      return success;
    }
  }

  /** Representation of constant Chaum Pederson proof */
  @Immutable
  public static class ConstantChaumPedersenProof extends Proof {
    public final ElementModP pad; // a in the spec
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

    /**
     * Validates a "constant" Chaum-Pedersen proof.
     * e.g. that the equations 𝑔𝑉 = 𝑎𝐴𝐶 mod 𝑝 and 𝑔𝐿𝐾𝑣 = 𝑏𝐵𝐶 mod 𝑝 are satisfied.
     * <p>
     * @param message: The ciphertext message
     * @param k: The public key of the election
     * @param q: The extended base hash of the election
     */
    boolean is_valid(ElGamal.Ciphertext message, ElementModP k, ElementModQ q) {

      ElementModP alpha = message.pad;
      ElementModP beta = message.data;
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
      boolean same_c = c.equals(Hash.hash_elems(q, alpha, beta, a, b));
      boolean consistent_gv = in_bounds_v && in_bounds_a && in_bounds_alpha && in_bounds_c &&
              // The equation 𝑔^𝑉 = 𝑎𝐴^𝐶 mod 𝑝
              g_pow_p(v).equals(mult_p(a, pow_p(alpha, c)));

      // The equation 𝑔^𝐿𝐾^𝑣 = 𝑏𝐵^𝐶 mod 𝑝
      boolean consistent_kv = in_bounds_constant &&
              mult_p(g_pow_p(mult_p(c, constant_q)), pow_p(k, v)).equals(mult_p(b, pow_p(beta, c)));

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
        logger.atInfo().log("found an invalid Constant Chaum-Pedersen proof:%n%s",
        String.format(" in_bounds_alpha %s%n" +
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
                sane_constant, same_c, consistent_gv, consistent_kv));
      }
      return success;
    }
  }

  /**
   * Produce a "disjunctive" proof that an encryption of a given plaintext is either an encrypted zero or one.
   * This is just a front-end helper for `make_disjunctive_chaum_pedersen_zero` and
   * `make_disjunctive_chaum_pedersen_one`.
   * <p>
   * @param message: An ElGamal ciphertext
   * @param r: The nonce used creating the ElGamal ciphertext
   * @param k: The ElGamal public key for the election
   * @param q: A value used when generating the challenge, usually the election extended base hash (𝑄')
   * @param seed: Used to generate other random values here
   * @param plaintext: Zero or one
   */
  static DisjunctiveChaumPedersenProof make_disjunctive_chaum_pedersen(
          ElGamal.Ciphertext message,
          ElementModQ r,
          ElementModP k,
          ElementModQ q,
          ElementModQ seed,
          BigInteger plaintext) {

    Preconditions.checkArgument(Group.betweenInclusive(BigInteger.ZERO, plaintext, BigInteger.ONE), "make_disjunctive_chaum_pedersen only supports plaintexts of 0 or 1");
    return (plaintext.equals(BigInteger.ZERO)) ? make_disjunctive_chaum_pedersen_zero(message, r, k, q, seed) :
            make_disjunctive_chaum_pedersen_one(message, r, k, q, seed);
  }

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
   * @param message: An ElGamal ciphertext
   * @param r: The nonce used creating the ElGamal ciphertext
   * @param k: The ElGamal public key for the election
   * @param q: A value used when generating the challenge,
   * usually the election extended base hash (𝑄')
   * @param seed: Used to generate other random values here
   */
  static DisjunctiveChaumPedersenProof make_disjunctive_chaum_pedersen_zero(
          ElGamal.Ciphertext message,
          ElementModQ r,
          ElementModP k,
          ElementModQ q,
          ElementModQ seed) {

    ElementModP alpha = message.pad;
    ElementModP beta = message.data;

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
    ElementModQ c = Hash.hash_elems(q, alpha, beta, a0, b0, a1, b1);
    ElementModQ c0 = a_minus_b_q(c, c1);
    ElementModQ v0 = a_plus_bc_q(u0, c0, r);

    return new DisjunctiveChaumPedersenProof(a0, b0, a1, b1, c0, c1, c, v0, v1);
  }


  /**
   * Produces a "disjunctive" proof that an encryption of one is either an encrypted zero or one.
   * <p>
   * @param message: An ElGamal ciphertext
   * @param r: The nonce used creating the ElGamal ciphertext
   * @param k: The ElGamal public key for the election
   * @param q: A value used when generating the challenge,
   * usually the election extended base hash (𝑄')
   * @param seed: Used to generate other random values here
   */
  static DisjunctiveChaumPedersenProof make_disjunctive_chaum_pedersen_one(
          ElGamal.Ciphertext message,
          ElementModQ r,
          ElementModP k,
          ElementModQ q,
          ElementModQ seed) {

    ElementModP alpha = message.pad;
    ElementModP beta = message.data;

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
    ElementModQ c = Hash.hash_elems(q, alpha, beta, a0, b0, a1, b1);
    ElementModQ c1 = a_minus_b_q(c, c0);
    ElementModQ v1 = a_plus_bc_q(u1, c1, r);

    return new DisjunctiveChaumPedersenProof(a0, b0, a1, b1, c0, c1, c, v0, v1);
  }

  /**
   *     Produces a proof that a given value corresponds to a specific encryption.
   *     computes: 𝑀 =𝐴^𝑠𝑖 mod 𝑝 and 𝐾𝑖 = 𝑔^𝑠𝑖 mod 𝑝
   *
   *     @param message: An ElGamal ciphertext
   *     @param s: The nonce or secret used to derive the value
   *     @param m: The value we are trying to prove
   *     @param seed: Used to generate other random values here
   *     @param hash_header: A value used when generating the challenge,
   *                         usually the election extended base hash (𝑄')
   */
  static ChaumPedersenProof make_chaum_pedersen(
          ElGamal.Ciphertext message,
          ElementModQ s,
          ElementModP m,
          ElementModQ seed,
          ElementModQ hash_header) {

    ElementModP alpha = message.pad;
    ElementModP beta = message.data;

    // Pick one random number in Q.
    ElementModQ u = new Nonces(seed, "constant-chaum-pedersen-proof").get(0);
    ElementModP a = g_pow_p(u);  // 𝑔^𝑢𝑖 mod 𝑝
    ElementModP b = pow_p(alpha, u);  // 𝐴^𝑢𝑖 mod 𝑝
    ElementModQ c = Hash.hash_elems(hash_header, alpha, beta, a, b, m);  // sha256(𝑄', A, B, a𝑖, b𝑖, 𝑀𝑖)
    ElementModQ v = a_plus_bc_q(u, c, s);  // (𝑢𝑖 + 𝑐𝑖𝑠𝑖) mod 𝑞

    return new ChaumPedersenProof(a, b, c, v);
  }

  /**
   * Produces a proof that a given encryption corresponds to a specific total value.
   * <p>
   * @param message: An ElGamal ciphertext
   * @param constant: The plaintext constant value used to make the ElGamal ciphertext (L in the spec)
   * @param r: The aggregate nonce used creating the ElGamal ciphertext
   * @param k: The ElGamal public key for the election
   * @param seed: Used to generate other random values here
   * @param crypto_extended_base_hash: usually the election extended base hash (𝑄')
   */
  static ConstantChaumPedersenProof make_constant_chaum_pedersen(
          ElGamal.Ciphertext message,
          int constant,
          ElementModQ r,
          ElementModP k,
          ElementModQ seed,
          ElementModQ crypto_extended_base_hash) {

    ElementModP alpha = message.pad;
    ElementModP beta = message.data;

    // Pick one random number in Q.
    ElementModQ u = new Nonces(seed, "constant-chaum-pedersen-proof").get(0);
    ElementModP a = g_pow_p(u);  // 𝑔^𝑢𝑖 mod 𝑝
    ElementModP b = pow_p(k, u);  // 𝐴^𝑢𝑖 mod 𝑝
    ElementModQ c = Hash.hash_elems(crypto_extended_base_hash, alpha, beta, a, b); // sha256(𝑄', A, B, a, b)
    ElementModQ v = a_plus_bc_q(u, c, r);

    return new ConstantChaumPedersenProof(a, b, c, v, constant);
  }
}
