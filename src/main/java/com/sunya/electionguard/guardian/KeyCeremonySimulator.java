package com.sunya.electionguard.guardian;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.sunya.electionguard.CiphertextElectionContext;
import com.sunya.electionguard.ElectionConstants;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.proto.TrusteeProto;
import com.sunya.electionguard.proto.TrusteeToProto;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.Publisher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

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
      Publisher publisher = new Publisher(cmdLine.outputDir, false, false);
      KeyCeremonySimulator keyCeremony = new KeyCeremonySimulator(election, cmdLine.nguardians, cmdLine.quorum, publisher);

      // publish
      if (cmdLine.outputDir != null) {
        boolean ok = keyCeremony.publishElectionRecord(publisher);
        System.out.printf("*** KeyCeremony %s%n", ok ? "SUCCESS" : "FAILURE");
        System.exit(ok ? 0 : 1);
      }
      System.exit(0);

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

  Group.ElementModP jointKey;
  Group.ElementModQ commitmentsHash;
  CiphertextElectionContext context;

  List<KeyCeremonyTrustee.KeyCeremonyProxy> trusteeProxies;
  List<KeyCeremony.CoefficientValidationSet> coefficientValidationSets = new ArrayList<>();

  public KeyCeremonySimulator(Manifest election, int nguardians, int quorum, Publisher publisher) throws IOException {
    this.election = election;
    this.quorum = quorum;
    this.numberOfGuardians = nguardians;
    System.out.printf("  Create %d Guardians, quorum = %d%n", this.numberOfGuardians, this.quorum);

    this.trusteeProxies = new ArrayList<>();
    List<KeyCeremonyTrustee> trustees = new ArrayList<>();
    for (int i = 0; i < nguardians; i++) {
      int seq = i + 1;
      KeyCeremonyTrustee trustee = new KeyCeremonyTrustee("trustee" + seq, seq, numberOfGuardians, quorum, null);
      trustees.add(trustee);
      this.trusteeProxies.add(trustee.getKeyCeremonyProxy());
    }
    if (!publishTrustees(publisher, trustees)) {
      throw new RuntimeException("*** publishTrustees failed");
    }

    System.out.printf("%nKey Ceremony Round1%n");
    if (!round1()) {
      throw new RuntimeException("*** Round1 failed");
    }

    System.out.printf("%nKey Ceremony Round2%n");
    if (!round2()) {
      throw new RuntimeException("*** Round2 failed");
    }

    System.out.printf("%nKey Ceremony Round3%n");
    if (!round3()) {
      throw new RuntimeException("*** Round3 failed");
    }

    System.out.printf("%nKey Ceremony JointKeys%n");
    if (!getJointKey()) {
      throw new RuntimeException("*** JointKeys failed");
    }

    System.out.printf("%nKey Ceremony getCoefficientValidationSets%n");
    if (!getCoefficientValidationSets()) {
      throw new RuntimeException("*** CoefficientValidationSets failed");
    }

    this.context = CiphertextElectionContext.create(this.numberOfGuardians, this.quorum,
            this.jointKey, this.election, this.commitmentsHash);

    System.out.printf("%nKey Ceremony publish Guardian serialization%n");
    if (!getCoefficientValidationSets()) {
      throw new RuntimeException("*** CoefficientValidationSets failed");
    }
  }

  /**
   * Round 1. Guardian broadcast/share their public keys and key share commitments.
   * Each guardian shares public keys with all the other guardians.
   * This is announce() of the current KeyCeremonyMediator.
   * LOOK: What is the "key share commitments"? election_public_key_proof? coefficient validation set?
   *     public abstract java.security.PublicKey auxiliary_public_key();
   *     public abstract ElementModP election_public_key();
   *     public abstract SchnorrProof election_public_key_proof();
   */
  boolean round1() {
    // Share public keys
    for (KeyCeremonyTrustee.KeyCeremonyProxy sender : trusteeProxies) {
      KeyCeremony.PublicKeySet publicKeys = sender.sendPublicKeys();
      for (KeyCeremonyTrustee.KeyCeremonyProxy recipient : trusteeProxies) {
        if (sender.id().equals(recipient.id())) {
          continue;
        }
        recipient.receivePublicKeys(publicKeys);
      }
    }

    for (KeyCeremonyTrustee.KeyCeremonyProxy proxy : trusteeProxies) {
      if (!proxy.allPublicKeysReceived()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Round 2. Each guardian uses each others’ auxiliary key to share private key shares and their own views of each other’s keys.
   * Each guardian shares partial key backups with all the other guardians (orchestrate()).
   */
   boolean round2() {
     // Share Partial Key Backup
     for (KeyCeremonyTrustee.KeyCeremonyProxy sender : trusteeProxies) {
       for (KeyCeremonyTrustee.KeyCeremonyProxy recipient : trusteeProxies) {
         if (sender.id().equals(recipient.id())) {
           continue;
         }
         // Each guardian T_i then publishes the encryption E_l (R_i,l , P_i(l)) for every other guardian T_l
         // where R_i,l is a random nonce. This is apparently the ElectionPartialKeyBackup
         KeyCeremony.ElectionPartialKeyBackup backup = sender.sendPartialKeyBackup(recipient.id());
         recipient.receivePartialKeyBackup(backup);
       }
     }

     for (KeyCeremonyTrustee.KeyCeremonyProxy proxy : trusteeProxies) {
       if (!proxy.allBackupsReceived()) {
         return false;
       }
     }
     return true;
   }


  /**
   * Round 3. Each guardian “broadcast” confirmations or objections.
   * Round 4. Each challenged guardian broadcasts it key shares sent to challenge.
   * Each guardian verifies that partial key backups contains a point on owners polynomial (validate()).
   */
  boolean round3() {
    // Each Guardian verifies all other Guardians' partial key backup
    for (KeyCeremonyTrustee.KeyCeremonyProxy sender : trusteeProxies) {
      for (KeyCeremonyTrustee.KeyCeremonyProxy recipient : trusteeProxies) {
        if (sender.id().equals(recipient.id())) {
          continue;
        }
        // Guardian T_l can now decrypt each P_i(l) encrypted to its public key and verify its validity
        // against the commitments made by T_i.
        KeyCeremony.ElectionPartialKeyBackup backup = sender.sendPartialKeyBackup(recipient.id());
        // Guardians then publicly report having confirmed or failed to confirm this computation.
        KeyCeremony.ElectionPartialKeyVerification verify = recipient.verifyPartialKeyBackup(backup);

        if (!verify.verified()) {
          // LOOK when/why does verifyPartialKeyBackup fail, but verifyPartialKeyChallenge succeed?
          System.out.printf("Guardian %s backup challenged by Guardian %s%n", sender.id(), recipient.id());

          // If the recipient guardian T_l reports not receiving a suitable value P_i(l), it becomes incumbent on the
          // sending guardian T_i to publish this P_i(l) together with the nonce R_i,l it used to encrypt P_i(l)
          // under the public key E_l of recipient guardian T_l .
          // LOOK wheres the nonce in ElectionPartialKeyChallenge?
          KeyCeremony.ElectionPartialKeyChallenge challenge = sender.sendBackupChallenge(recipient.id());

          // If guardian T_i fails to produce a suitable P_i(l)
          // and nonce R_i,l that match both the published encryption and the above equation, it should be
          // excluded from the election and the key generation process should be restarted with an
          // alternate guardian. If, however, the published P_i(l) and R_i,l satisfy both the published
          // encryption and the equation above, the claim of malfeasance is dismissed and the key
          // generation process continues undeterred.
          // LOOK anyone can verify, probably best not done by recipient?
          KeyCeremony.ElectionPartialKeyVerification challenge_verify = recipient.verifyPartialKeyChallenge(challenge);
          if (!challenge_verify.verified()) {
            System.out.printf("Guardian %s failed to provide verifyPartialKeyChallenge from Guardian %s%n",
                    sender.id(), recipient.id());
            return false;
          }
        }
      }
    }
    return true;
  }

  boolean getJointKey() {
    boolean allMatch = true;

    SortedMap<String, Group.ElementModP> jointKeys = new TreeMap<>();
    for (KeyCeremonyTrustee.KeyCeremonyProxy sender : trusteeProxies) {
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

  boolean getCoefficientValidationSets() {
    SortedMap<String, Group.ElementModP> jointKeys = new TreeMap<>();
    for (KeyCeremonyTrustee.KeyCeremonyProxy sender : trusteeProxies) {
      KeyCeremony.CoefficientValidationSet coeffSet = sender.sendCoefficientValidationSet();
      this.coefficientValidationSets.add(coeffSet);
    }
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
    // KeyCeremonySimulator cannot publish remote trustees.
    TrusteeProto.Trustees trusteesProto = TrusteeToProto.convertTrustees(trustees);
    publisher.writeTrusteesProto(trusteesProto);
    return true;
  }

}
