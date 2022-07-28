package com.sunya.electionguard.json;

import com.sunya.electionguard.core.UInt256;
import com.sunya.electionguard.protoconvert.ElectionResultsConvert;
import com.sunya.electionguard.publish.ElectionRecord;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.Publisher;
import com.sunya.electionguard.verifier.TestParameterVerifier;
import electionguard.ballot.DecryptionResult;
import electionguard.ballot.ElectionConfig;
import electionguard.ballot.ElectionConstants;
import electionguard.ballot.ElectionInitialized;
import electionguard.ballot.Guardian;
import electionguard.ballot.TallyResult;
import electionguard.protogen.ElectionRecordProto;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

// Test Json reading from python library sample_election_record.
// Then using that to roundtrip to proto and back.

public class TestPythonSampleElectionRecord {
  private static JsonConsumer consumer;

  @BeforeContainer
  public static void setUp() throws IOException {
    consumer = new JsonConsumer(TestParameterVerifier.topdirJsonExample);
  }

  @Example
  public void testJsonConsumer() throws IOException {
    assertThat(consumer.manifest()).isNotNull();
    assertThat(consumer.context()).isNotNull();
    assertThat(consumer.constants()).isNotNull();
    assertThat(consumer.ciphertextTally()).isNotNull();
    assertThat(consumer.decryptedTally()).isNotNull();
    assertThat(consumer.devices()).hasSize(1);
    assertThat(consumer.guardianRecords()).hasSize(2);
    assertThat(consumer.availableGuardians()).hasSize(2);

    assertThat(consumer.acceptedBallots()).isNotNull();
    assertThat(consumer.acceptedBallots()).isNotEmpty();
  }

  // DecryptionResult

  @Example
  public void testDecryptionResultJsonRoundtrip() throws IOException {
    DecryptionResult dresult = makeDecryptionResult();
    ElectionRecordProto.DecryptionResult proto = ElectionResultsConvert.publishDecryptionResult(dresult);
    DecryptionResult roundtrip = ElectionResultsConvert.importDecryptionResult(proto);
    compareDecryptionResult(roundtrip, dresult);

    assertThat(roundtrip.getDecryptedTally()).isEqualTo(consumer.decryptedTally());
    assertThat(roundtrip.getDecryptingGuardians()).isEqualTo(consumer.availableGuardians());
  }

  void compareDecryptionResult(DecryptionResult roundtrip, DecryptionResult expected) throws IOException {
    compareConfig(roundtrip.getTallyResult().getElectionIntialized().getConfig(), expected.getTallyResult().getElectionIntialized().getConfig());
    assertThat(roundtrip.getTallyResult().getElectionIntialized()).isEqualTo(expected.getTallyResult().getElectionIntialized());
    assertThat(roundtrip.getTallyResult()).isEqualTo(expected.getTallyResult());
    assertThat(roundtrip).isEqualTo(expected);

    assertThat(roundtrip.getDecryptedTally()).isEqualTo(expected.getDecryptedTally());
    assertThat(roundtrip.getDecryptingGuardians()).isEqualTo(expected.getDecryptingGuardians());
  }

  void compareConfig(ElectionConfig config, ElectionConfig expected) {
    assertThat(config.getProtoVersion()).isEqualTo(expected.getProtoVersion());
    compareConstants(config.getConstants(), expected.getConstants());
    assertThat(config.getMetadata()).isEqualTo(expected.getMetadata());
    assertThat(config.getManifest()).isEqualTo(expected.getManifest());
    assertThat(config.getQuorum()).isEqualTo(expected.getQuorum());
    assertThat(config.getNumberOfGuardians()).isEqualTo(expected.getNumberOfGuardians());
    assertThat(config).isEqualTo(expected);
  }

  void compareConstants(ElectionConstants constants, ElectionConstants expected) {
    assertThat(constants.getName()).isEqualTo(expected.getName());
    assertThat(constants.getLargePrime()).isEqualTo(expected.getLargePrime());
    assertThat(constants.getSmallPrime()).isEqualTo(expected.getSmallPrime());
    assertThat(constants.getCofactor()).isEqualTo(expected.getCofactor());
    assertThat(constants.getGenerator()).isEqualTo(expected.getGenerator());
    int h1 = constants.hashCode();
    int h2 = expected.hashCode();
    assertThat(h1 == h2).isTrue();

    assertThat(constants.equals(expected)).isTrue();
    assertThat(constants).isEqualTo(expected);
  }

  @Example
  public void testPublishDecryptionResultJsonRoundtrip() throws IOException {
    DecryptionResult dresult = makeDecryptionResult();

    Path tmp = Files.createTempDirectory("publish");
    tmp.toFile().deleteOnExit();
    String protoDir = tmp.toAbsolutePath().toString();
    Publisher publisher = new Publisher(protoDir, Publisher.Mode.createNew);
    publisher.writeDecryptionResults(dresult);

    Consumer consumer = new Consumer(protoDir);
    DecryptionResult roundtrip = consumer.readDecryptionResult();

    compareDecryptionResult(roundtrip, dresult);
  }


  private DecryptionResult makeDecryptionResult() throws IOException {
    ElectionConfig config = new ElectionConfig(
            consumer.manifest(),
            consumer.context().numberOfGuardians,
            consumer.context().quorum
    );

    ElectionInitialized init = new ElectionInitialized(
            config,
            consumer.context().jointPublicKey,
            UInt256.fromModQ(consumer.context().manifestHash),
            UInt256.fromModQ(consumer.context().cryptoBaseHash),
            UInt256.fromModQ(consumer.context().cryptoExtendedBaseHash),
            consumer.guardianRecords().stream().map(g -> new Guardian(g)).toList(),
            emptyMap()
    );

    TallyResult tresult = new TallyResult(
            init,
            consumer.ciphertextTally(),
            emptyList(), emptyList());

    DecryptionResult dresult = new DecryptionResult(tresult,
            consumer.decryptedTally(),
            consumer.availableGuardians(),
            emptyMap());

    return dresult;
  }
}
