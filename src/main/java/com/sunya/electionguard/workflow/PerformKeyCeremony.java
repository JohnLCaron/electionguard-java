package com.sunya.electionguard.workflow;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.sunya.electionguard.CiphertextElectionContext;
import com.sunya.electionguard.ElectionConstants;
import com.sunya.electionguard.GuardianRecord;
import com.sunya.electionguard.GuardianRecordPrivate;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Guardian;
import com.sunya.electionguard.Hash;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.KeyCeremonyMediator;
import com.sunya.electionguard.input.ElectionInputValidation;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.Publisher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A command line program that performs the key ceremony to create the Guardians.
 * <p>
 * For command line help:
 * <strong>
 * <pre>
 *  java -classpath electionguard-java-all.jar com.sunya.electionguard.workflow.PerformKeyCeremony --help
 * </pre>
 * </strong>
 *
 * @see <a href="https://www.electionguard.vote/spec/0.95.0/4_Key_generation/#details-of-key-generation">Key Generation</a>
 */
public class PerformKeyCeremony {

  private static class CommandLine {
    @Parameter(names = {"-in"}, order = 0,
            description = "Directory containing input election description", required = true)
    String inputDir;

    @Parameter(names = {"-out"}, order = 1,
            description = "Directory where Guardians and election context are written", required = true)
    String outputDir;

    @Parameter(names = {"-nguardians"}, order = 4, description = "Number of guardians to create (required if no coefficients)")
    int nguardians;

    @Parameter(names = {"-quorum"}, order = 5, description = "Number of guardians that make a quorum (required if no coefficients)")
    int quorum;

    @Parameter(names = {"-h", "--help"}, order = 6, description = "Display this help and exit", help = true)
    boolean help = false;

    private final JCommander jc;

    public CommandLine(String progName, String[] args) throws ParameterException {
      this.jc = new JCommander(this);
      this.jc.parse(args);
      jc.setProgramName(String.format("java -classpath electionguard-java-all.jar %s", progName));
    }

    public void printUsage() {
      jc.usage();
    }
  }

  public static void main(String[] args) {
    String progName = PerformKeyCeremony.class.getName();
    CommandLine cmdLine = null;

    try {
      cmdLine = new CommandLine(progName, args);
      if (cmdLine.help) {
        cmdLine.printUsage();
        return;
      }
    } catch (ParameterException e) {
      System.err.println(e.getMessage());
      System.err.printf("Try '%s --help' for more information.%n", progName);
      System.exit(1);
    }

    try {
      // all we need from election record is the ElectionDescription.
      Consumer consumer = new Consumer(cmdLine.inputDir);
      Manifest election = consumer.readManifest();
      ElectionInputValidation validator = new ElectionInputValidation(election);
      Formatter errors = new Formatter();
      if (!validator.validateElection(errors)) {
        System.out.printf("*** ElectionInputValidation FAILED on %s%n%s", cmdLine.inputDir, errors);
        System.exit(3);
      }

      System.out.printf("KeyCeremony read election description from directory %s%n", cmdLine.inputDir);
      System.out.printf("  write Guardians to directory %s%n", cmdLine.outputDir);
      System.out.printf("  generate %d random Guardian coefficients%n", cmdLine.quorum);
      PerformKeyCeremony keyCeremony = new PerformKeyCeremony(election, cmdLine.nguardians, cmdLine.quorum);

      // publish
      boolean ok = keyCeremony.publish(cmdLine.outputDir);
      System.out.printf("*** KeyCeremony %s%n", ok ? "SUCCESS" : "FAILURE");
      System.exit(ok ? 0 : 1);

    } catch (Throwable t) {
      System.out.printf("*** KeyCeremony FAILURE%n");
      t.printStackTrace();
      System.exit(2);
    }
  }

  ///////////////////////////////////////////////////////////////////////////
  final Manifest election;
  final int numberOfGuardians;
  final int quorum;

  KeyCeremony.ElectionJointKey jointKey;
  Group.ElementModQ commitmentsHash;
  CiphertextElectionContext context;

  List<Guardian> guardians;
  List<GuardianRecord> guardian_records = new ArrayList<>();

  public PerformKeyCeremony(Manifest election, int nguardians, int quorum) {
    this.election = election;
    this.quorum = quorum;
    this.numberOfGuardians = nguardians;
    System.out.printf("  Create %d Guardians, quorum = %d%n", this.numberOfGuardians, this.quorum);

    this.guardians = new ArrayList<>();
    // Setup Guardians
    for (int i = 1; i <= nguardians; i++) {
      this.guardians.add(Guardian.createForTesting("guardian_" + i, i, nguardians, quorum, null));
    }

    System.out.printf("%nKey Ceremony%n");
    if (!keyCeremony()) {
      throw new RuntimeException("*** Key Ceremony failed");
    }

    this.context = CiphertextElectionContext.create(this.numberOfGuardians, this.quorum,
            this.jointKey.joint_public_key(), this.election, this.commitmentsHash);
  }

