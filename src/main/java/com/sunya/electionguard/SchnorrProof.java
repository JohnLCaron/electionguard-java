package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;

import javax.annotation.concurrent.Immutable;
import java.util.Objects;

import static com.sunya.electionguard.Group.*;
import static com.sunya.electionguard.Proof.Usage.SecretValue;

@Immutable
public class SchnorrProof extends Proof {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** k in the spec */
  public final ElementModP public_key;
  /** h in the spec */
  public final ElementModP commitment;
  /** c in the spec */
  public final ElementModQ challenge;
  /** u in the spec */
  public final ElementModQ response;

  public SchnorrProof(ElementModP public_key, ElementModP commitment, ElementModQ challenge, ElementModQ response) {
    super("SchnorrProof", SecretValue);
    this.public_key = Preconditions.checkNotNull(public_key);
    this.commitment = Preconditions.checkNotNull(commitment);
    this.challenge = Preconditions.checkNotNull(challenge);
    this.response = Preconditions.checkNotNull(response);
  }

  /**
   * Check validity of the `proof` for proving possession of the private key corresponding to `public_key`.
   * This is specification 2A and 2.B.
   * @return true if the transcript is valid, false if anything is wrong
   */
  boolean is_valid(ElementModQ crypto_base_hash) {
    ElementModP k = this.public_key;
    ElementModP h = this.commitment;
    ElementModQ u = this.response;
    boolean valid_public_key = k.is_valid_residue();
    boolean in_bounds_h = h.is_in_bounds();
    boolean in_bounds_u = u.is_in_bounds();

    // LOOK changed to follow validation spec 2.A. see issue #278
    boolean valid_2A = this.challenge.equals(Hash.hash_elems(crypto_base_hash, k, h));
    boolean valid_2B = g_pow_p(u).equals(Group.mult_p(h, Group.pow_p(k, this.challenge)));

    boolean success = valid_public_key && in_bounds_h && in_bounds_u && valid_2A && valid_2B;
    if (!success) {
      logger.atWarning().log("found an invalid Schnorr proof: %s", this);
    }
    return success;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    SchnorrProof that = (SchnorrProof) o;
    return public_key.equals(that.public_key) &&
            commitment.equals(that.commitment) &&
            challenge.equals(that.challenge) &&
            response.equals(that.response);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), public_key, commitment, challenge, response);
  }

  /**
   * Given an ElGamal keypair, generates a proof that the prover knows the secret key without revealing it.
   * @param keypair An ElGamal keypair.
   * @param nonce   A random element in [0,Q).
   */
  static SchnorrProof make_schnorr_proof(ElGamal.KeyPair keypair, ElementModQ nonce, ElementModQ crypto_base_hash) {
    ElementModP k = keypair.public_key;
    ElementModP h = g_pow_p(nonce);
    // LOOK changed to follow validation spec 2.A. see issue #278
    ElementModQ c = Hash.hash_elems(crypto_base_hash, k, h);
    ElementModQ u = a_plus_bc_q(nonce, keypair.secret_key, c);

    return new SchnorrProof(k, h, c, u);
  }
}
