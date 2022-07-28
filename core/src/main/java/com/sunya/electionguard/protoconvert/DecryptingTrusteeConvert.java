package com.sunya.electionguard.protoconvert;

import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.decrypting.DecryptingTrustee;
import com.sunya.electionguard.keyceremony.KeyCeremony2;
import electionguard.protogen.TrusteeProto;

import java.util.List;
import java.util.Map;

import static com.sunya.electionguard.protoconvert.CommonConvert.publishElementModP;

public class DecryptingTrusteeConvert {

  public static TrusteeProto.DecryptingTrustee publishDecryptingTrustee(DecryptingTrustee trustee) {
    TrusteeProto.DecryptingTrustee.Builder builder = TrusteeProto.DecryptingTrustee.newBuilder();
    builder.setGuardianId(trustee.id());
    builder.setGuardianXCoordinate(trustee.xCoordinate());
    builder.setElectionKeypair(publishElgamalKeypair(trustee.electionKeypair()));
    trustee.secretKeyShares().forEach(k -> builder.addSecretKeyShares(publishElectionPartialKeyBackup(k)));
    trustee.coefficientCommitments().entrySet().forEach(entry -> builder.addCoefficientCommitments(publishCommitmentSet(entry)));
    return builder.build();
  }

  private static TrusteeProto.SecretKeyShare publishElectionPartialKeyBackup(KeyCeremony2.SecretKeyShare org) {
    TrusteeProto.SecretKeyShare.Builder builder = TrusteeProto.SecretKeyShare.newBuilder();
    builder.setGeneratingGuardianId(org.generatingGuardianId());
    builder.setDesignatedGuardianId(org.designatedGuardianId());
    builder.setDesignatedGuardianXCoordinate(org.designatedGuardianXCoordinate());
    builder.setEncryptedCoordinate(CommonConvert.publishHashedCiphertext(org.encryptedCoordinate()));
    builder.setError(org.error());
    return builder.build();
  }

  private static TrusteeProto.ElGamalKeypair publishElgamalKeypair(ElGamal.KeyPair keypair) {
    TrusteeProto.ElGamalKeypair.Builder builder = TrusteeProto.ElGamalKeypair.newBuilder();
    builder.setSecretKey(CommonConvert.publishElementModQ(keypair.secret_key()));
    builder.setPublicKey(CommonConvert.publishElementModP(keypair.public_key()));
    return builder.build();
  }

  private static TrusteeProto.CommitmentSet publishCommitmentSet(Map.Entry<String, List<Group.ElementModP>> entry) {
    TrusteeProto.CommitmentSet.Builder builder = TrusteeProto.CommitmentSet.newBuilder();
    builder.setGuardianId(entry.getKey());
    for (Group.ElementModP commitment : entry.getValue()) {
      builder.addCommitments(publishElementModP(commitment));
    }
    return builder.build();
  }

}
