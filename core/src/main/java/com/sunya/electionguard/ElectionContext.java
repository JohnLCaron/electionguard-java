package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.util.Map;
import java.util.Objects;

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
public class ElectionContext {
  public static final String SPEC_VERSION = "v0.95";

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
  public static ElectionContext create(
          int number_of_guardians,
          int quorum,
          Group.ElementModP joint_public_key,
          Manifest description,
          Group.ElementModQ commitment_hash,
          @Nullable Map<String, String> extended_data) {

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

    return new ElectionContext(
            number_of_guardians,
            quorum,
            joint_public_key,
            description.cryptoHash(),
            crypto_base_hash,
            crypto_extended_base_hash,
            commitment_hash,
            extended_data);
  }

  // TODO hash ElementModQ rather than BigInteger: whats the difference?
  public static Group.ElementModQ make_crypto_base_hash(int number_of_guardians, int quorum, Manifest election) {
    ElectionConstants primes = Group.getPrimes();
    return Hash.hash_elems(int_to_p_unchecked(primes.largePrime),
                    int_to_q_unchecked(primes.smallPrime),
                    int_to_p_unchecked(primes.generator),
                    number_of_guardians, quorum, election.cryptoHash());
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////
  /** The number of guardians necessary to generate the public key. */
  public final Integer numberOfGuardians;
  /** The quorum of guardians necessary to decrypt an election.  Must be less than number_of_guardians. */
  public final Integer quorum;
  /** The joint public key (K) in the ElectionGuard Spec. */
  public final Group.ElementModP jointPublicKey;
  /** Hash of all the public commitments for all the guardians = H(K 1,0 , K 1,1 , K 1,2 , ..., K n,k−1 ). */
  public final Group.ElementModQ commitmentHash; // unused, here for compatibility with python.
  /** The Manifest.crypto_hash. */
  public final Group.ElementModQ manifestHash;
  /** The base hash code (𝑄) in the ElectionGuard Spec. */
  public final Group.ElementModQ cryptoBaseHash;
  /** The extended base hash code (𝑄') in the ElectionGuard Spec. */
  public final Group.ElementModQ cryptoExtendedBaseHash;

  /** Data to allow extending the context for special cases. */
  @Nullable
  public final Map<String, String> extended_data;

  /** Do not use directly, use CiphertextElectionContext.create() */
  public ElectionContext(int numberOfGuardians, int quorum, Group.ElementModP jointPublicKey,
                         Group.ElementModQ manifestHash, Group.ElementModQ cryptoBaseHash,
                         Group.ElementModQ cryptoExtendedBaseHash, Group.ElementModQ commitmentHash,
                         @Nullable Map<String, String> extended_data) {
    this.numberOfGuardians = numberOfGuardians;
    this.quorum = quorum;
    this.jointPublicKey = Preconditions.checkNotNull(jointPublicKey);
    this.manifestHash = Preconditions.checkNotNull(manifestHash);
    this.cryptoBaseHash = Preconditions.checkNotNull(cryptoBaseHash);
    this.cryptoExtendedBaseHash = Preconditions.checkNotNull(cryptoExtendedBaseHash);
    this.commitmentHash = commitmentHash; // Preconditions.checkNotNull(commitment_hash);
    this.extended_data = extended_data == null ? null : ImmutableMap.copyOf(extended_data);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ElectionContext that = (ElectionContext) o;
    return Objects.equals(numberOfGuardians, that.numberOfGuardians) &&
            Objects.equals(quorum, that.quorum) &&
            jointPublicKey.equals(that.jointPublicKey) &&
            manifestHash.equals(that.manifestHash) &&
            cryptoBaseHash.equals(that.cryptoBaseHash) &&
            cryptoExtendedBaseHash.equals(that.cryptoExtendedBaseHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(numberOfGuardians, quorum, jointPublicKey, manifestHash, cryptoBaseHash, cryptoExtendedBaseHash);
  }

  public String toStringOld() {
    return "CiphertextElectionContext{" +
            "number_of_guardians=" + numberOfGuardians +
            ", quorum=" + quorum +
            '}';
  }

  @Override
  public String toString() {
    return "CiphertextElectionContext{" +
            "number_of_guardians=" + numberOfGuardians +
            ", quorum=" + quorum +
            ", elgamal_public_key=" + jointPublicKey +
            ", commitment_hash=" + commitmentHash +
            ", manifest_hash=" + manifestHash +
            ", crypto_base_hash=" + cryptoBaseHash +
            ", crypto_extended_base_hash=" + cryptoExtendedBaseHash +
            ", extended_data=" + extended_data +
            '}';
  }
}
