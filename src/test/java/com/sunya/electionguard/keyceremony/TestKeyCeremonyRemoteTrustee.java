package com.sunya.electionguard.keyceremony;

import com.sunya.electionguard.Group;
import com.sunya.electionguard.TestUtils;
import com.sunya.electionguard.proto.CommonConvert;
import com.sunya.electionguard.protogen.CommonProto;
import com.sunya.electionguard.protogen.RemoteKeyCeremonyTrusteeProto;
import io.grpc.stub.StreamObserver;
import net.jqwik.api.Example;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.*;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

public class TestKeyCeremonyRemoteTrustee {
  private static final Random random = new Random();
  private static final int QUORUM = 3;

  @Mock
  StreamObserver<RemoteKeyCeremonyTrusteeProto.PublicKeySet> observePublicKeySet;
  @Captor
  ArgumentCaptor<RemoteKeyCeremonyTrusteeProto.PublicKeySet> capturePublicKeySet;

  @Mock
  StreamObserver<CommonProto.ErrorResponse> observeBooleanResponse;
  @Captor
  ArgumentCaptor<CommonProto.ErrorResponse> captureBooleanResponse;

  @Mock
  StreamObserver<RemoteKeyCeremonyTrusteeProto.PartialKeyBackup> observePartialKeyBackup;
  @Captor
  ArgumentCaptor<RemoteKeyCeremonyTrusteeProto.PartialKeyBackup> capturePartialKeyBackup;

  @Mock
  StreamObserver<RemoteKeyCeremonyTrusteeProto.PartialKeyVerification> observePartialKeyVerification;
  @Captor
  ArgumentCaptor<RemoteKeyCeremonyTrusteeProto.PartialKeyVerification> capturePartialKeyVerification;

  @Mock
  StreamObserver<RemoteKeyCeremonyTrusteeProto.PartialKeyChallengeResponse> observePartialKeyChallenge;
  @Captor
  ArgumentCaptor<RemoteKeyCeremonyTrusteeProto.PartialKeyChallengeResponse> capturePartialKeyChallenge;

  @Mock
  StreamObserver<RemoteKeyCeremonyTrusteeProto.JointPublicKeyResponse> observeJointPublicKeyResponse;
  @Captor
  ArgumentCaptor<RemoteKeyCeremonyTrusteeProto.JointPublicKeyResponse> captureJointPublicKeyResponse;

  public TestKeyCeremonyRemoteTrustee() {
    MockitoAnnotations.openMocks(this);
  }

  private KeyCeremonyRemoteTrustee makeRemote(int xCoordinate) throws IOException {
    return new KeyCeremonyRemoteTrustee(makeId(xCoordinate), xCoordinate, QUORUM,
            "/home/snake/tmp/electionguard/testKeyCeremonyRemoteTrustee");
  }

  private String makeId(int xCoordinate) {
    return "KeyCeremonyRemoteTrustee" + xCoordinate;
  }

  @Example
  public void makeRemoteNoDirectory() {
    int xCoordinate = 1;
    assertThrows(IllegalStateException.class, () ->
      new KeyCeremonyRemoteTrustee("KeyCeremonyRemoteTrustee" + xCoordinate, xCoordinate, QUORUM,
              "/home/snake/tmp/electionguard/testKeyCeremonyRemoteTrusteeUnownDirectory"));
  }

  @Example
  public void testConstructor() throws IOException {
    KeyCeremonyRemoteTrustee remote = makeRemote(1);
    assertThat(remote.delegate.xCoordinate).isEqualTo(1);
    assertThat(remote.outputDir).isNotNull();
  }

