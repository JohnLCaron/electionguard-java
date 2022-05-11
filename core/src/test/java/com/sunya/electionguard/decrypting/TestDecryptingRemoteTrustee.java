package com.sunya.electionguard.decrypting;

import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.protoconvert.CommonConvert;
import electionguard.protogen.CommonProto;
import electionguard.protogen.CommonRpcProto;
import electionguard.protogen.DecryptingTrusteeProto;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.ElectionRecord;

import io.grpc.stub.StreamObserver;
import net.jqwik.api.Example;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;

/** Test DecryptingRemoteTrustee. Needs one to be running on 17771. */
public class TestDecryptingRemoteTrustee {

  @Mock
  StreamObserver<DecryptingTrusteeProto.CompensatedDecryptionResponse> observeCompensatedDecryption;
  @Captor
  ArgumentCaptor<DecryptingTrusteeProto.CompensatedDecryptionResponse> captureCompensatedDecryption;

  @Mock
  StreamObserver<DecryptingTrusteeProto.PartialDecryptionResponse> observePartialDecryption;
  @Captor
  ArgumentCaptor<DecryptingTrusteeProto.PartialDecryptionResponse> capturePartialDecryption;

  @Mock
  StreamObserver<CommonRpcProto.ErrorResponse> observeBooleanResponse;
  @Captor
  ArgumentCaptor<CommonRpcProto.ErrorResponse> captureBooleanResponse;

  CiphertextTally tally;
  Group.ElementModQ extendedHash;

  public TestDecryptingRemoteTrustee() throws IOException {
    MockitoAnnotations.openMocks(this);

    Consumer consumer = new Consumer(TestDecryptingMediator.DECRYPTING_DATA_DIR);
    ElectionRecord electionRecord = consumer.readElectionRecord();
    this.tally = electionRecord.ciphertextTally();
    this.extendedHash =  electionRecord.extendedHash();
  }

  DecryptingRemoteTrustee makeDecryptingRemoteTrustee() throws IOException {
    return new DecryptingRemoteTrustee(TestDecryptingMediator.TRUSTEE_DATA_DIR +
            "/decryptingTrustee-remoteTrustee1.protobuf");
  }

  @Example
  public void testPartialDecrypt() throws IOException {
    DecryptingRemoteTrustee remote1 = makeDecryptingRemoteTrustee();

    List<CommonProto.ElGamalCiphertext> texts = this.tally.contests.values().stream()
            .flatMap(c -> c.selections.values().stream())
            .map(s -> CommonConvert.publishCiphertext(s.ciphertext()))
            .toList();

    DecryptingTrusteeProto.PartialDecryptionRequest request = DecryptingTrusteeProto.PartialDecryptionRequest.newBuilder()
            .addAllText(texts)
            .setExtendedBaseHash(CommonConvert.publishElementModQ(this.extendedHash))
            .build();

    remote1.partialDecrypt(request, observePartialDecryption);

    verify(observePartialDecryption).onCompleted();
    verify(observePartialDecryption).onNext(capturePartialDecryption.capture());
    DecryptingTrusteeProto.PartialDecryptionResponse response= capturePartialDecryption.getValue();

    assertThat(response).isNotNull();
    assertThat(response.getError()).isEmpty();
    assertThat(response.getResultsCount()).isEqualTo(texts.size());
  }

  @Example
  public void testPartialDecryptFakeText() throws IOException {
    DecryptingRemoteTrustee remote1 = makeDecryptingRemoteTrustee();

    ElGamal.Ciphertext fakeText = new ElGamal.Ciphertext(Group.TWO_MOD_P, Group.TWO_MOD_P);
    DecryptingTrusteeProto.PartialDecryptionRequest request = DecryptingTrusteeProto.PartialDecryptionRequest.newBuilder()
            .addText(CommonConvert.publishCiphertext(fakeText))
            .setExtendedBaseHash(CommonConvert.publishElementModQ(this.extendedHash))
            .build();

    remote1.partialDecrypt(request, observePartialDecryption);

    verify(observePartialDecryption).onCompleted();
    verify(observePartialDecryption).onNext(capturePartialDecryption.capture());
    DecryptingTrusteeProto.PartialDecryptionResponse response= capturePartialDecryption.getValue();

    assertThat(response).isNotNull();
    assertThat(response.getError()).contains("PartialDecrypt invalid proof for remoteTrustee1");
  }

