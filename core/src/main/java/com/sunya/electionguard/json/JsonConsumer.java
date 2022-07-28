package com.sunya.electionguard.json;

import com.google.common.base.Preconditions;
import com.sunya.electionguard.ballot.DecryptingGuardian;
import com.sunya.electionguard.ballot.EncryptedTally;
import com.sunya.electionguard.ElectionConstants;
import com.sunya.electionguard.ElectionCryptoContext;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.GuardianRecord;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.ballot.EncryptedBallot;
import com.sunya.electionguard.publish.CloseableIterableAdapter;
import com.sunya.electionguard.publish.ElectionRecord;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Helper class for consumers of published election records in JSON. */
public class JsonConsumer {
  private final JsonPublisher publisher;

  public JsonConsumer(JsonPublisher publisher) {
    this.publisher = publisher;
  }

  public JsonConsumer(String topDir) throws IOException {
    publisher = new JsonPublisher(topDir, JsonPublisher.Mode.readonly);
  }

  public static JsonConsumer fromElectionRecord(String electionRecordDir) throws IOException {
    return new JsonConsumer(new JsonPublisher(Path.of(electionRecordDir), JsonPublisher.Mode.readonly));
  }

  public String location() {
    return publisher.publishPath().toAbsolutePath().toString();
  }

  public boolean isValidElectionRecord(Formatter error) {
    if (!Files.exists(publisher.publishPath())) {
      error.format("%s does not exist", publisher.publishPath());
      return false;
    }
    return true;
  }

  public ElectionRecord readElectionRecord() throws IOException {
    ElectionRecord result;
    if (Files.exists(publisher.constantsPath()))  {
      result = readElectionRecordJson();
    } else {
      throw new FileNotFoundException(String.format("No election record found in %s", publisher.publishPath()));
    }

    /* check constants
    if (!result.constants.equals(Group.getPrimes())) {
      System.out.printf("** Non-standard constants in %s%n", publisher.publishPath());
      Group.setPrimes(result.constants);
    } */
    return result;
  }

  //////////////////// Json

  public ElectionRecordJson readElectionRecordJson() throws IOException {
    return new ElectionRecordJson(
            ElectionRecordJson.currentVersion, // logic is that it would fail on an earlier version TODO add to Json
            this.manifest(), // required
            this.constants(), // required
            this.context(),
            this.guardianRecords(),
            this.devices(),
            this.ciphertextTally(),
            this.decryptedTally(),
            CloseableIterableAdapter.wrap(this.acceptedBallots()),
            CloseableIterableAdapter.wrap(this.spoiledBallots()),
            this.availableGuardians());
  }

  public Manifest manifest() throws IOException {
    return ConvertFromJson.readManifest(publisher.manifestPath().toString());
  }

  public ElectionConstants constants() throws IOException {
    ElectionConstants result = ConvertFromJson.readConstants(publisher.constantsPath().toString());
    ElectionConstants current = Group.getPrimes();
    Preconditions.checkArgument(current.equals(result));
    return result;
  }

  @Nullable
  public ElectionCryptoContext context() throws IOException {
    if (Files.exists(publisher.contextPath())) {
      return ConvertFromJson.readContext(publisher.contextPath().toString());
    }
    return null;
  }

  @Nullable
  public PlaintextTally decryptedTally() throws IOException {
    if (Files.exists(publisher.tallyPath())) {
      return ConvertFromJson.readPlaintextTally(publisher.tallyPath().toString());
    }
    return null;
  }

  @Nullable
  public EncryptedTally ciphertextTally() throws IOException {
    if (Files.exists(publisher.encryptedTallyPath())) {
      return ConvertFromJson.readCiphertextTally(publisher.encryptedTallyPath().toString());
    }
    return null;
  }

  public List<Encrypt.EncryptionDevice> devices() throws IOException {
    List<Encrypt.EncryptionDevice> result = new ArrayList<>();
    for (File file : publisher.deviceFiles()) {
      Encrypt.EncryptionDevice fromPython = ConvertFromJson.readDevice(file.getAbsolutePath());
      result.add(fromPython);
    }
    return result;
  }

  public List<EncryptedBallot> acceptedBallots() throws IOException {
    List<EncryptedBallot> result = new ArrayList<>();
    for (File file : publisher.ballotFiles()) {
      EncryptedBallot fromPython = ConvertFromJson.readSubmittedBallot(file.getAbsolutePath());
      result.add(fromPython);
    }
    return result;
  }

  public List<DecryptingGuardian> availableGuardians() throws IOException {
    if (!publisher.coefficientsPath().toFile().exists()) {
      return new ArrayList<>();
    }
    Map<String, GuardianRecord> grMap = guardianRecords().stream().collect(Collectors.toMap(
            GuardianRecord::guardianId, gr -> gr));
    LagrangeCoefficientsPojo coeffPojo = ConvertFromJson.readCoefficients(publisher.coefficientsPath().toString());
    // Preconditions.checkArgument(grs.size() == pojo.coefficients.size());
    List<DecryptingGuardian> result = new ArrayList<>();
    for (Map.Entry<String, Group.ElementModQ> entry : coeffPojo.coefficients.entrySet()) {
      GuardianRecord gr = grMap.get(entry.getKey());
      DecryptingGuardian avail = new DecryptingGuardian(entry.getKey(), gr.xCoordinate(), entry.getValue());
      result.add(avail);
    }
    return result;
  }

  // Decrypted, spoiled ballots
  public List<PlaintextTally> spoiledBallots() {
    List<PlaintextTally> result = new ArrayList<>();
    for (File file : publisher.spoiledBallotFiles()) {
      try {
        PlaintextTally fromPython = ConvertFromJson.readPlaintextTally(file.getAbsolutePath());
        result.add(fromPython);
      } catch (Exception e) {
        // System.out.printf("Exception %s on %s%n", e.getMessage(), file.getAbsolutePath());
      }
    }
    return result;
  }

  public List<GuardianRecord> guardianRecords() throws IOException {
    List<GuardianRecord> result = new ArrayList<>();
    for (File file : publisher.guardianRecordsFiles()) {
      GuardianRecord fromPython = ConvertFromJson.readGuardianRecord(file.getAbsolutePath());
      result.add(fromPython);
    }
    return result;
  }
}