  @Example
  public void testSendAndReceivePublicKeys() throws IOException {
    KeyCeremonyRemoteTrustee remote = makeRemote(1);

    // send
    RemoteKeyCeremonyTrusteeProto.PublicKeySetRequest request =
            RemoteKeyCeremonyTrusteeProto.PublicKeySetRequest.getDefaultInstance();

    remote.sendPublicKeys(request, observePublicKeySet);

    verify(observePublicKeySet).onCompleted();
    verify(observePublicKeySet).onNext(capturePublicKeySet.capture());
    RemoteKeyCeremonyTrusteeProto.PublicKeySet responseSend = capturePublicKeySet.getValue();

    assertThat(responseSend).isNotNull();
    assertThat(responseSend.getError()).isEmpty();
    assertThat(responseSend.getOwnerId()).isEqualTo(makeId(1));
    assertThat(responseSend.getGuardianXCoordinate()).isEqualTo(1);

    // Now make a valid public key
    RemoteKeyCeremonyTrusteeProto.PublicKeySet validPublicKey = RemoteKeyCeremonyTrusteeProto.PublicKeySet.newBuilder()
            .setOwnerId("whatEVER")
            .setGuardianXCoordinate(2)
            .setAuxiliaryPublicKey(responseSend.getAuxiliaryPublicKey())
            .addAllCoefficientProofs(responseSend.getCoefficientProofsList())
            .build();

    remote.receivePublicKeys(validPublicKey, observeBooleanResponse);

    verify(observeBooleanResponse).onCompleted();
    verify(observeBooleanResponse).onNext(captureBooleanResponse.capture());
    CommonProto.ErrorResponse response2 = captureBooleanResponse.getValue();

    assertThat(response2).isNotNull();
    assertThat(response2.getError()).isEmpty();
  }

  @Example
  public void testReceiveSamePublicKeys() throws IOException {
    KeyCeremonyRemoteTrustee remote = makeRemote(1);
    RemoteKeyCeremonyTrusteeProto.PublicKeySetRequest request =
            RemoteKeyCeremonyTrusteeProto.PublicKeySetRequest.getDefaultInstance();
    remote.sendPublicKeys(request, observePublicKeySet);
    verify(observePublicKeySet).onCompleted();
    verify(observePublicKeySet).onNext(capturePublicKeySet.capture());
    RemoteKeyCeremonyTrusteeProto.PublicKeySet responseSend = capturePublicKeySet.getValue();

    // send same keys back - should fail
    remote.receivePublicKeys(responseSend, observeBooleanResponse);

    verify(observeBooleanResponse).onCompleted();
    verify(observeBooleanResponse).onNext(captureBooleanResponse.capture());
    CommonProto.ErrorResponse response = captureBooleanResponse.getValue();

    assertThat(response).isNotNull();
    assertThat(response.getError()).contains("Guardian Id equals mine");
  }

  @Example
  public void testReceiveInvalidPublicKeys() throws IOException {
    KeyCeremonyRemoteTrustee remote = makeRemote(1);
    RemoteKeyCeremonyTrusteeProto.PublicKeySetRequest request =
            RemoteKeyCeremonyTrusteeProto.PublicKeySetRequest.getDefaultInstance();
    remote.sendPublicKeys(request, observePublicKeySet);
    verify(observePublicKeySet).onCompleted();
    verify(observePublicKeySet).onNext(capturePublicKeySet.capture());
    RemoteKeyCeremonyTrusteeProto.PublicKeySet responseSend = capturePublicKeySet.getValue();

    // Now make an invalid public key
    RemoteKeyCeremonyTrusteeProto.PublicKeySet invalidPublicKey = RemoteKeyCeremonyTrusteeProto.PublicKeySet.newBuilder()
            .setOwnerId("whatEVER")
            .setGuardianXCoordinate(2)
            .setAuxiliaryPublicKey(responseSend.getAuxiliaryPublicKey())
            .addAllCoefficientProofs(munge(responseSend.getCoefficientProofsList()))
            .build();

    remote.receivePublicKeys(invalidPublicKey, observeBooleanResponse);

    verify(observeBooleanResponse).onCompleted();
    verify(observeBooleanResponse).onNext(captureBooleanResponse.capture());
    CommonProto.ErrorResponse response2 = captureBooleanResponse.getValue();

    assertThat(response2).isNotNull();
    assertThat(response2.getError()).contains("Invalid Schnorr proof");
  }

  private List<CommonProto.SchnorrProof> munge(List<CommonProto.SchnorrProof> proofs) {
    // pick a random one
    int index = random.nextInt(proofs.size());
    ArrayList<CommonProto.SchnorrProof> result = new ArrayList<>(proofs);
    CommonProto.SchnorrProof org = proofs.get(index);
    CommonProto.SchnorrProof munged = CommonProto.SchnorrProof.newBuilder()
            .setChallenge(org.getChallenge())
            .setCommitment(org.getCommitment())
            .setPublicKey(org.getPublicKey())
            .setResponse(CommonConvert.convertElementModQ(TestUtils.elements_mod_q()))
            .build();

    result.set(index, munged);
    return result;
  }

