package com.sunya.electionguard.protoconvert;

import com.sunya.electionguard.AvailableGuardian;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.PlaintextTally;
import electionguard.ballot.DecryptionResult;
import electionguard.ballot.ElectionInitialized;
import electionguard.ballot.TallyResult;
import electionguard.protogen.ElectionRecordProto2;

import javax.annotation.concurrent.Immutable;

import java.util.List;

import static com.sunya.electionguard.protoconvert.CiphertextTallyFromProto.importCiphertextTally;
import static com.sunya.electionguard.protoconvert.CiphertextTallyToProto.publishCiphertextTally;
import static com.sunya.electionguard.protoconvert.CommonConvert.publishElementModQ;
import static com.sunya.electionguard.protoconvert.ElectionInitializedConvert.importElectionInitialized;
import static com.sunya.electionguard.protoconvert.ElectionInitializedConvert.publishElectionInitialized;
import static com.sunya.electionguard.protoconvert.PlaintextTallyFromProto.importPlaintextTally;
import static com.sunya.electionguard.protoconvert.PlaintextTallyToProto.publishPlaintextTally;

@Immutable
public class ElectionResultsConvert {

  public static TallyResult importTallyResult(ElectionRecordProto2.TallyResult proto) {
    ElectionInitialized init = importElectionInitialized(proto.getElectionInit());
    CiphertextTally encrypted = importCiphertextTally(proto.getCiphertextTally());

    return new TallyResult(
            init,
            encrypted,
            proto.getBallotIdsList(),
            proto.getTallyIdsList());
  }

  public static DecryptionResult importDecryptionResult(ElectionRecordProto2.DecryptionResult proto) {
    TallyResult tallyResult = importTallyResult(proto.getTallyResult());
    PlaintextTally decryptedTally = importPlaintextTally(proto.getDecryptedTally());

    List<AvailableGuardian> guardians =
            proto.getDecryptingGuardiansList().stream()
                    .map(ElectionResultsConvert::importAvailableGuardian)
                    .toList();

    return new DecryptionResult(
            tallyResult,
            decryptedTally,
            guardians,
            proto.getMetadataMap());
  }

  static AvailableGuardian importAvailableGuardian(ElectionRecordProto2.AvailableGuardian proto) {
    return new AvailableGuardian(
            proto.getGuardianId(),
            proto.getXCoordinate(),
            CommonConvert.importElementModQ(proto.getLagrangeCoefficient())
    );
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////

  public static ElectionRecordProto2.TallyResult publishTallyResult(TallyResult result) {
    ElectionRecordProto2.TallyResult.Builder builder = ElectionRecordProto2.TallyResult.newBuilder();
    builder.setElectionInit(publishElectionInitialized(result.getElectionIntialized()));
    builder.setCiphertextTally(publishCiphertextTally(result.getCiphertextTally()));
    builder.addAllBallotIds(result.getBallotIds());
    builder.addAllTallyIds(result.getTallyIds());

    return builder.build();
  }

  public static ElectionRecordProto2.DecryptionResult publishDecryptionResult(DecryptionResult result) {
    ElectionRecordProto2.DecryptionResult.Builder builder = ElectionRecordProto2.DecryptionResult.newBuilder();
    builder.setTallyResult(publishTallyResult(result.getTallyResult()));
    builder.setDecryptedTally(publishPlaintextTally(result.getDecryptedTally()));
    builder.addAllDecryptingGuardians(
            result.getAvailableGuardians().stream().map(it -> publishAvailableGuardian(it)).toList());
    builder.putAllMetadata(result.getMetadata());
    return builder.build();
  }

  static ElectionRecordProto2.AvailableGuardian publishAvailableGuardian(AvailableGuardian guardian) {
    ElectionRecordProto2.AvailableGuardian.Builder builder = ElectionRecordProto2.AvailableGuardian.newBuilder();
    builder.setGuardianId(guardian.guardianId());
    builder.setXCoordinate(guardian.xCoordinate());
    builder.setLagrangeCoefficient(publishElementModQ(guardian.lagrangeCoefficient()));
    return builder.build();
  }

}
