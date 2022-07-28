package com.sunya.electionguard.decrypting;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.BallotBox;
import com.sunya.electionguard.ballot.EncryptedTally;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.protoconvert.TrusteeFromProto;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.ElectionRecord;

import java.io.IOException;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

/** Test DecryptingRemoteTrusteeProxy. Needs DecryptingRemote to be running on 17771. */
public class TestDecryptingRemoteTrusteeProxy {
  String trusteeProto = "/home/snake/tmp/electionguard/remoteWorkflow/keyCeremony/remoteTrustee-1.protobuf";
  String electionRecordDir = "/home/snake/tmp/electionguard/remoteWorkflow/accumTally/";
  int port = 17771;
  DecryptingTrustee trustee;
  DecryptingRemoteTrusteeProxy proxy;
  EncryptedTally tally;
  Group.ElementModQ extendedHash;

  TestDecryptingRemoteTrusteeProxy() throws IOException {
    this.trustee = TrusteeFromProto.readTrustee(trusteeProto);

    String url = "localhost:"+port;
    DecryptingRemoteTrusteeProxy.Builder builder = DecryptingRemoteTrusteeProxy.builder();
    builder.setTrusteeId(trustee.id());
    builder.setUrl(url);
    builder.setXCoordinate(trustee.xCoordinate());
    builder.setElectionPublicKey(trustee.electionPublicKey());
    this.proxy = builder.build();

    Consumer consumer = new Consumer(electionRecordDir);
    ElectionRecord record = consumer.readElectionRecord();
    this.tally = record.ciphertextTally();
    this.extendedHash =  record.extendedHash();
  }

  // @Example
  public void testCompensatedDecrypt() {
    for (EncryptedTally.Contest contest : tally.contests.values()) {
      for (EncryptedTally.Selection selection : contest.selections.values()) {
        List<DecryptionProofRecovery> result = proxy.compensatedDecrypt(
                "remoteTrustee-3",
                ImmutableList.of(selection.ciphertext()),
                this.extendedHash,
                null);

        System.out.printf("compensatedDecrypt = %s%n", result.isEmpty());
        assertThat(result).isNotEmpty();
      }
    }
  }

  // @Example
  public void testPartialDecrypt() {
    try {
      Thread.sleep(500);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    List<BallotBox.DecryptionProofTuple> result = proxy.partialDecrypt(
            ImmutableList.of(new ElGamal.Ciphertext(Group.TWO_MOD_P, Group.TWO_MOD_P)),
            Group.rand_q(),
            null);

    System.out.printf("partialDecrypt = %s%n", result.isEmpty());
    assertThat(result).isNotEmpty();
  }
}
