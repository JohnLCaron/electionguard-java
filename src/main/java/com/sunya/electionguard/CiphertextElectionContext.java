package com.sunya.electionguard;

import com.google.common.base.Preconditions;

import javax.annotation.concurrent.Immutable;
import java.util.Objects;

import static com.sunya.electionguard.Group.G;
import static com.sunya.electionguard.Group.P;
import static com.sunya.electionguard.Group.Q;

/** The cryptographic context of an election. */
@Immutable
public class CiphertextElectionContext {

  /**
   * Makes a CiphertextElectionContext object.
   * @param number_of_guardians The number of guardians necessary to generate the public key.
   * @param quorum The quorum of guardians necessary to decrypt an election.  Must be less than number_of_guardians.
   * @param elgamal_public_key the public key of the election.
   * @param description the election description.
   * @param commitment_hash all the public commitments for all the guardians = H(K 1,0 , K 1,1 , K 1,2 , ... ,
   *    K 1,k−1 , K 2,0 , K 2,1 , K 2,2 , ... , K 2,k−1 , ... , K n,0 , K n,1 , K n,2 , ... , K n,k−1 )
   */
  public static CiphertextElectionContext create(
          int number_of_guardians,
          int quorum,
          Group.ElementModP elgamal_public_key,
          Election.ElectionDescription description,
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
            elgamal_public_key,
            description.crypto_hash(),
            crypto_base_hash,
            crypto_extended_base_hash);
  }

  public static Group.ElementModQ make_crypto_base_hash(int number_of_guardians, int quorum, Election.ElectionDescription election) {
    return Hash.hash_elems(P, Q, G, number_of_guardians, quorum, election.crypto_hash());
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////
  public final int number_of_guardians; // The number of guardians necessary to generate the public key
  public final int quorum; // The quorum of guardians necessary to decrypt an election.  Must be less than `number_of_guardians`

  // the `joint public key (K)` in the [ElectionGuard Spec](https://github.com/microsoft/electionguard/wiki)
  public final Group.ElementModP elgamal_public_key;

  // The hash of the election metadata
  public final Group.ElementModQ description_hash;

  // the `base hash code (𝑄)` in the [ElectionGuard Spec](https://github.com/microsoft/electionguard/wiki)
  public final Group.ElementModQ crypto_base_hash;

  // the `extended base hash code (𝑄')` in the [ElectionGuard Spec](https://github.com/microsoft/electionguard/wiki)
  public final Group.ElementModQ crypto_extended_base_hash;

  /** Do not use directly, use CiphertextElectionContext.create() */
  public CiphertextElectionContext(int number_of_guardians, int quorum, Group.ElementModP jointPublicKey,
                                   Group.ElementModQ description_hash, Group.ElementModQ crypto_base_hash,
                                   Group.ElementModQ crypto_extended_base_hash) {
    this.number_of_guardians = number_of_guardians;
    this.quorum = quorum;
    this.elgamal_public_key = Preconditions.checkNotNull(jointPublicKey);
    this.description_hash = Preconditions.checkNotNull(description_hash);
    this.crypto_base_hash = Preconditions.checkNotNull(crypto_base_hash);
    this.crypto_extended_base_hash = Preconditions.checkNotNull(crypto_extended_base_hash);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CiphertextElectionContext that = (CiphertextElectionContext) o;
    return number_of_guardians == that.number_of_guardians &&
            quorum == that.quorum &&
            elgamal_public_key.equals(that.elgamal_public_key) &&
            description_hash.equals(that.description_hash) &&
            crypto_base_hash.equals(that.crypto_base_hash) &&
            crypto_extended_base_hash.equals(that.crypto_extended_base_hash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(number_of_guardians, quorum, elgamal_public_key, description_hash, crypto_base_hash, crypto_extended_base_hash);
  }

  @Override
  public String toString() {
    return "CiphertextElectionContext{" +
            "number_of_guardians=" + number_of_guardians +
            ", quorum=" + quorum +
            '}';
  }
}
