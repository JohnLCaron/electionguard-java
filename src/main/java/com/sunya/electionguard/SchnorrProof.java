package com.sunya.electionguard;

import java.util.Objects;

import static com.sunya.electionguard.Group.*;
import static com.sunya.electionguard.Proof.Usage.SecretValue;

public class SchnorrProof extends Proof {
  /** k in the spec */
  final ElementModP publicKey;
  /** h in the spec */
  final ElementModP commitment;
  /** c in the spec */
  final ElementModQ challenge;
  /** u in the spec */
  final ElementModQ response;

  SchnorrProof(ElementModP publicKey, ElementModP commitment, ElementModQ challenge, ElementModQ response) {
    super("SchnorrProof", SecretValue);
    this.publicKey = publicKey;
    this.commitment = commitment;
    this.challenge = challenge;
    this.response = response;
  }

  /**
   *         Check validity of the `proof` for proving possession of the private key corresponding to `public_key`.
   *         @return true if the transcript is valid, false if anything is wrong
   */
  boolean is_valid() {
    ElementModP k = this.publicKey;
    ElementModP h = this.commitment;
    ElementModQ u = this.response;
    boolean valid_public_key = k.is_valid_residue();
    boolean in_bounds_h = h.is_in_bounds();
    boolean in_bounds_u = u.is_in_bounds();

    ElementModQ c = Hash.hash_elems(k, h);
    boolean valid_proof = g_pow_p(u).equals(Group.mult_p(h, Group.pow_p(k, c)));

    boolean success = valid_public_key && in_bounds_h && in_bounds_u && valid_proof;
    if (!success) {
      // log_warning("found an invalid Schnorr proof: %s", this);
    }

    return success;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    SchnorrProof that = (SchnorrProof) o;
    return publicKey.equals(that.publicKey) &&
            commitment.equals(that.commitment) &&
            challenge.equals(that.challenge) &&
            response.equals(that.response);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), publicKey, commitment, challenge, response);
  }

  /**
   *     Given an ElGamal keypair and a nonce, generates a proof that the prover knows the secret key without revealing it.
   *
   *     @param keypair An ElGamal keypair.
   *     @param r A random element in [0,Q).
   */
  static SchnorrProof make_schnorr_proof(ElGamal.KeyPair keypair, ElementModQ r) {
    ElementModP k = keypair.public_key;
    ElementModP h = g_pow_p(r);
    ElementModQ c = Hash.hash_elems(k, h);
    ElementModQ u = a_plus_bc_q(r, keypair.secret_key, c);

    return new SchnorrProof(k, h, c, u);
  }
}
