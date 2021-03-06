package com.sunya.electionguard;

import com.google.common.base.Preconditions;

import javax.annotation.concurrent.Immutable;
import java.util.Objects;

import static com.sunya.electionguard.Group.G;
import static com.sunya.electionguard.Group.P;
import static com.sunya.electionguard.Group.Q;
import static com.sunya.electionguard.Group.int_to_p_unchecked;
import static com.sunya.electionguard.Group.int_to_q_unchecked;

/**
 * The cryptographic context of an election.
 * @see <a href="https://www.electionguard.vote/spec/0.95.0/3_Baseline_parameters/">Baseline Parameters</a>
 * for definition of 𝑄.
 * @see <a href="https://www.electionguard.vote/spec/0.95.0/4_Key_generation/#details-of-key-generation">Key Generation</a>
 * for defintion of K, 𝑄, 𝑄'.
 */
@Immutable
public class CiphertextElectionContext {

  /**
   * Makes a CiphertextElectionContext object. python: election.make_ciphertext_election_context().
   * python: make_ciphertext_election_context()
   * @param number_of_guardians The number of guardians necessary to generate the public key.
   * @param quorum The quorum of guardians necessary to decrypt an election.  Must be less than number_of_guardians.
   * @param joint_public_key the joint public key of the election, K.
   * @param description the election description.
   * @param commitment_hash all the public commitments for all the guardians = H(K 1,0 , K 1,1 , K 1,2 , ... ,
   *    K 1,k−1 , K 2,0 , K 2,1 , K 2,2 , ... , K 2,k−1 , ... , K n,0 , K n,1 , K n,2 , ... , K n,k−1 )
   */
  public static CiphertextElectionContext create(
          int number_of_guardians,
          int quorum,
          Group.ElementModP joint_public_key,
          Manifest description,
          Group.ElementModQ commitment_hash) {

    // What's a crypto_base_hash?
    // The metadata of this object are hashed together with the
    //  - prime modulus (𝑝),
    //  - subgroup order (𝑞),
    //  - generator (𝑔),
    //  - number of guardians (𝑛),
    //  - decryption threshold value (𝑘),
    //  to form a base hash code (𝑄) which will be incorporated
    //  into every subsequent hash computation in the election.

    //  What's a crypto_extended_base_hash?
    //  Once the baseline parameters have been produced and confirmed,
    //  all of the public guardian commitments 𝐾𝑖,𝑗 are hashed together
    //  with the base hash 𝑄 to form an extended base hash 𝑄' that will
    //  form the basis of subsequent hash computations.

    Group.ElementModQ crypto_base_hash = make_crypto_base_hash(number_of_guardians, quorum, description);
    Group.ElementModQ crypto_extended_base_hash = Hash.hash_elems(crypto_base_hash, commitment_hash);

    return new CiphertextElectionContext(
            number_of_guardians,
            quorum,
            joint_public_key,
            description.crypto_hash(),
            crypto_base_hash,
            crypto_extended_base_hash,
            commitment_hash);
  }

  public static Group.ElementModQ make_crypto_base_hash(int number_of_guardians, int quorum, Manifest election) {
    return Hash.hash_elems(int_to_p_unchecked(P),
                    int_to_q_unchecked(Q),
                    int_to_p_unchecked(G),
                    number_of_guardians, quorum, election.crypto_hash());
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////
  /** The number of guardians necessary to generate the public key. */
  public final int number_of_guardians;
  /** The quorum of guardians necessary to decrypt an election.  Must be less than number_of_guardians. */
  public final int quorum;
  /** The joint public key (K) in the ElectionGuard Spec. */
  public final Group.ElementModP elgamal_public_key;
  /** Hash of all the public commitments for all the guardians = H(K 1,0 , K 1,1 , K 1,2 , ..., K n,k−1 ). */
  public final Group.ElementModQ commitment_hash; // unused, here for compatibility with python.
  /** The Manifest.crypto_hash. */
  public final Group.ElementModQ manifest_hash;
  /** The base hash code (𝑄) in the ElectionGuard Spec. */
  public final Group.ElementModQ crypto_base_hash;
  /** The extended base hash code (𝑄') in the ElectionGuard Spec. */
  public final Group.ElementModQ crypto_extended_base_hash;

  /** Do not use directly, use CiphertextElectionContext.create() */
  public CiphertextElectionContext(int number_of_guardians, int quorum, Group.ElementModP jointPublicKey,
                                   Group.ElementModQ manifest_hash, Group.ElementModQ crypto_base_hash,
                                   Group.ElementModQ crypto_extended_base_hash, Group.ElementModQ commitment_hash) {
    this.number_of_guardians = number_of_guardians;
    this.quorum = quorum;
    this.elgamal_public_key = Preconditions.checkNotNull(jointPublicKey);
    this.manifest_hash = Preconditions.checkNotNull(manifest_hash);
    this.crypto_base_hash = Preconditions.checkNotNull(crypto_base_hash);
    this.crypto_extended_base_hash = Preconditions.checkNotNull(crypto_extended_base_hash);
    this.commitment_hash = commitment_hash; // Preconditions.checkNotNull(commitment_hash);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CiphertextElectionContext that = (CiphertextElectionContext) o;
    return number_of_guardians == that.number_of_guardians &&
            quorum == that.quorum &&
            elgamal_public_key.equals(that.elgamal_public_key) &&
            manifest_hash.equals(that.manifest_hash) &&
            crypto_base_hash.equals(that.crypto_base_hash) &&
            crypto_extended_base_hash.equals(that.crypto_extended_base_hash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(number_of_guardians, quorum, elgamal_public_key, manifest_hash, crypto_base_hash, crypto_extended_base_hash);
  }

  @Override
  public String toString() {
    return "CiphertextElectionContext{" +
            "number_of_guardians=" + number_of_guardians +
            ", quorum=" + quorum +
            '}';
  }
}
