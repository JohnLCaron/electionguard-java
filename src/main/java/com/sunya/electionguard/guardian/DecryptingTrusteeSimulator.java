package com.sunya.electionguard.guardian;

import com.sunya.electionguard.DecryptionProofRecovery;
import com.sunya.electionguard.DecryptionProofTuple;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;

import javax.annotation.Nullable;
import java.util.Optional;

public class DecryptingTrusteeSimulator implements DecryptingTrusteeIF {
  private final DecryptingTrustee delegate;

  public DecryptingTrusteeSimulator(DecryptingTrustee delegate) {
    this.delegate = delegate;
  }

  @Override
  public String id() {
    return delegate.id;
  }

  @Override
  public int xCoordinate() {
    return delegate.xCoordinate;
  }

  @Override
  public Group.ElementModP electionPublicKey() {
    return delegate.election_keypair.public_key;
  }

  @Override
  public Optional<DecryptionProofRecovery> compensatedDecrypt(
          String missing_guardian_id,
          ElGamal.Ciphertext elgamal,
          Group.ElementModQ extended_base_hash,
          @Nullable Group.ElementModQ nonce_seed) {

    return Optional.of(delegate.compensatedDecrypt(missing_guardian_id,
            elgamal,
            extended_base_hash,
            nonce_seed));
  }

  @Override
  public Optional<DecryptionProofTuple> partialDecrypt(ElGamal.Ciphertext elgamal, Group.ElementModQ extended_base_hash, @Nullable Group.ElementModQ nonce_seed) {
    return Optional.of(delegate.partialDecrypt(elgamal, extended_base_hash, nonce_seed));
  }

}
