package com.sunya.electionguard.workflow;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.collect.Iterables;
import com.sunya.electionguard.Election;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Guardian;
import com.sunya.electionguard.GuardianBuilder;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.KeyCeremonyMediator;
import com.sunya.electionguard.proto.KeyCeremonyProto;
import com.sunya.electionguard.proto.KeyCeremonyToProto;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.Publisher;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** KeyCeremony to create the Guardians. */
public class PerformKeyCeremony {

  private static class CommandLine {
    @Parameter(names = {"-in"},
            description = "Directory containing input election description", required = true)
    String inputDir;

    @Parameter(names = {"--proto"}, description = "Input election record is in proto format")
    boolean isProto = false;

    @Parameter(names = {"-out"},
            description = "Directory where Guardians and election context is written", required = true)
    String outputDir;

    @Parameter(names = {"-coefficients"},
            description = "CoefficientsProvider classname")
    String coefficientsProviderClass;

    @Parameter(names = {"-nguardians"}, description = "Number of quardians to create (required if no coefficients)")
    int nguardians;

    @Parameter(names = {"-quorum"}, description = "Number of quardians that make a quorum (required if no coefficients)")
    int quorum;

    @Parameter(names = {"-h", "--help"}, description = "Display this help and exit", help = true)
    boolean help = false;

    private final JCommander jc;

    public CommandLine(String progName, String[] args) throws ParameterException {
      this.jc = new JCommander(this);
      this.jc.parse(args);
      jc.setProgramName(progName); // Displayed in the usage information.
    }

    public void printUsage() {
      jc.usage();
    }
  }

  public static void main(String[] args) throws IOException {
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

    // all we need from election record is the ElectionDescription.
    Consumer consumer = new Consumer(cmdLine.inputDir);
    Election.ElectionDescription election;
    if (cmdLine.isProto) {
      election = consumer.readElectionRecordProto().election;
    } else {
      election = consumer.election();
    }

    CoefficientsProvider coefficientsProvider = null;
    if (cmdLine.coefficientsProviderClass != null) {
      try {
        coefficientsProvider = makeCoefficientsProvider(cmdLine.coefficientsProviderClass);
      } catch (Throwable t) {
        t.printStackTrace();
        System.exit(2);
      }
    }

    System.out.printf("KeyCeremony read election description from directory %s%n", cmdLine.inputDir);
    System.out.printf("  write Guardians to directory %s%n", cmdLine.outputDir);
    if (coefficientsProvider == null) {
      System.out.printf("  generate random Guardian coefficients%n");
    } else {
      System.out.printf("  Guardian coefficients provided by %s%n", coefficientsProvider);
    }
    if (coefficientsProvider == null) {
      coefficientsProvider = new RandomCoefficientsProvider(cmdLine.nguardians, cmdLine.quorum);
    }
    PerformKeyCeremony keyCeremony = new PerformKeyCeremony(election, coefficientsProvider);

    try {
      // publish
      keyCeremony.publish(cmdLine.outputDir);
      System.exit(0);

    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(5);
    }
  }

  static CoefficientsProvider makeCoefficientsProvider(String className) throws Throwable {
    Class<?> c = Class.forName(className);
    if (!(CoefficientsProvider.class.isAssignableFrom(c))) {
      throw new IllegalArgumentException(String.format("CoefficientsProvider '%s' does not implement %s", c.getName(), CoefficientsProvider.class));
    }
    Constructor<CoefficientsProvider> constructor = (Constructor<CoefficientsProvider>) c.getConstructor();
    return constructor.newInstance();
  }

  static class RandomCoefficientsProvider implements CoefficientsProvider {
    final int nguardians;
    final int quorum;

    public RandomCoefficientsProvider(int nguardians, int quorum) {
      this.nguardians = nguardians;
      this.quorum = quorum;
    }

    @Override
    public int quorum() {
      return quorum;
    }

    @Override
    public Iterable<com.sunya.electionguard.KeyCeremony.CoefficientSet> guardianCoefficients() {
      ArrayList<com.sunya.electionguard.KeyCeremony.CoefficientSet> coeffSets = new ArrayList<>();
      for (int k = 0; k < this.nguardians; k++) {
        int sequence = k + 1;
        ArrayList<Group.ElementModQ> coefficients = new ArrayList<>();
        for (int j = 0; j < this.quorum; j++) {
          coefficients.add(Group.rand_q()); // ramdomly chosen
        }
        coeffSets.add(com.sunya.electionguard.KeyCeremony.CoefficientSet.create("guardian_" + sequence, sequence, coefficients));
      }
      return coeffSets;
    }
  }

