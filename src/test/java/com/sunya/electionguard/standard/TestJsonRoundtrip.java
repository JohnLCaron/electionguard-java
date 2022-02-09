package com.sunya.electionguard.standard;

import com.sunya.electionguard.GuardianRecord;
import com.sunya.electionguard.publish.ConvertFromJson;
import com.sunya.electionguard.publish.ConvertToJson;
import net.jqwik.api.Example;

import java.io.File;
import java.io.IOException;

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
