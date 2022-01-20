package com.sunya.electionguard.standard;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.AvailableGuardian;
import com.sunya.electionguard.BallotBox;
import com.sunya.electionguard.CiphertextBallot;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.ElectionFactory;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.GuardianRecord;
import com.sunya.electionguard.GuardianRecordPrivate;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.PlaintextBallot;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.publish.Coefficients;
import com.sunya.electionguard.publish.ConvertFromJson;
import com.sunya.electionguard.publish.ConvertToJson;
import net.jqwik.api.Example;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;

public class TestJsonRoundtrip {

  @Example
  public void readGuardianRecordPrivateRoundtrip() throws IOException {
    File file = File.createTempFile("temp", null);
    file.deleteOnExit();
    String outputFile = file.getAbsolutePath();

    // original
    Guardian guardian = Guardian.createForTesting("test", 5, 4, 3, null);
    GuardianRecordPrivate org = guardian.export_private_data();
    // write json
    ConvertToJson.writeGuardianRecordPrivate(org, file.toPath());
    // read it back
    GuardianRecordPrivate fromFile = ConvertFromJson.readGuardianRecordPrivate(outputFile);
    assertThat(fromFile).isEqualTo(org);
  }

  @Example
  public void testGuardianRecordRoundtrip() throws IOException {
    File file = File.createTempFile("temp", null);
    file.deleteOnExit();
    String outputFile = file.getAbsolutePath();

    // original
    Guardian guardian = Guardian.createForTesting("test", 5, 4, 3, null);
    GuardianRecord org = guardian.publish();
    // write json
    ConvertToJson.writeGuardianRecord(org, file.toPath());
    // read it back
    GuardianRecord fromFile = ConvertFromJson.readGuardianRecord(outputFile);
    assertThat(fromFile).isEqualTo(org);
  }

}
