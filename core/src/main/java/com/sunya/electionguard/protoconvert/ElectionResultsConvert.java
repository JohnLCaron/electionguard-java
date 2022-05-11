package com.sunya.electionguard.protoconvert;

import com.sunya.electionguard.AvailableGuardian;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.PlaintextTally;
import electionguard.ballot.DecryptionResult;
import electionguard.ballot.ElectionInitialized;
import electionguard.ballot.TallyResult;
import electionguard.protogen.ElectionRecordProto;

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

  public static TallyResult importTallyResult(ElectionRecordProto.TallyResult proto) {
    ElectionInitialized init = importElectionInitialized(proto.getElectionInit());
    CiphertextTally encrypted = importCiphertextTally(proto.getCiphertextTally());

    return new TallyResult(
            init,
            encrypted,
            proto.getBallotIdsList(),
            proto.getTallyIdsList());
  }

  public static DecryptionResult importDecryptionResult(ElectionRecordProto.DecryptionResult proto) {
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

  static AvailableGuardian importAvailableGuardian(ElectionRecordProto.AvailableGuardian proto) {
    return new AvailableGuardian(
            proto.getGuardianId(),
            proto.getXCoordinate(),
            CommonConvert.importElementModQ(proto.getLagrangeCoefficient())
    );
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////

  public static ElectionRecordProto.TallyResult publishTallyResult(TallyResult result) {
    ElectionRecordProto.TallyResult.Builder builder = ElectionRecordProto.TallyResult.newBuilder();
    builder.setElectionInit(publishElectionInitialized(result.getElectionIntialized()));
    builder.setCiphertextTally(publishCiphertextTally(result.getCiphertextTally()));
    builder.addAllBallotIds(result.getBallotIds());
    builder.addAllTallyIds(result.getTallyIds());

    return builder.build();
  }

  public static ElectionRecordProto.DecryptionResult publishDecryptionResult(DecryptionResult result) {
    ElectionRecordProto.DecryptionResult.Builder builder = ElectionRecordProto.DecryptionResult.newBuilder();
    builder.setTallyResult(publishTallyResult(result.getTallyResult()));
    builder.setDecryptedTally(publishPlaintextTally(result.getDecryptedTally()));
    builder.addAllDecryptingGuardians(
            result.getAvailableGuardians().stream().map(it -> publishAvailableGuardian(it)).toList());
    builder.putAllMetadata(result.getMetadata());
    return builder.build();
  }

  static ElectionRecordProto.AvailableGuardian publishAvailableGuardian(AvailableGuardian guardian) {
    ElectionRecordProto.AvailableGuardian.Builder builder = ElectionRecordProto.AvailableGuardian.newBuilder();
    builder.setGuardianId(guardian.guardianId());
    builder.setXCoordinate(guardian.xCoordinate());
    builder.setLagrangeCoefficient(publishElementModQ(guardian.lagrangeCoefficient()));
    return builder.build();
  }

}