  @Example
  public void testCompensatedDecrypt() throws IOException {
    DecryptingRemoteTrustee remote1 = makeDecryptingRemoteTrustee();

    List<CommonProto.ElGamalCiphertext> texts = this.tally.contests.values().stream()
            .flatMap(c -> c.selections.values().stream())
            .map(s -> CommonConvert.publishCiphertext(s.ciphertext()))
            .toList();

    DecryptingTrusteeProto.CompensatedDecryptionRequest request = DecryptingTrusteeProto.CompensatedDecryptionRequest.newBuilder()
            .setMissingGuardianId("remoteTrustee2")
            .addAllText(texts)
            .setExtendedBaseHash(CommonConvert.publishElementModQ(this.extendedHash))
            .build();

    remote1.compensatedDecrypt(request, observeCompensatedDecryption);

    verify(observeCompensatedDecryption).onCompleted();
    verify(observeCompensatedDecryption).onNext(captureCompensatedDecryption.capture());
    DecryptingTrusteeProto.CompensatedDecryptionResponse response= captureCompensatedDecryption.getValue();

    assertThat(response).isNotNull();
    assertThat(response.getError()).isEmpty();
    assertThat(response.getResultsCount()).isEqualTo(texts.size());
  }

  @Example
  public void testCompensatedDecryptFakeText() throws IOException {
    DecryptingRemoteTrustee remote1 = makeDecryptingRemoteTrustee();

    ElGamal.Ciphertext fakeText = new ElGamal.Ciphertext(Group.TWO_MOD_P, Group.TWO_MOD_P);
    DecryptingTrusteeProto.CompensatedDecryptionRequest request = DecryptingTrusteeProto.CompensatedDecryptionRequest.newBuilder()
            .setMissingGuardianId("remoteTrustee2")
            .addText(CommonConvert.publishCiphertext(fakeText))
            .setExtendedBaseHash(CommonConvert.publishElementModQ(this.extendedHash))
            .build();

    remote1.compensatedDecrypt(request, observeCompensatedDecryption);

    verify(observeCompensatedDecryption).onCompleted();
    verify(observeCompensatedDecryption).onNext(captureCompensatedDecryption.capture());
    DecryptingTrusteeProto.CompensatedDecryptionResponse response= captureCompensatedDecryption.getValue();

    assertThat(response).isNotNull();
    assertThat(response.getError()).contains("CompensatedDecrypt invalid proof for remoteTrustee1 missing = remoteTrustee2");
  }

  @Example
  public void testCompensatedDecryptUnknownGuardian() throws IOException {
    DecryptingRemoteTrustee remote1 = makeDecryptingRemoteTrustee();

    ElGamal.Ciphertext fakeText = new ElGamal.Ciphertext(Group.TWO_MOD_P, Group.TWO_MOD_P);
    DecryptingTrusteeProto.CompensatedDecryptionRequest request = DecryptingTrusteeProto.CompensatedDecryptionRequest.newBuilder()
            .setMissingGuardianId("who?")
            .addText(CommonConvert.publishCiphertext(fakeText))
            .setExtendedBaseHash(CommonConvert.publishElementModQ(this.extendedHash))
            .build();

    remote1.compensatedDecrypt(request, observeCompensatedDecryption);

    verify(observeCompensatedDecryption).onCompleted();
    verify(observeCompensatedDecryption).onNext(captureCompensatedDecryption.capture());
    DecryptingTrusteeProto.CompensatedDecryptionResponse response= captureCompensatedDecryption.getValue();

    assertThat(response).isNotNull();
    assertThat(response.getError()).contains("compensate_decrypt guardian remoteTrustee1 missing backup for who?");
  }

  @Example
  public void testFinish() throws IOException {
    DecryptingRemoteTrustee remote1 = makeDecryptingRemoteTrustee();
    remote1.finish(CommonRpcProto.FinishRequest.getDefaultInstance(), observeBooleanResponse);

    verify(observeBooleanResponse).onCompleted();
    verify(observeBooleanResponse).onNext(captureBooleanResponse.capture());
    CommonRpcProto.ErrorResponse response = captureBooleanResponse.getValue();

    assertThat(response).isNotNull();
    assertThat(response.getError()).isEmpty();
  }
}
