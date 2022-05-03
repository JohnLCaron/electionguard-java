package com.sunya.electionguard.protoconvert;

import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.SchnorrProof;
import com.sunya.electionguard.keyceremony.KeyCeremony2;
import com.sunya.electionguard.keyceremony.KeyCeremonyTrustee;

import static com.sunya.electionguard.protoconvert.CommonConvert.publishElementModP;

import electionguard.protogen.TrusteeProto;

public class KeyCeremonyTrusteeToProto {

  public static TrusteeProto.DecryptingTrustee convertTrustee(KeyCeremonyTrustee trustee) {
    TrusteeProto.DecryptingTrustee.Builder builder = TrusteeProto.DecryptingTrustee.newBuilder();
    builder.setGuardianId(trustee.id);
    builder.setGuardianXCoordinate(trustee.xCoordinate);
    builder.setElectionKeypair(convertElgamalKeypair(trustee.secrets().election_key_pair));
    trustee.otherGuardianPartialKeyBackups.values().forEach(k -> builder.addSecretKeyShares(convertElectionPartialKeyBackup(k)));
    trustee.allGuardianPublicKeys.values().forEach(k -> builder.addCoefficientCommitments(convertCoefficients(k)));
    return builder.build();
  }

  private static TrusteeProto.SecretKeyShare convertElectionPartialKeyBackup(KeyCeremony2.PartialKeyBackup org) {
    TrusteeProto.SecretKeyShare.Builder builder = TrusteeProto.SecretKeyShare.newBuilder();
    builder.setGeneratingGuardianId(org.generatingGuardianId());
    builder.setDesignatedGuardianId(org.designatedGuardianId());
    builder.setDesignatedGuardianXCoordinate(org.designatedGuardianXCoordinate());
    if (org.coordinate() != null) {
      builder.setGeneratingGuardianValue(CommonConvert.publishElementModQ(org.coordinate()));
    }
    builder.setError(org.error());
    return builder.build();
  }

  private static TrusteeProto.ElGamalKeypair convertElgamalKeypair(ElGamal.KeyPair keypair) {
    TrusteeProto.ElGamalKeypair.Builder builder = TrusteeProto.ElGamalKeypair.newBuilder();
    builder.setSecretKey(CommonConvert.publishElementModQ(keypair.secret_key()));
    builder.setPublicKey(CommonConvert.publishElementModP(keypair.public_key()));
    return builder.build();
  }

  private static TrusteeProto.CommitmentSet convertCoefficients(KeyCeremony2.PublicKeySet publicKetSey) {
    TrusteeProto.CommitmentSet.Builder builder = TrusteeProto.CommitmentSet.newBuilder();
    builder.setGuardianId(publicKetSey.ownerId());
    for (SchnorrProof proof : publicKetSey.coefficientProofs()) {
      builder.addCommitments(publishElementModP(proof.publicKey));
    }
    return builder.build();
  }

}