  /**
   * Using the numberOfGuardians, generate public-private keypairs and share
   * representations of those keys with quorum of other Guardians.  Then, combine
   * the public election keys to make a joint election key that is used to encrypt ballots
   */
  boolean keyCeremony() {
    // Setup Mediator
    com.sunya.electionguard.KeyCeremony.CeremonyDetails details =
            com.sunya.electionguard.KeyCeremony.CeremonyDetails.create(this.numberOfGuardians, this.quorum);
    KeyCeremonyMediator keyCeremony = new KeyCeremonyMediator("PerformKeyCeremony", details);

    // ROUND 1: Public Key Sharing
    // Attendance (Public Key Share)
    for (Guardian guardian : this.guardians) {
      keyCeremony.announce(guardian.share_public_keys());
    }

    System.out.printf(" Confirm all guardians have shared their public keys%n");
    for (Guardian guardian : this.guardians) {
      List<KeyCeremony.PublicKeySet> announced_keys = keyCeremony.share_announced(null).orElseThrow();
      for (KeyCeremony.PublicKeySet key_set : announced_keys) {
        if (!guardian.object_id.equals(key_set.election().owner_id())) {
          guardian.save_guardian_public_keys(key_set);
        }
      }
    }

    System.out.printf(" Confirm all guardians have shared their public keys%n");
    if (!keyCeremony.all_guardians_announced()) {
      System.out.printf(" *** FAILED%n");
      return false;
    }

    // ROUND 2: Election Partial Key Backup Sharing
    //Share Backups
    for (Guardian sending_guardian : this.guardians) {
      sending_guardian.generate_election_partial_key_backups(null);
      List<KeyCeremony.ElectionPartialKeyBackup> backups = new ArrayList<>();
      for (Guardian designated_guardian : this.guardians) {
        if (!designated_guardian.object_id.equals(sending_guardian.object_id)) {
          backups.add(sending_guardian.share_election_partial_key_backup(designated_guardian.object_id).orElseThrow());
        }
      }
      keyCeremony.receive_backups(backups);
      System.out.printf("Receive election partial key backups from key owning guardian %s%n", sending_guardian.object_id);
    }

    System.out.printf("Confirm all guardians have shared their election partial key backups%n");
    if (!keyCeremony.all_election_partial_key_backups_available()) {
      System.out.printf(" *** FAILED%n");
      return false;
    }

    // Receive Backups
    for (Guardian designated_guardian : this.guardians) {
      List<KeyCeremony.ElectionPartialKeyBackup> backups = keyCeremony.share_backups(designated_guardian.object_id).orElseThrow();
      if (backups.size() != numberOfGuardians - 1) {
        System.out.printf(" %s share_backups must have %d *** FAILED%n", designated_guardian.object_id, numberOfGuardians - 1);
        return false;
      }
      for (KeyCeremony.ElectionPartialKeyBackup backup : backups) {
        designated_guardian.save_election_partial_key_backup(backup);
      }
    }

    // ROUND 3: Verification of Backups
    // Verify Backups
    for (Guardian designated_guardian : this.guardians) {
      List<KeyCeremony.ElectionPartialKeyVerification> verifications = new ArrayList<>();
      for (Guardian backup_owner : this.guardians) {
        if (!designated_guardian.object_id.equals(backup_owner.object_id)) {
          KeyCeremony.ElectionPartialKeyVerification verification =
                  designated_guardian.verify_election_partial_key_backup(
                          backup_owner.object_id, null).orElseThrow();
          verifications.add(verification);
        }
      }
      keyCeremony.receive_backup_verifications(verifications);
    }

    // Verification
    boolean verified = keyCeremony.all_backups_verified();
    System.out.printf(" Confirm all guardians truthfully executed the ceremony%n");
    if (!verified) {
      System.out.printf(" *** FAILED%n");
      return false;
    }

    System.out.printf(" Confirm all guardians have submitted a verification of the backups of all other guardians%n");
    if (!keyCeremony.all_election_partial_key_verifications_received()) {
      System.out.printf(" *** FAILED%n");
      return false;
    }

    System.out.printf(" Confirm all guardians have verified the backups of all other guardians%n");
    if (!keyCeremony.all_election_partial_key_backups_verified()) {
      System.out.printf(" *** FAILED%n");
      return false;
    }

    // FINAL: Publish Joint Key
    Optional<KeyCeremony.ElectionJointKey> joint_key = keyCeremony.publish_joint_key();
    System.out.printf(" Create the Joint Manifest Key%n");
    if (joint_key.isEmpty()) {
      System.out.printf(" *** FAILED%n");
      return false;
    }
    this.jointKey = joint_key.get();

    // Save CoefficientValidations, calculate commitment hash
    // Save Validation Keys
    List<Group.ElementModP> commitments = new ArrayList<>();
    for (Guardian guardian : this.guardians) {
      GuardianRecord guardianRecord = guardian.publish();
      this.guardian_records.add(guardianRecord);
      commitments.addAll(guardianRecord.election_commitments());

      Group.ElementModQ commitmentHash = Hash.hash_elems(guardianRecord.election_commitments());
      System.out.printf(" %d commitmentHash %s%n", guardian.sequence_order, commitmentHash);
    }
    this.commitmentsHash = Hash.hash_elems(commitments);
    if (!this.commitmentsHash.equals(this.jointKey.commitment_hash())) {
      System.out.printf(" commitmentHash *** FAILED%n");
      return false;
    }

    return true;
  }

  boolean publish(String publishDir) throws IOException {
    Publisher publisher = new Publisher(publishDir, true, true);
    publisher.writeKeyCeremonyJson(
            this.election,
            this.context,
            new ElectionConstants(),
            this.guardian_records);

    // save private data for decrypting
    List<GuardianRecordPrivate> gprivate = this.guardians.stream().map(g -> g.export_private_data()).collect(Collectors.toList());
    publisher.writeGuardiansJson(gprivate);
    return true;
  }
}
