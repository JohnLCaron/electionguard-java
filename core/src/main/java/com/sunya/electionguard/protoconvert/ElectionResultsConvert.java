package com.sunya.electionguard.protoconvert;

import com.sunya.electionguard.ballot.DecryptingGuardian;
import com.sunya.electionguard.ballot.EncryptedTally;
import com.sunya.electionguard.PlaintextTally;
import electionguard.ballot.DecryptionResult;
import electionguard.ballot.ElectionInitialized;
import electionguard.ballot.TallyResult;
import electionguard.protogen.ElectionRecordProto;

import javax.annotation.concurrent.Immutable;

import java.util.List;

import static com.sunya.electionguard.protoconvert.EncryptedTallyConvert.importEncryptedTally;
import static com.sunya.electionguard.protoconvert.EncryptedTallyConvert.publishEncryptedTally;
import static com.sunya.electionguard.protoconvert.CommonConvert.publishElementModQ;
import static com.sunya.electionguard.protoconvert.ElectionInitializedConvert.importElectionInitialized;
import static com.sunya.electionguard.protoconvert.ElectionInitializedConvert.publishElectionInitialized;
import static com.sunya.electionguard.protoconvert.PlaintextTallyConvert.importPlaintextTally;
import static com.sunya.electionguard.protoconvert.PlaintextTallyConvert.publishPlaintextTally;

@Immutable
public class ElectionResultsConvert {

  public static TallyResult importTallyResult(ElectionRecordProto.TallyResult proto) {
    ElectionInitialized init = importElectionInitialized(proto.getElectionInit());
    EncryptedTally encrypted = importEncryptedTally(proto.getEncryptedTally());

    return new TallyResult(
            init,
            encrypted,
            proto.getBallotIdsList(),
            proto.getTallyIdsList());
  }

  public static DecryptionResult importDecryptionResult(ElectionRecordProto.DecryptionResult proto) {
    TallyResult tallyResult = importTallyResult(proto.getTallyResult());
    PlaintextTally decryptedTally = importPlaintextTally(proto.getDecryptedTally());

    List<DecryptingGuardian> guardians =
            proto.getDecryptingGuardiansList().stream()
                    .map(ElectionResultsConvert::importAvailableGuardian)
                    .toList();

    return new DecryptionResult(
            tallyResult,
            decryptedTally,
            guardians,
            proto.getMetadataMap());
  }

  static DecryptingGuardian importAvailableGuardian(ElectionRecordProto.DecryptingGuardian proto) {
    return new DecryptingGuardian(
            proto.getGuardianId(),
            proto.getXCoordinate(),
            CommonConvert.importElementModQ(proto.getLagrangeCoefficient())
    );
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////

  public static ElectionRecordProto.TallyResult publishTallyResult(TallyResult result) {
    ElectionRecordProto.TallyResult.Builder builder = ElectionRecordProto.TallyResult.newBuilder();
    builder.setElectionInit(publishElectionInitialized(result.getElectionIntialized()));
    builder.setEncryptedTally(publishEncryptedTally(result.getEncryptedTally()));
    builder.addAllBallotIds(result.getBallotIds());
    builder.addAllTallyIds(result.getTallyIds());

    return builder.build();
  }

  public static ElectionRecordProto.DecryptionResult publishDecryptionResult(DecryptionResult result) {
    ElectionRecordProto.DecryptionResult.Builder builder = ElectionRecordProto.DecryptionResult.newBuilder();
    builder.setTallyResult(publishTallyResult(result.getTallyResult()));
    builder.setDecryptedTally(publishPlaintextTally(result.getDecryptedTally()));
    builder.addAllDecryptingGuardians(
            result.getDecryptingGuardians().stream().map(it -> publishAvailableGuardian(it)).toList());
    builder.putAllMetadata(result.getMetadata());
    return builder.build();
  }

  static ElectionRecordProto.DecryptingGuardian publishAvailableGuardian(DecryptingGuardian guardian) {
    ElectionRecordProto.DecryptingGuardian.Builder builder = ElectionRecordProto.DecryptingGuardian.newBuilder();
    builder.setGuardianId(guardian.guardianId());
    builder.setXCoordinate(guardian.xCoordinate());
    builder.setLagrangeCoefficient(publishElementModQ(guardian.lagrangeCoefficient()));
    return builder.build();
  }

}
