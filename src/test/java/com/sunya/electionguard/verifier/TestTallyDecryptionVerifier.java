package com.sunya.electionguard.verifier;

import com.sunya.electionguard.ElectionWithPlaceholders;
import com.sunya.electionguard.publish.Consumer;
import net.jqwik.api.Example;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestTallyDecryptionVerifier {

  @Example
  public void testVerifyTallyDecryptionProto() throws IOException {
    String topdir = TestParameterVerifier.topdirProto;
    Consumer consumer = new Consumer(topdir);
    ElectionRecord electionRecord = consumer.readElectionRecordProto();

    TallyDecryptionVerifier tdv = new TallyDecryptionVerifier(electionRecord.election, electionRecord.decryptedTally);
    boolean tdvOk = tdv.verify_tally_decryption();
    assertThat(tdvOk).isTrue();
  }

  @Example
  public void testVerifyTallyDecryptionJson() throws IOException {
    String topdir = TestParameterVerifier.topdirJson;
    Consumer consumer = new Consumer(topdir);
    ElectionRecord electionRecord = consumer.readElectionRecordJson();

    TallyDecryptionVerifier tdv = new TallyDecryptionVerifier(electionRecord.election, electionRecord.decryptedTally);
    boolean tdvOk = tdv.verify_tally_decryption();
    assertThat(tdvOk).isTrue();
  }
}
