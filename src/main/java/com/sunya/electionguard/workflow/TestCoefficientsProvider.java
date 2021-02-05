package com.sunya.electionguard.workflow;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.proto.KeyCeremonyFromProto;
import com.sunya.electionguard.proto.KeyCeremonyProto;
import com.sunya.electionguard.proto.KeyCeremonyToProto;

import static com.sunya.electionguard.KeyCeremony.CoefficientSet;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class TestCoefficientsProvider implements CoefficientsProvider {
  private static final String WHERE = "/home/snake/tmp/electionguard/coeff.proto";
  private static final int NUMBER_OF_GUARDIANS = 6;
  private static final int QUORUM = 6;

  private Iterable<CoefficientSet> guardianCoefficients;

  @Override
  public int quorum() {
    return QUORUM;
  }

  @Override
  public Iterable<CoefficientSet> guardianCoefficients() {
    if (guardianCoefficients == null) {
      guardianCoefficients = read();
    }
    return guardianCoefficients;
  }

  static ImmutableList<CoefficientSet> read() {
    try {
      return KeyCeremonyFromProto.readCoefficientSet(WHERE);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // run this to initialize
  public static void main(String[] args) throws IOException {
    ArrayList<CoefficientSet> coeffSets = new ArrayList<>();

    for (int k = 0; k < NUMBER_OF_GUARDIANS; k++) {
      int sequence = k + 1;
      ArrayList<Group.ElementModQ> coefficients = new ArrayList<>();
      for (int j = 0; j < QUORUM; j++) {
        coefficients.add(Group.rand_q()); // ramdomly chosen
      }
      coeffSets.add(CoefficientSet.create("guardian_" + sequence, sequence, coefficients));
    }
    KeyCeremonyProto.CoefficientSets proto = KeyCeremonyToProto.convertCoefficientSet(coeffSets);

    try (FileOutputStream out = new FileOutputStream(WHERE)) {
      proto.writeDelimitedTo(out);
    }
  }
}