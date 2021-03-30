package com.sunya.electionguard.guardian;

import com.sunya.electionguard.DecryptionProofTuple;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;

import javax.annotation.Nullable;
import java.util.Optional;

public class DecryptingTrusteeProxy implements DecryptingTrusteeIF {
  private final DecryptingTrustee delegate;

  public DecryptingTrusteeProxy(DecryptingTrustee delegate) {
    this.delegate = delegate;
  }

  @Override
  public String id() {
    return delegate.id;
  }

  @Override
  public int xCoordinate() {
    return delegate.sequence_order;
  }

  @Override
  public Group.ElementModP electionPublicKey() {
    return delegate.election_keypair.public_key;
  }

  @Override
  public Optional<DecryptionProofTuple> compensatedDecrypt(
          String missing_guardian_id,
          ElGamal.Ciphertext elgamal,
          Group.ElementModQ extended_base_hash,
          @Nullable Group.ElementModQ nonce_seed) {

    return delegate.compensatedDecrypt(missing_guardian_id,
            elgamal,
            extended_base_hash,
            nonce_seed);
  }

  @Override
  public Optional<DecryptionProofTuple>  partialDecrypt(ElGamal.Ciphertext elgamal, Group.ElementModQ extended_base_hash, @Nullable Group.ElementModQ nonce_seed) {
    return Optional.of(delegate.partialDecrypt(elgamal, extended_base_hash, nonce_seed));
  }

  @Override
  public Optional<Group.ElementModP> recoverPublicKey(String missing_guardian_id) {
    return delegate.recoverPublicKey(missing_guardian_id);
  }

}