  ///////////////////////////////////////////////////////////////////////////
  final Election.ElectionDescription election;
  final int numberOfGuardians;
  final int quorum;

  Group.ElementModP jointKey;
  Election.CiphertextElectionContext context;
  Group.ElementModQ crypto_base_hash;

  List<GuardianBuilder> guardianBuilders;
  List<com.sunya.electionguard.KeyCeremony.CoefficientValidationSet> coefficientValidationSets = new ArrayList<>();

  public PerformKeyCeremony(Election.ElectionDescription election, CoefficientsProvider coefficientsProvider) {
    this.election = election;
    this.quorum = coefficientsProvider.quorum();
    this.numberOfGuardians = Iterables.size(coefficientsProvider.guardianCoefficients());
    System.out.printf("  Create %d Guardians, quorum = %d%n", this.numberOfGuardians, this.quorum);

    this.crypto_base_hash = Election.make_crypto_base_hash(this.numberOfGuardians, this.quorum, election);

    this.guardianBuilders = new ArrayList<>();
    for (KeyCeremony.CoefficientSet coeffSet : coefficientsProvider.guardianCoefficients()) {
      this.guardianBuilders.add(GuardianBuilder.createRandom(coeffSet, numberOfGuardians, quorum));
    }

    System.out.printf("%nKey Ceremony%n");
    if (!keyCeremony()) {
      throw new RuntimeException("*** Key Ceremony failed");
    }
    buildElectionContext(this.election, this.jointKey);
  }

  /**
   * Using the numberOfGuardians, generate public-private keypairs and share
   * representations of those keys with quorum of other Guardians.  Then, combine
   * the public election keys to make a joint election key that is used to encrypt ballots
   */
  boolean keyCeremony() {
    // Setup Mediator
    com.sunya.electionguard.KeyCeremony.CeremonyDetails details =
            com.sunya.electionguard.KeyCeremony.CeremonyDetails.create(this.numberOfGuardians,  this.quorum);
    KeyCeremonyMediator keyCeremony = new KeyCeremonyMediator(details);

    // Attendance (Public Key Share)
    for (GuardianBuilder guardian : this.guardianBuilders) {
      keyCeremony.announce(guardian);
    }

    System.out.printf(" Confirm all guardians have shared their public keys%n");
    if (!keyCeremony.all_guardians_in_attendance()) {
      System.out.printf(" *** FAILED%n");
      return false;
    }

    // Run the Key Ceremony process, which shares the keys among the guardians
    Optional<List<GuardianBuilder>> orchestrated = keyCeremony.orchestrate(null);
    System.out.printf(" Execute the key exchange between guardians%n");
    if (orchestrated.isEmpty()) {
      System.out.printf(" *** FAILED%n");
      return false;
    }

    System.out.printf(" Confirm all guardians have shared their partial key backups%n");
    if (!keyCeremony.all_election_partial_key_backups_available()) {
      System.out.printf(" *** FAILED%n");
      return false;
    }

    // Verification
    boolean verified = keyCeremony.verify(null);
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

    // Joint Key
    Optional<Group.ElementModP> joint_key = keyCeremony.publish_joint_key();
    System.out.printf(" Create the Joint Election Key%n");
    if (joint_key.isEmpty()) {
      System.out.printf(" *** FAILED%n");
      return false;
    }
    this.jointKey = joint_key.get();

    // Save Validation Keys
    for (GuardianBuilder guardian : this.guardianBuilders) {
      this.coefficientValidationSets.add(guardian.share_coefficient_validation_set());
    }

    return true;
  }

  void buildElectionContext(Election.ElectionDescription description, Group.ElementModP joint_key) {
    this.context = Election.make_ciphertext_election_context(this.numberOfGuardians, this.quorum,
            joint_key, description);
  }

  void publish(String publishDir) throws IOException {
    // the election record
    Publisher publisher = new Publisher(publishDir, false, false);
    publisher.writeKeyCeremonyProto(
            this.election,
            this.context,
            new Election.ElectionConstants(),
            this.coefficientValidationSets);

    // the quardians - private info
    List<Guardian> guardians = guardianBuilders.stream().map(gb -> gb.build()).collect(Collectors.toList());
    KeyCeremonyProto.Guardians guardianProto = KeyCeremonyToProto.convertGuardians(guardians, this.quorum);

    publisher.writeGuardiansProto(guardianProto);
  }
}
