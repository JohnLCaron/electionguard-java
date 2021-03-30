package com.sunya.electionguard.guardian;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.sunya.electionguard.CiphertextElectionContext;
import com.sunya.electionguard.ElectionConstants;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Hash;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.input.ElectionInputValidation;
import com.sunya.electionguard.proto.TrusteeProto;
import com.sunya.electionguard.proto.TrusteeToProto;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.Publisher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
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
public class KeyCeremonySimulator {

  private static class CommandLine {
    @Parameter(names = {"-in"}, order = 0,
            description = "Directory containing input election description", required = true)
    String inputDir;

    @Parameter(names = {"-out"}, order = 1,
            description = "Directory where Guardians and election context are written")
    String outputDir;

    @Parameter(names = {"-nguardians"}, order = 4, description = "Number of quardians to create", required = true)
    int nguardians;

    @Parameter(names = {"-quorum"}, order = 5, description = "Number of quardians that make a quorum", required = true)
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
    String progName = KeyCeremonySimulator.class.getName();
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
        System.exit(1);
      }

      Publisher publisher = new Publisher(cmdLine.outputDir, false, false);
      KeyCeremonySimulator keyCeremony = new KeyCeremonySimulator(election, cmdLine.nguardians, cmdLine.quorum, publisher);

      // publish
      if (cmdLine.outputDir != null) {
        boolean ok = keyCeremony.publishElectionRecord(publisher);
        System.out.printf("*** KeyCeremony %s%n", ok ? "SUCCESS" : "FAILURE");
        System.exit(ok ? 0 : 2);
      }
      System.exit(0);

    } catch (Throwable t) {
      System.out.printf("*** KeyCeremony FAILURE%n");
      t.printStackTrace();
      System.exit(3);
    }
  }

  ///////////////////////////////////////////////////////////////////////////
  final Manifest election;
  final int numberOfGuardians;
  final int quorum;

  Group.ElementModP jointKey;
  Group.ElementModQ commitmentsHash;
  CiphertextElectionContext context;

  List<KeyCeremonyTrusteeProxy> trusteeProxies;
  List<KeyCeremony.CoefficientValidationSet> coefficientValidationSets = new ArrayList<>();
  Map<String, KeyCeremony2.PublicKeySet> publicKeys = new HashMap<>();

  public KeyCeremonySimulator(Manifest election, int nguardians, int quorum, Publisher publisher) throws IOException {
    this.election = election;
    this.quorum = quorum;
    this.numberOfGuardians = nguardians;
    System.out.printf("  Create %d Guardians, quorum = %d%n", this.numberOfGuardians, this.quorum);

    this.trusteeProxies = new ArrayList<>();
    List<KeyCeremonyTrustee> trustees = new ArrayList<>();
    for (int i = 0; i < nguardians; i++) {
      int seq = i + 1;
      KeyCeremonyTrustee trustee = new KeyCeremonyTrustee("trustee" + seq, seq, quorum, null);
      trustees.add(trustee);
      this.trusteeProxies.add(new KeyCeremonyTrusteeProxy(trustee));
    }

    System.out.printf("%nKey Ceremony Round1: exchange public keys%n");
    if (!round1()) {
      throw new RuntimeException("*** Round1 failed");
    }

    System.out.printf("%nKey Ceremony Round2: exchange partial key backups%n");
    ListMultimap<String, KeyCeremony2.PartialKeyVerification> failures = ArrayListMultimap.create();
    if (!round2(failures)) {
      throw new RuntimeException("*** Round2 failed");
    }

    System.out.printf("%nKey Ceremony Round3: challenge and validate partial key backup responses %n");
    if (!round3(failures)) {
      throw new RuntimeException("*** Round3 failed");
    }

    System.out.printf("%nKey Ceremony Round4: compute and check JointKey agreement%n");
    if (!round4()) {
      throw new RuntimeException("*** JointKeys failed");
    }

    System.out.printf("%nKey Ceremony makeCoefficientValidationSets%n");
    if (!makeCoefficientValidationSets()) {
      throw new RuntimeException("*** makeCoefficientValidationSets failed");
    }

    this.context = CiphertextElectionContext.create(this.numberOfGuardians, this.quorum,
            this.jointKey, this.election, this.commitmentsHash);

    System.out.printf("%nKey Ceremony publish Guardian serialization%n");
    if (!publishTrustees(publisher, trustees)) {
      throw new RuntimeException("*** publishTrustees failed");
    }

    System.out.printf("%nKey Ceremony publish Election record%n");
    if (!publishElectionRecord(publisher)) {
      throw new RuntimeException("*** publishElectionRecord failed");
    }
  }

  KeyCeremonyTrusteeProxy findTrusteeById(String id) {
    return trusteeProxies.stream().filter(t -> t.id().equals(id)).findAny().orElseThrow();
  }

  /**
   * Round 1. Each guardian shares their public keys with all the other guardians
   * Each guardian validates the other guardian's commitments against their proof.
   * Return true on success.
   */
  boolean round1() {
    boolean fail = false;
    for (KeyCeremonyTrusteeProxy trustee : trusteeProxies) {
      KeyCeremony2.PublicKeySet publicKeys = trustee.sendPublicKeys();
      this.publicKeys.put(publicKeys.ownerId(), publicKeys);
      // one could gather all PublicKeySets and send all at once, for 2*n, rather than n*n total messages.
      for (KeyCeremonyTrusteeProxy recipient : trusteeProxies) {
        if (!trustee.id().equals(recipient.id())) {
          boolean verify =  recipient.receivePublicKeys(publicKeys);
          if (!verify) {
            System.out.printf("PublicKey Commitments:'%s' failed to validate '%s'", recipient.id(), trustee.id());
            fail = true;
          }
        }
      }
    }
    return !fail;
  }

  /**
   * Round 2. Each guardian shares partial key backups with each of the other guardians,
   * Each guardian verifies their own backups.
   * Return true on success.
   */
  boolean round2(ListMultimap<String, KeyCeremony2.PartialKeyVerification> failures) {
    // Share Partial Key Backup
    for (KeyCeremonyTrusteeProxy trustee : trusteeProxies) {
      // one could gather all KeyBackups and send all at once, for 2*n, rather than 2*n*n total messages.
      for (KeyCeremonyTrusteeProxy recipient : trusteeProxies) {
        if (!trustee.id().equals(recipient.id())) {
          // LOOK not seeing the random nonce
          // Each guardian T_i then publishes the encryption E_l (R_i,l , P_i(l)) for every other guardian T_l
          // where R_i,l is a random nonce. This is the ElectionPartialKeyBackup
          KeyCeremony2.PartialKeyBackup backup = trustee.sendPartialKeyBackup(recipient.id());
          KeyCeremony2.PartialKeyVerification verify = recipient.verifyPartialKeyBackup(backup);
          if (!verify.verified()) {
            System.out.printf("Guardian %s backup challenged by Guardian %s%n", trustee.id(), recipient.id());
            failures.put(trustee.id(), verify);
          }
        }
      }
    }
    return true;
  }

  /**
   * Round 3. For any partial backup verification failures, each challenged guardian broadcasts its response to the challenge.
   * The mediator verifies the challenge. In point to point, each guardian would validate.
   */
  boolean round3(ListMultimap<String, KeyCeremony2.PartialKeyVerification> failures) {
    boolean fail = false;
    // Each Guardian verifies all other Guardians' partial key backup
    for (KeyCeremony2.PartialKeyVerification failure : failures.values()) {
      // LOOK when/why does verifyPartialKeyBackup fail, but verifyPartialKeyChallenge succeed?
      //  when the designated Guardian is lying or mistaken?
      System.out.printf("Validate Guardian %s backup that was challenged by Guardian %s%n",
              failure.generatingGuardianId(), failure.designatedGuardianId());

      // If the recipient guardian T_l reports not receiving a suitable value P_i(l), it becomes incumbent on the
      // sending guardian T_i to publish this P_i(l) together with the nonce R_i,l it used to encrypt P_i(l)
      // under the public key E_l of recipient guardian T_l .
      // LOOK wheres the nonce in ElectionPartialKeyChallenge?
      KeyCeremonyTrusteeProxy challenged = findTrusteeById(failure.generatingGuardianId());
      KeyCeremony2.PartialKeyChallengeResponse response = challenged.sendBackupChallenge(failure.designatedGuardianId());

      // If guardian T_i fails to produce a suitable P_i(l)
      // and nonce R_i,l that match both the published encryption and the above equation, it should be
      // excluded from the election and the key generation process should be restarted with an
      // alternate guardian. If, however, the published P_i(l) and R_i,l satisfy both the published
      // encryption and the equation above, the claim of malfeasance is dismissed and the key
      // generation process continues undeterred.
      KeyCeremony2.PublicKeySet generatingKeys = publicKeys.get(response.generatingGuardianId());
      KeyCeremony2.PartialKeyVerification challenge_verify = KeyCeremony2.verify_election_partial_key_challenge(response, generatingKeys.coefficientCommitments());
      if (!challenge_verify.verified()) {
        System.out.printf("***FAILED to validate Guardian %s backup that was challenged by Guardian %s%n",
                failure.generatingGuardianId(), failure.designatedGuardianId());
        fail = true;
      } else {
        System.out.printf("***SUCCESS validate Guardian %s backup that was challenged by Guardian %s%n",
                failure.generatingGuardianId(), failure.designatedGuardianId());
      }
    }
    return !fail;
  }

  /**
   * Round 4. All guardians compute and send their joint election public key.
   * If they agree, then key ceremony is a success.
   */
  boolean round4() {
    boolean allMatch = true;

    SortedMap<String, Group.ElementModP> jointKeys = new TreeMap<>();
    for (KeyCeremonyTrusteeProxy sender : trusteeProxies) {
      Group.ElementModP jointKey = sender.sendJointPublicKey();
      jointKeys.put(sender.id(), jointKey);
      if (this.jointKey == null) {
        this.jointKey = jointKey;
      } else {
        if (!this.jointKey.equals(jointKey)) {
          allMatch = false;
        }
      }
    }

    if (!allMatch) {
      System.out.printf("Not all Guardians agree on JointKey value%n");
      jointKeys.forEach((key, value) -> System.out.printf("  %30s %s%n", key, value));
      System.out.printf("%n");
    }

    return allMatch;
  }

  boolean makeCoefficientValidationSets() {
    // The hashing is order dependent, I think.
    List<KeyCeremony2.PublicKeySet> sorted = this.publicKeys.values().stream()
            .sorted(Comparator.comparing(KeyCeremony2.PublicKeySet::ownerId))
            .collect(Collectors.toList());

    List<Group.ElementModP> commitments = new ArrayList<>();
    for (KeyCeremony2.PublicKeySet keys : sorted) {
      KeyCeremony.CoefficientValidationSet coeffSet = KeyCeremony.CoefficientValidationSet.create(
              keys.ownerId(), keys.coefficientCommitments(), keys.coefficientProofs());
      this.coefficientValidationSets.add(coeffSet);
      commitments.addAll(coeffSet.coefficient_commitments());
    }
    this.commitmentsHash = Hash.hash_elems(commitments);
    return true;
  }

  boolean publishElectionRecord(Publisher publisher) throws IOException {
    // the election record
    publisher.writeKeyCeremonyProto(
            this.election,
            this.context,
            new ElectionConstants(),
            this.coefficientValidationSets);

    return true;
  }

  boolean publishTrustees(Publisher publisher, List<KeyCeremonyTrustee> trustees) throws IOException {
    // FAKE KeyCeremonySimulator cannot publish remote trustees.
    TrusteeProto.Trustees trusteesProto = TrusteeToProto.convertTrustees(trustees);
    publisher.writeTrusteesProto(trusteesProto);
    return true;
  }

}
