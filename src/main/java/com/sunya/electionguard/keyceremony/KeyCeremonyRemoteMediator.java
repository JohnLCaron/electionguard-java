package com.sunya.electionguard.keyceremony;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.sunya.electionguard.CiphertextElectionContext;
import com.sunya.electionguard.ElectionConstants;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Hash;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.guardian.KeyCeremony2;
import com.sunya.electionguard.publish.Publisher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Mediate the key ceremony with remote Guardians.
 */
class KeyCeremonyRemoteMediator {
  final Manifest election;
  final int quorum;

  Group.ElementModP jointKey;
  Group.ElementModQ commitmentsHash;
  CiphertextElectionContext context;

  List<KeyCeremonyRemoteTrusteeProxy> trusteeProxies;
  List<KeyCeremony.CoefficientValidationSet> coefficientValidationSets = new ArrayList<>();
  Map<String, KeyCeremony2.PublicKeySet> publicKeys = new HashMap<>();

  KeyCeremonyRemoteMediator(Manifest election, int quorum,
                                   List<KeyCeremonyRemoteTrusteeProxy> trusteeProxies) {
    this.election = election;
    this.quorum = quorum;
    this.trusteeProxies = trusteeProxies;
    System.out.printf("  KeyCeremonyRemoteMediator %d Guardians, quorum = %d%n", this.trusteeProxies.size(), this.quorum);

    System.out.printf("  Key Ceremony Round1: exchange public keys%n");
    if (!round1()) {
      throw new RuntimeException("*** Round1 failed");
    }

    System.out.printf("  Key Ceremony Round2: exchange partial key backups%n");
    ListMultimap<String, KeyCeremony2.PartialKeyVerification> failures = ArrayListMultimap.create();
    if (!round2(failures)) {
      throw new RuntimeException("*** Round2 failed");
    }

    System.out.printf("  Key Ceremony Round3: challenge and validate partial key backup responses %n");
    if (!round3(failures)) {
      throw new RuntimeException("*** Round3 failed");
    }

    System.out.printf("  Key Ceremony Round4: compute and check JointKey agreement%n");
    if (!round4()) {
      throw new RuntimeException("*** JointKeys failed");
    }

    System.out.printf("  Key Ceremony makeCoefficientValidationSets%n");
    if (!makeCoefficientValidationSets()) {
      throw new RuntimeException("*** makeCoefficientValidationSets failed");
    }

    this.context = CiphertextElectionContext.create(this.trusteeProxies.size(), this.quorum,
            this.jointKey, this.election, this.commitmentsHash);

    System.out.printf("  Key Ceremony complete%n");
  }

  KeyCeremonyRemoteTrusteeProxy findTrusteeById(String id) {
    return trusteeProxies.stream().filter(t -> t.id().equals(id)).findAny().orElseThrow();
  }

  /**
   * Round 1. Each guardian shares their public keys with all the other guardians
   * Each guardian validates the other guardian's commitments against their proof.
   * Return true on success.
   */
  boolean round1() {
    boolean fail = false;
    for (KeyCeremonyRemoteTrusteeProxy trustee : trusteeProxies) {
      KeyCeremony2.PublicKeySet publicKeys = trustee.sendPublicKeys();
      if (publicKeys == null) {
        System.out.printf("sendPublicKeys failed: '%s' ", trustee.id());
        fail = true;
      } else {
        this.publicKeys.put(publicKeys.ownerId(), publicKeys);
        // one could gather all PublicKeySets and send all at once, for 2*n, rather than n*n total messages.
        for (KeyCeremonyRemoteTrusteeProxy recipient : trusteeProxies) {
          if (!trustee.id().equals(recipient.id())) {
            boolean verify = recipient.receivePublicKeys(publicKeys);
            if (!verify) {
              System.out.printf("PublicKey Commitments:'%s' failed to validate '%s'", recipient.id(), trustee.id());
              fail = true;
            }
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
    for (KeyCeremonyRemoteTrusteeProxy trustee : trusteeProxies) {
      // one could gather all KeyBackups and send all at once, for 2*n, rather than 2*n*n total messages.
      for (KeyCeremonyRemoteTrusteeProxy recipient : trusteeProxies) {
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
      KeyCeremonyRemoteTrusteeProxy challenged = findTrusteeById(failure.generatingGuardianId());
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
    for (KeyCeremonyRemoteTrusteeProxy sender : trusteeProxies) {
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

  boolean publishElectionRecord(Publisher publisher) {
    System.out.printf("Publish ElectionRecord to %s%n", publisher.publishPath());
    // the election record
    try {
      publisher.writeKeyCeremonyProto(
              this.election,
              this.context,
              new ElectionConstants(),
              this.coefficientValidationSets);
      return true;
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
  }

}
