package sunya.electionguard;

import com.google.auto.value.AutoValue;

import java.util.List;

import static sunya.electionguard.Group.*;

public class KeyCeremony {

  /**     Details of key ceremony. */
  @AutoValue
  abstract static class CeremonyDetails {
    abstract int number_of_guardians();
    abstract int quorum();
  }

  /** A tuple of election key pair, proof and polynomial */
  @AutoValue
  abstract static class ElectionKeyPair {
    abstract ElGamal.KeyPair key_pair();
    abstract SchnorrProof proof();
    abstract ElectionPolynomial polynomial();
  }

  /** A tuple of election public key and owner information. */
  @AutoValue
  abstract static class ElectionPublicKey {
    abstract String owner_id();
    abstract SchnorrProof proof();
    abstract ElementModP key();
  }

  /** Public key set of auxiliary and election keys and owner information. */
  @AutoValue
  abstract static class PublicKeySet {
    abstract String owner_id();
    abstract int sequence_order();
    abstract String auxiliary_public_key();
    abstract ElementModP election_public_key();
    abstract SchnorrProof election_public_key_proof();
  }

  /** Pair of guardians involved in sharing. */
  @AutoValue
  abstract static class GuardianPair {
    abstract String owner_id();
    abstract String designated_id();
  }

  /** Election partial key backup used for key sharing. */
  @AutoValue
  abstract static class ElectionPartialKeyBackup {
    abstract String owner_id(); // The Id of the guardian that generated this backup
    abstract String designated_id(); // The Id of the guardian to receive this backup
    abstract int designated_sequence_order(); // The sequence order of the designated guardian
    abstract String encrypted_value(); // The encrypted coordinate corresponding to a secret election polynomial
    abstract List<ElementModP> coefficient_commitments(); // The public keys `K_ij`generated from the election polynomial coefficients
    abstract List<SchnorrProof> coefficient_proofs(); // the proofs of posession of the private keys for the election polynomial secret coefficients
  }

  /** Set of validation pieces for election key coefficients. */
  @AutoValue
  abstract static class CoefficientValidationSet {
    abstract String owner_id();
    abstract List<ElementModP>coefficient_commitments();
    abstract List<SchnorrProof> coefficient_proofs();
  }

  /** verification of election partial key used in key sharing. */
  @AutoValue
  abstract static class ElectionPartialKeyVerification {
    abstract String owner_id();
    abstract String designated_id();
    abstract String verifier_id();
    abstract boolean verified();
  }

  /** Challenge of election partial key used in key sharing. */
  @AutoValue
  abstract static class ElectionPartialKeyChallenge {
    abstract String owner_id();
    abstract String designated_id();
    abstract int designated_sequence_order(); // The sequence order of the designated guardian
    abstract ElementModQ value();
    abstract List<ElementModP>coefficient_commitments();
    abstract List<SchnorrProof> coefficient_proofs();
  }
}
