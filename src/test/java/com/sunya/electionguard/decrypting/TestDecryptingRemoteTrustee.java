package com.sunya.electionguard.decrypting;

import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.DecryptionProofRecovery;
import com.sunya.electionguard.DecryptionProofTuple;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.guardian.DecryptingTrustee;
import com.sunya.electionguard.proto.TrusteeFromProto;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.verifier.ElectionRecord;
import net.jqwik.api.Example;

import java.io.IOException;
import java.util.Optional;

import static com.google.common.truth.Truth8.assertThat;

public class TestDecryptingRemoteTrustee {
  String trusteeProto = "/home/snake/tmp/electionguard/remoteWorkflow/keyCeremony/remoteTrustee-1.protobuf";
  String electionRecordDir = "/home/snake/tmp/electionguard/remoteWorkflow/accumTally/";
  int port = 17771;
  DecryptingTrustee trustee;
  DecryptingRemoteTrusteeProxy proxy;
  CiphertextTally tally;
  Group.ElementModQ extendedHash;

  TestDecryptingRemoteTrustee() throws IOException {
    this.trustee = TrusteeFromProto.readTrustee(trusteeProto);

    String url = "localhost:"+port;
    DecryptingRemoteTrusteeProxy.Builder builder = DecryptingRemoteTrusteeProxy.builder();
    builder.setTrusteeId(trustee.id);
    builder.setUrl(url);
    builder.setXCoordinate(trustee.xCoordinate);
    builder.setElectionPublicKey(trustee.publicKey());
    this.proxy = builder.build();

    Consumer consumer = new Consumer(electionRecordDir);
    ElectionRecord record = consumer.readElectionRecord();
    this.tally = record.encryptedTally;
    this.extendedHash =  record.extendedHash();
  }

  @Example
  public void testPing() {
    System.out.printf("ping = %s%n", proxy.ping());
  }

  @Example
  public void testCompensatedDecrypt() {
    for (CiphertextTally.Contest contest : tally.contests.values()) {
      for (CiphertextTally.Selection selection : contest.selections.values()) {
        Optional<DecryptionProofRecovery> result = proxy.compensatedDecrypt(
                "remoteTrustee-3",
                selection.ciphertext(),
                this.extendedHash,
                null);

        System.out.printf("compensatedDecrypt = %s%n", result.isPresent());
        // assertThat(result).isPresent();
      }
    }
  }

  @Example
  public void testPartialDecrypt() {
    try {
      Thread.sleep(500);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    Optional<DecryptionProofTuple> result = proxy.partialDecrypt(
            new ElGamal.Ciphertext(Group.TWO_MOD_P, Group.TWO_MOD_P),
            Group.rand_q(),
            null);

    System.out.printf("partialDecrypt = %s%n", result.isPresent());
    assertThat(result).isEmpty();
  }
}