  @Example
  public void testSendPartialKeyBackupUnknownGuardian() throws IOException {
    KeyCeremonyRemoteTrustee remote1 = makeRemote(1);

    // now ask for backup of 2 by 1
    RemoteKeyCeremonyTrusteeProto.PartialKeyBackupRequest request =
            RemoteKeyCeremonyTrusteeProto.PartialKeyBackupRequest.newBuilder()
                    .setGuardianId(makeId(2))
                    .build();
    remote1.sendPartialKeyBackup(request, observePartialKeyBackup);

    verify(observePartialKeyBackup).onCompleted();
    verify(observePartialKeyBackup).onNext(capturePartialKeyBackup.capture());
    RemoteKeyCeremonyTrusteeProto.PartialKeyBackup responseSend = capturePartialKeyBackup.getValue();

    assertThat(responseSend).isNotNull();
    assertThat(responseSend.getError()).contains("Trustee 'KeyCeremonyRemoteTrustee1', does not have public key for 'KeyCeremonyRemoteTrustee2'");
    assertThat(responseSend.getGeneratingGuardianId()).isEqualTo(makeId(1));
    assertThat(responseSend.getDesignatedGuardianId()).isEqualTo(makeId(2));
  }

  @Example
  public void testSendAndVerifyPartialKeyBackup() throws IOException {
    KeyCeremonyRemoteTrustee remote1 = makeRemote(1);
    KeyCeremonyRemoteTrustee remote2 = makeRemote(2);

    // get guardian 2 public key, send to guardian 1
    RemoteKeyCeremonyTrusteeProto.PublicKeySetRequest requestPublicKey =
            RemoteKeyCeremonyTrusteeProto.PublicKeySetRequest.getDefaultInstance();
    remote2.sendPublicKeys(requestPublicKey, observePublicKeySet);
    verify(observePublicKeySet).onCompleted();
    verify(observePublicKeySet).onNext(capturePublicKeySet.capture());
    RemoteKeyCeremonyTrusteeProto.PublicKeySet response1 = capturePublicKeySet.getValue();
    remote1.receivePublicKeys(response1, observeBooleanResponse);

    // get guardian 1 public key, send to guardian 2
    remote1.sendPublicKeys(requestPublicKey, observePublicKeySet);
    verify(observePublicKeySet, times( 2)).onCompleted();
    verify(observePublicKeySet, times( 2)).onNext(capturePublicKeySet.capture());
    RemoteKeyCeremonyTrusteeProto.PublicKeySet response2 = capturePublicKeySet.getValue();
    remote2.receivePublicKeys(response2, observeBooleanResponse);

    // now ask for backup of 2 by 1
    RemoteKeyCeremonyTrusteeProto.PartialKeyBackupRequest requestBackup =
            RemoteKeyCeremonyTrusteeProto.PartialKeyBackupRequest.newBuilder()
            .setGuardianId(makeId(2))
            .build();
    remote1.sendPartialKeyBackup(requestBackup, observePartialKeyBackup);

    verify(observePartialKeyBackup).onCompleted();
    verify(observePartialKeyBackup).onNext(capturePartialKeyBackup.capture());
    RemoteKeyCeremonyTrusteeProto.PartialKeyBackup responseBackup = capturePartialKeyBackup.getValue();

    assertThat(responseBackup).isNotNull();
    assertThat(responseBackup.getError()).isEmpty();
    assertThat(responseBackup.getGeneratingGuardianId()).isEqualTo(makeId(1));
    assertThat(responseBackup.getDesignatedGuardianId()).isEqualTo(makeId(2));
    assertThat(responseBackup.getDesignatedGuardianXCoordinate()).isEqualTo(2);

    // now ask for 2 to verify backup
    remote2.verifyPartialKeyBackup(responseBackup, observePartialKeyVerification);

    verify(observePartialKeyVerification).onCompleted();
    verify(observePartialKeyVerification).onNext(capturePartialKeyVerification.capture());
    RemoteKeyCeremonyTrusteeProto.PartialKeyVerification responseVerification= capturePartialKeyVerification.getValue();

    assertThat(responseVerification).isNotNull();
    assertThat(responseVerification.getError()).isEmpty();
    assertThat(responseVerification.getGeneratingGuardianId()).isEqualTo(makeId(1));
    assertThat(responseVerification.getDesignatedGuardianId()).isEqualTo(makeId(2));
    assertThat(responseVerification.getDesignatedGuardianXCoordinate()).isEqualTo(2);
  }

