package com.sunya.electionguard.guardian;

import com.sunya.electionguard.DecryptionProofTuple;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;

import javax.annotation.Nullable;
import java.util.Optional;

public interface DecryptingTrusteeIF {
  String id();

  int xCoordinate();

  Group.ElementModP electionPublicKey();

  Optional<DecryptionProofTuple> compensatedDecrypt(
          String missing_guardian_id,
          ElGamal.Ciphertext elgamal,
          Group.ElementModQ extended_base_hash,
          @Nullable Group.ElementModQ nonce_seed);

  Optional<DecryptionProofTuple> partialDecrypt(
          ElGamal.Ciphertext elgamal,
          Group.ElementModQ extended_base_hash,
          @Nullable Group.ElementModQ nonce_seed);

  Optional<Group.ElementModP> recoverPublicKey(String missing_guardian_id);
}
