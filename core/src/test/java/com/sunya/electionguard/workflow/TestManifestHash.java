package com.sunya.electionguard.workflow;

import com.sunya.electionguard.Group;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.ElectionRecord;
import net.jqwik.api.Example;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;


public class TestManifestHash {
  String input = "/home/snake/dev/github/electionguard-kotlin-multiplatform/src/commonTest/data/testPython";

  @Example
  public void testManifestHash() throws IOException {
    Consumer consumer = new Consumer(input);
    ElectionRecord electionRecord = consumer.readElectionRecord();
    Manifest manifest = electionRecord.manifest();

    System.out.printf("***manifest%n");
    manifest.recalcCryptoHash(
            manifest.electionScopeId(),
            manifest.electionType(),
            manifest.startDate(),
            manifest.endDate(),
            manifest.geopoliticalUnits(),
            manifest.parties(),
            manifest.candidates(),
            manifest.contests(),
            manifest.ballotStyles(),
            manifest.name(),
            manifest.contactInformation()
    );

    System.out.printf("%n***manifest.electionScopeId '%s' %n", manifest.electionScopeId());
    System.out.printf("***manifest.electionType '%s' %n", manifest.electionType());
    System.out.printf("***manifest.startDate '%s' %n", manifest.startDate());
    System.out.printf("***manifest.endDate '%s' %n", manifest.endDate());
    System.out.printf("***manifest.name '%s' crypto = %s%n", manifest.name(), manifest.name().cryptoHash());
    System.out.printf("***contactInformation.name '%s' crypto = %s%n", manifest.contactInformation(),
            manifest.contactInformation().cryptoHash());

    System.out.printf("***manifest.geopoliticalUnits %n");
    for (Manifest.GeopoliticalUnit gp : manifest.geopoliticalUnits()) {
      System.out.printf("  '%s' = %s %n%n", gp, gp.cryptoHash());
    }

    System.out.printf("***manifest.parties %n");
    for (Manifest.Party gp : manifest.parties()) {
      System.out.printf("  '%s' = %s %n%n", gp, gp.cryptoHash());
    }

    System.out.printf("***manifest.ballotStyles %n");
    for (Manifest.BallotStyle gp : manifest.ballotStyles()) {
      System.out.printf("  '%s' = %s %n%n", gp, gp.cryptoHash());
    }

    System.out.printf("***manifest.candidates %n");
    for (Manifest.Candidate gp : manifest.candidates()) {
      System.out.printf("  '%s' = %s %n%n", gp, gp.cryptoHash());
    }

   for (Manifest.ContestDescription contest : manifest.contests()) {
      System.out.printf(" contest %s = %s %n", contest.contestId(), contest.cryptoHash());
      for (Manifest.SelectionDescription selection : contest.selections()) {
        System.out.printf("  selection %s = %s %n", selection.selectionId(), selection.cryptoHash());
        // Group.ElementModQ recalc = selection.recalcCryptoHash(selection.selectionId(), selection.sequenceOrder(), selection.candidateId());
        // assertThat(recalc).isEqualTo(selection.cryptoHash());
      }
    }

    System.out.printf("%n***manifest.cryptoHash '%s' %n%n", manifest.cryptoHash());
    Group.ElementModQ expected = Group.hex_to_q_unchecked("b2af12d76e8d1a869213fa7f3136eede0bb125250b9f3a23a95998665180d45f");
    assertThat(manifest.cryptoHash()).isEqualTo(expected);
  }
}