  @Example
  public void testSendPartialKeyVerifyWrongGuardian() throws IOException {
    KeyCeremonyRemoteTrustee remote1 = makeRemote(1);
    RemoteKeyCeremonyTrusteeProto.PartialKeyBackup fakeBackup = RemoteKeyCeremonyTrusteeProto.PartialKeyBackup.newBuilder()
            .setGeneratingGuardianId(makeId(1))
            .setDesignatedGuardianId("who?")
            .build();

    remote1.verifyPartialKeyBackup(fakeBackup, observePartialKeyVerification);

    verify(observePartialKeyVerification).onCompleted();
    verify(observePartialKeyVerification).onNext(capturePartialKeyVerification.capture());
    RemoteKeyCeremonyTrusteeProto.PartialKeyVerification responseVerification= capturePartialKeyVerification.getValue();

    assertThat(responseVerification).isNotNull();
    assertThat(responseVerification.getError()).contains("Sent backup to wrong trustee 'KeyCeremonyRemoteTrustee1', should be trustee 'who?'");
  }

  @Example
  public void testSendPartialKeyVerifyUnknownGuardian() throws IOException {
    KeyCeremonyRemoteTrustee remote1 = makeRemote(1);
    RemoteKeyCeremonyTrusteeProto.PartialKeyBackup fakeBackup = RemoteKeyCeremonyTrusteeProto.PartialKeyBackup.newBuilder()
            .setDesignatedGuardianId(makeId(1))
            .setGeneratingGuardianId("who?")
            .build();

    remote1.verifyPartialKeyBackup(fakeBackup, observePartialKeyVerification);

    verify(observePartialKeyVerification).onCompleted();
    verify(observePartialKeyVerification).onNext(capturePartialKeyVerification.capture());
    RemoteKeyCeremonyTrusteeProto.PartialKeyVerification responseVerification= capturePartialKeyVerification.getValue();

    assertThat(responseVerification).isNotNull();
    assertThat(responseVerification.getError()).contains("Rsa.decrypt failed");
  }

  @Example
  public void testSendBackupChallengeUnknownGuardian() throws IOException {
    KeyCeremonyRemoteTrustee remote1 = makeRemote(1);
    RemoteKeyCeremonyTrusteeProto.PartialKeyChallenge request = RemoteKeyCeremonyTrusteeProto.PartialKeyChallenge.newBuilder()
            .setGuardianId(makeId(2))
            .build();

    remote1.sendBackupChallenge(request, observePartialKeyChallenge);

    verify(observePartialKeyChallenge).onCompleted();
    verify(observePartialKeyChallenge).onNext(capturePartialKeyChallenge.capture());
    RemoteKeyCeremonyTrusteeProto.PartialKeyChallengeResponse responseChallenge = capturePartialKeyChallenge.getValue();

    assertThat(responseChallenge).isNotNull();
    assertThat(responseChallenge.getError()).contains("Trustee 'KeyCeremonyRemoteTrustee1' does not have backup for 'KeyCeremonyRemoteTrustee2' trustee");
  }

  @Example
  public void testSendBackupChallenge() throws IOException {
    KeyCeremonyRemoteTrustee remote1 = makeRemote(1);
    KeyCeremonyRemoteTrustee remote2 = makeRemote(2);

    // get guardian 2 public key, send to guardian 1
    RemoteKeyCeremonyTrusteeProto.PublicKeySetRequest requestPublicKey =
            RemoteKeyCeremonyTrusteeProto.PublicKeySetRequest.getDefaultInstance();
    remote2.sendPublicKeys(requestPublicKey, observePublicKeySet);
    verify(observePublicKeySet).onCompleted();
    verify(observePublicKeySet).onNext(capturePublicKeySet.capture());
    RemoteKeyCeremonyTrusteeProto.PublicKeySet response2 = capturePublicKeySet.getValue();
    remote1.receivePublicKeys(response2, observeBooleanResponse);

    // now challenge
    RemoteKeyCeremonyTrusteeProto.PartialKeyChallenge request = RemoteKeyCeremonyTrusteeProto.PartialKeyChallenge.newBuilder()
            .setGuardianId(makeId(2))
            .build();

    remote1.sendBackupChallenge(request, observePartialKeyChallenge);

    verify(observePartialKeyChallenge).onCompleted();
    verify(observePartialKeyChallenge).onNext(capturePartialKeyChallenge.capture());
    RemoteKeyCeremonyTrusteeProto.PartialKeyChallengeResponse response = capturePartialKeyChallenge.getValue();

    assertThat(response).isNotNull();
    assertThat(response.getError()).contains("Trustee 'KeyCeremonyRemoteTrustee1' does not have backup for 'KeyCeremonyRemoteTrustee2' trustee");
  }

