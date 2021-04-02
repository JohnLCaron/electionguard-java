package com.sunya.electionguard.guardian;

import com.sunya.electionguard.DecryptionProofRecovery;
import com.sunya.electionguard.DecryptionProofTuple;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;

import javax.annotation.Nullable;
import java.util.Optional;

public interface DecryptingTrusteeIF {
  /** Guardian id. */
  String id();
  /** Guardian x coordinate (sequence_number). */
  int xCoordinate();
  /** Elgamal election public key. */
  Group.ElementModP electionPublicKey();

  Optional<DecryptionProofRecovery> compensatedDecrypt(
          String missing_guardian_id,
          ElGamal.Ciphertext message,
          Group.ElementModQ extended_base_hash,
          @Nullable Group.ElementModQ nonce_seed);

  Optional<DecryptionProofTuple> partialDecrypt(
          ElGamal.Ciphertext message,
          Group.ElementModQ extended_base_hash,
          @Nullable Group.ElementModQ nonce_seed);

}
