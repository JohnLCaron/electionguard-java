package com.sunya.electionguard.simulate;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.input.ElectionInputValidation;
import com.sunya.electionguard.keyceremony.KeyCeremonyTrustee;
import com.sunya.electionguard.keyceremony.KeyCeremonyTrusteeIF;
import com.sunya.electionguard.keyceremony.KeyCeremonyRemoteMediator;
import electionguard.protogen.TrusteeProto;
import com.sunya.electionguard.proto.TrusteeToProto;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.PrivateData;
import com.sunya.electionguard.publish.Publisher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

/**
 * A command line program that performs the key ceremony to create the Guardians.
 * <p>
 * For command line help:
 * <strong>
 * <pre>
 *  java -classpath electionguard-java-all.jar com.sunya.electionguard.standard.KeyCeremonySimulator --help
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

    @Parameter(names = {"-nguardians"}, order = 4, description = "Number of guardians to create", required = true)
    int nguardians;

    @Parameter(names = {"-quorum"}, order = 5, description = "Number of guardians that make a quorum", required = true)
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

      Publisher publisher = new Publisher(cmdLine.outputDir, Publisher.Mode.createNew, false);
      KeyCeremonySimulator keyCeremony = new KeyCeremonySimulator(election, cmdLine.nguardians, cmdLine.quorum, publisher);

      keyCeremony.runKeyCeremony();
      System.exit(0);

    } catch (Throwable t) {
      System.out.printf("*** KeyCeremony FAILURE%n");
      t.printStackTrace();
      System.exit(3);
    }
  }

  ///////////////////////////////////////////////////////////////////////////
  final Manifest manifest;
  final int numberOfGuardians;
  final int quorum;
  final Publisher publisher;

  List<KeyCeremonyTrusteeSimulator> trusteeProxies;

  public KeyCeremonySimulator(Manifest manifest, int nguardians, int quorum, Publisher publisher) {
    this.manifest = manifest;
    this.numberOfGuardians = nguardians;
    this.quorum = quorum;
    this.publisher = publisher;
    System.out.printf("  Create %d Guardians, quorum = %d%n", this.numberOfGuardians, this.quorum);

    this.trusteeProxies = new ArrayList<>();
    for (int i = 0; i < nguardians; i++) {
      int seq = i + 1;
      KeyCeremonyTrustee trustee = new KeyCeremonyTrustee("trustee" + seq, seq, quorum, null);
      this.trusteeProxies.add(new KeyCeremonyTrusteeSimulator(trustee));
    }
  }

  private void runKeyCeremony() {
    List<KeyCeremonyTrusteeIF> trusteeIfs = new ArrayList<>(trusteeProxies);
    KeyCeremonyRemoteMediator mediator = new KeyCeremonyRemoteMediator(manifest, quorum, trusteeIfs);
    mediator.runKeyCeremony();

    boolean ok = mediator.publishElectionRecord(this.publisher);
    System.out.printf("%nKey Ceremony publishElectionRecord = %s%n", ok);

    List<KeyCeremonyTrustee> trustees = trusteeProxies.stream()
            .map(t -> t.delegate)
            .toList();
    TrusteeProto.DecryptingTrustees trusteesProto = TrusteeToProto.convertTrustees(trustees);
    boolean okt;
    try {
      PrivateData pdata = publisher.makePrivateData(false, false);
      pdata.writeTrusteesProto(trusteesProto);
      okt = true;
    } catch (IOException e) {
      e.printStackTrace();
      okt = false;
    }
    System.out.printf("%nKey Ceremony publish Trustee = %s%n", okt);

    System.exit(ok && okt ? 0 : 1);
  }

}
