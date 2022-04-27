package com.sunya.electionguard.protoconvert;

import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.SchnorrProof;
import com.sunya.electionguard.decrypting.DecryptingTrustee;
import com.sunya.electionguard.keyceremony.KeyCeremony2;
import electionguard.protogen.TrusteeProto;

import java.util.List;
import java.util.Map;

import static com.sunya.electionguard.protoconvert.CommonConvert.publishElementModP;

public class DecryptingTrusteeToProto {

  public static TrusteeProto.DecryptingTrustee publishDecryptingTrustee(DecryptingTrustee trustee) {
    TrusteeProto.DecryptingTrustee.Builder builder = TrusteeProto.DecryptingTrustee.newBuilder();
    builder.setGuardianId(trustee.id());
    builder.setGuardianXCoordinate(trustee.xCoordinate());
    builder.setElectionKeyPair(publishElgamalKeypair(trustee.election_keypair()));
    trustee.otherGuardianPartialKeyBackups().values().forEach(k -> builder.addOtherGuardianBackups(publishElectionPartialKeyBackup(k)));
    trustee.guardianCommittments().entrySet().forEach(entry -> builder.addGuardianCommitments(publishCommitmentSet(entry)));
    return builder.build();
  }

  private static TrusteeProto.ElectionPartialKeyBackup2 publishElectionPartialKeyBackup(KeyCeremony2.PartialKeyBackup org) {
    TrusteeProto.ElectionPartialKeyBackup2.Builder builder = TrusteeProto.ElectionPartialKeyBackup2.newBuilder();
    builder.setGeneratingGuardianId(org.generatingGuardianId());
    builder.setDesignatedGuardianId(org.designatedGuardianId());
    builder.setDesignatedGuardianXCoordinate(org.designatedGuardianXCoordinate());
    if (org.coordinate() != null) {
      builder.setCoordinate(CommonConvert.publishElementModQ(org.coordinate()));
    }
    builder.setError(org.error());
    return builder.build();
  }

  private static TrusteeProto.ElGamalKeyPair publishElgamalKeypair(ElGamal.KeyPair keypair) {
    TrusteeProto.ElGamalKeyPair.Builder builder = TrusteeProto.ElGamalKeyPair.newBuilder();
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
