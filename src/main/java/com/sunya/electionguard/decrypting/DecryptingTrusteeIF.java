package com.sunya.electionguard.decrypting;

import com.sunya.electionguard.DecryptionProofTuple;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;

import javax.annotation.Nullable;
import java.util.List;

public interface DecryptingTrusteeIF {
  /** Guardian id. */
  String id();
  /** Guardian x coordinate (sequence_number). */
  int xCoordinate();
  /** Elgamal election public key = K_i. */
  Group.ElementModP electionPublicKey();

  List<DecryptionProofRecovery> compensatedDecrypt(
          String missing_guardian_id,
          List<ElGamal.Ciphertext> texts,
          Group.ElementModQ extended_base_hash,
          @Nullable Group.ElementModQ nonce_seed);

  List<DecryptionProofTuple> partialDecrypt(
          List<ElGamal.Ciphertext> texts,
          Group.ElementModQ extended_base_hash,
          @Nullable Group.ElementModQ nonce_seed);

}
