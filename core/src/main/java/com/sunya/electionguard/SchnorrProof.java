package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.util.Objects;

import static com.sunya.electionguard.Group.*;
import static com.sunya.electionguard.Proof.Usage.SecretValue;

/**
 * Implements the Schnorr proof of knowledge protocol.
 * A zero-knowledge proof that the holder knows x = log_g(y) (discrete log base g, mod p) without revealing x.
 * @see <a href="https://en.wikipedia.org/wiki/Proof_of_knowledge#Schnorr_protocol">Schnorr Proof</a>
 */
@Immutable
public class SchnorrProof extends Proof {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final boolean throwException = false;

  /** The commitment K_ij. */
  public final ElementModP publicKey;
  /** h in the spec = g^nonce.*/
  @Nullable
  private final ElementModP commitment;
  /** c in the spec */
  public final ElementModQ challenge;
  /** u in the spec */
  public final ElementModQ response;

  public SchnorrProof(ElementModP publicKey, @Nullable ElementModP commitment, ElementModQ challenge, ElementModQ response) {
    super("SchnorrProof", SecretValue);
    this.publicKey = Preconditions.checkNotNull(publicKey);
    this.commitment = commitment;
    this.challenge = Preconditions.checkNotNull(challenge);
    this.response = Preconditions.checkNotNull(response);
  }

  public ElementModP getCommitment() {
    return (commitment != null) ? commitment :
            Group.div_p( Group.g_pow_p(this.response), Group.pow_p(this.publicKey, this.challenge)); // h
  }

  /**
   * Check validity of the proof of possession of the private key corresponding to public_key.
   * This is specification 2A and 2.B.
   * @return true if the proof is valid, false if anything is wrong
   */
  public boolean is_valid() {
    ElementModP k = this.publicKey;
    ElementModP h = this.getCommitment();
    ElementModQ u = this.response;
    boolean valid_public_key = k.is_valid_residue();
    boolean in_bounds_h = h.is_in_bounds();
    boolean in_bounds_u = u.is_in_bounds();

    // Changed validation spec 2.A. see issue #278
    boolean valid_2A = this.challenge.equals(Hash.hash_elems(k, h));
    boolean valid_2B = g_pow_p(u).equals(Group.mult_p(h, Group.pow_p(k, this.challenge)));

    boolean success = valid_public_key && in_bounds_h && in_bounds_u && valid_2A && valid_2B;
    if (!success) {
      logger.atWarning().log("found an invalid Schnorr proof: %s constants = %s",
              this, Group.getPrimes().name);
      if (throwException) {
        throw new IllegalStateException();
      }
    }
    return success;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    SchnorrProof that = (SchnorrProof) o;
    return publicKey.equals(that.publicKey) && Objects.equals(commitment, that.commitment) && challenge.equals(that.challenge) && response.equals(that.response);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), publicKey, commitment, challenge, response);
  }

  @Override
  public String toString() {
    return "SchnorrProof{" +
            "public_key=" + publicKey.toShortString() +
            ", commitment=" + getCommitment().toShortString() +
            ", challenge=" + challenge +
            ", response=" + response +
            "} " + super.toString();
  }

  /**
   * Given an ElGamal keypair, generates a proof that the prover knows the secret key without revealing it.
   * @param keypair An ElGamal keypair.
   * @param nonce   A random element in [0,Q).
   */
  public static SchnorrProof make_schnorr_proof(ElGamal.KeyPair keypair, ElementModQ nonce) {
    ElementModP k = keypair.public_key();
    ElementModP h = g_pow_p(nonce);
    // Changed validation spec 2.A. see issue #278
    ElementModQ c = Hash.hash_elems(k, h);
    ElementModQ u = a_plus_bc_q(nonce, keypair.secret_key(), c);
    return new SchnorrProof(k, h, c, u);
  }
}