  @Example
  public void testSendJointPublicKey() throws IOException {
    KeyCeremonyRemoteTrustee remote1 = makeRemote(1);
    KeyCeremonyRemoteTrustee remote2 = makeRemote(2);

    // get guardian 2 public key, send to guardian 1
    RemoteKeyCeremonyTrusteeProto.PublicKeySetRequest requestPublicKey =
            RemoteKeyCeremonyTrusteeProto.PublicKeySetRequest.getDefaultInstance();
    remote2.sendPublicKeys(requestPublicKey, observePublicKeySet);
    verify(observePublicKeySet).onCompleted();
    verify(observePublicKeySet).onNext(capturePublicKeySet.capture());
    RemoteKeyCeremonyTrusteeProto.PublicKeySet keySet2 = capturePublicKeySet.getValue();
    remote1.receivePublicKeys(keySet2, observeBooleanResponse);

    RemoteKeyCeremonyTrusteeProto.JointPublicKeyRequest request = RemoteKeyCeremonyTrusteeProto.JointPublicKeyRequest.getDefaultInstance();
    remote1.sendJointPublicKey(request, observeJointPublicKeyResponse);

    verify(observeJointPublicKeyResponse).onCompleted();
    verify(observeJointPublicKeyResponse).onNext(captureJointPublicKeyResponse.capture());
    RemoteKeyCeremonyTrusteeProto.JointPublicKeyResponse response = captureJointPublicKeyResponse.getValue();

    assertThat(response).isNotNull();
    assertThat(response.getError()).isEmpty();

    remote1.sendPublicKeys(requestPublicKey, observePublicKeySet);
    verify(observePublicKeySet, times(2)).onCompleted();
    verify(observePublicKeySet, times(2)).onNext(capturePublicKeySet.capture());
    RemoteKeyCeremonyTrusteeProto.PublicKeySet keySet1 = capturePublicKeySet.getValue();

    Group.ElementModP key1 = CommonConvert.convertElementModP(keySet1.getCoefficientProofs(0).getPublicKey());
    Group.ElementModP key2 = CommonConvert.convertElementModP(keySet2.getCoefficientProofs(0).getPublicKey());
    Group.ElementModP expected = Group.mult_p(key1, key2);
    Group.ElementModP actual = CommonConvert.convertElementModP(response.getJointPublicKey());
    assertThat(actual).isEqualTo(expected);
  }

  @Example
  public void testSaveState() throws IOException {
    KeyCeremonyRemoteTrustee remote1 = makeRemote(1);
    remote1.saveState(com.google.protobuf.Empty.getDefaultInstance(), observeBooleanResponse);

    verify(observeBooleanResponse).onCompleted();
    verify(observeBooleanResponse).onNext(captureBooleanResponse.capture());
    CommonProto.ErrorResponse response = captureBooleanResponse.getValue();

    assertThat(response).isNotNull();
    assertThat(response.getError()).isEmpty();
  }

  @Example
  public void testFinish() throws IOException {
    KeyCeremonyRemoteTrustee remote1 = makeRemote(1);
    remote1.finish(CommonProto.FinishRequest.getDefaultInstance(), observeBooleanResponse);

    verify(observeBooleanResponse).onCompleted();
    verify(observeBooleanResponse).onNext(captureBooleanResponse.capture());
    CommonProto.ErrorResponse response = captureBooleanResponse.getValue();

    assertThat(response).isNotNull();
    assertThat(response.getError()).isEmpty();
  }

}
