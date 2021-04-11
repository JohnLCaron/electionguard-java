package com.sunya.electionguard.keyceremony;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.CiphertextElectionContext;
import com.sunya.electionguard.ElectionConstants;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Hash;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.publish.Publisher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Mediate the key ceremony with remote Guardians.
 */
public class KeyCeremonyTrusteeMediator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  final Manifest election;
  final int quorum;

  Group.ElementModP jointKey;
  Group.ElementModQ commitmentsHash;
  CiphertextElectionContext context;

  List<KeyCeremonyTrusteeIF> trusteeProxies;
  List<KeyCeremony.CoefficientValidationSet> coefficientValidationSets = new ArrayList<>();
  Map<String, KeyCeremony2.PublicKeySet> publicKeysMap = new HashMap<>();

  /**
   * This runs the key ceremony. Caller calls publishElectionRecord() separately.
   * Caller is in charge od saving trustee state.
   */
  public KeyCeremonyTrusteeMediator(Manifest election, int quorum,
                                    List<KeyCeremonyTrusteeIF> trusteeProxies) {
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

  KeyCeremonyTrusteeIF findTrusteeById(String id) {
    return trusteeProxies.stream().filter(t -> t.id().equals(id)).findAny().orElseThrow();
  }

  /**
   * Round 1. Each guardian shares their public keys with all the other guardians
   * Each guardian validates the other guardian's commitments against their proof.
   * Return true on success.
   */
  public boolean round1() {
    boolean fail = false;
    for (KeyCeremonyTrusteeIF trustee : trusteeProxies) {
      Optional<KeyCeremony2.PublicKeySet> publicKeysO = trustee.sendPublicKeys();
      if (publicKeysO.isEmpty()) {
        fail = true;
      } else {
        KeyCeremony2.PublicKeySet publicKeys = publicKeysO.get();
        this.publicKeysMap.put(publicKeys.ownerId(), publicKeys);
        // one could gather all PublicKeySets and send all at once, for 2*n, rather than n*n total messages.
        for (KeyCeremonyTrusteeIF recipient : trusteeProxies) {
          if (!trustee.id().equals(recipient.id())) {
            boolean verify = recipient.receivePublicKeys(publicKeys);
            if (!verify) {
              System.out.printf("PublicKey Commitments: '%s' failed to validate '%s'", recipient.id(), trustee.id());
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
  public boolean round2(ListMultimap<String, KeyCeremony2.PartialKeyVerification> failures) {
    boolean fail = false;
    for (KeyCeremonyTrusteeIF trustee : trusteeProxies) {
      // one could gather all KeyBackups and send all at once, for 2*n, rather than 2*n*n total messages.
      for (KeyCeremonyTrusteeIF recipient : trusteeProxies) {
        if (!trustee.id().equals(recipient.id())) {
          // Each guardian T_i then publishes the encryption E_l(P_i(l)) for every other guardian T_l
          // This is the ElectionPartialKeyBackup
          Optional<KeyCeremony2.PartialKeyBackup> backupO = trustee.sendPartialKeyBackup(recipient.id());
          if (backupO.isEmpty()) {
            fail = true;
          } else {
            KeyCeremony2.PartialKeyBackup backup = backupO.get();
            Optional<KeyCeremony2.PartialKeyVerification> verifyO = recipient.verifyPartialKeyBackup(backup);
            if (verifyO.isEmpty()) {
              fail = true;
            } else {
              KeyCeremony2.PartialKeyVerification verify = verifyO.get();
              if (!verify.error().isEmpty()) {
                System.out.printf("Guardian %s backup challenged by Guardian %s error = '%s'%n",
                        trustee.id(), recipient.id(), verify.error());
                failures.put(trustee.id(), verify);
              }
            }
          }
        }
      }
    }
    return !fail;
  }

  /**
   * Round 3. For any partial backup verification failures, each challenged guardian broadcasts its response to the challenge.
   * The mediator verifies the challenge. In point to point, each guardian would validate.
   */
  public boolean round3(ListMultimap<String, KeyCeremony2.PartialKeyVerification> failures) {
    boolean fail = false;
    // Each Guardian verifies all other Guardians' partial key backup
    for (KeyCeremony2.PartialKeyVerification failure : failures.values()) {
      // LOOK when/why does verifyPartialKeyBackup fail, but verifyPartialKeyChallenge succeed?
      //  when the designated Guardian is lying or mistaken?
      System.out.printf("Validate Guardian %s backup that was challenged by Guardian %s%n",
              failure.generatingGuardianId(), failure.designatedGuardianId());

      // If the recipient guardian T_l reports not receiving a suitable value P_i(l), the
      // sending guardian T_i publishes an unencypted P_i(l).
      KeyCeremonyTrusteeIF challenged = findTrusteeById(failure.generatingGuardianId());
      Optional<KeyCeremony2.PartialKeyChallengeResponse> responseO = challenged.sendBackupChallenge(failure.designatedGuardianId());
      if (responseO.isEmpty()) {
        fail = true;
      } else {
        KeyCeremony2.PartialKeyChallengeResponse response = responseO.get();

        // If guardian T_i fails to produce a suitable P_i(l) that match both the published encryption and the above equation, it should be
        // excluded from the election and the key generation process should be restarted with an
        // alternate guardian. If, however, the published P_i(l) satisfy both the published
        // encryption and the equation above, the claim of malfeasance is dismissed and the key
        // generation process continues undeterred.
        KeyCeremony2.PublicKeySet challengedGuardianKeys = publicKeysMap.get(response.generatingGuardianId());
        KeyCeremony2.PartialKeyVerification challenge_verify = KeyCeremony2.verifyElectionPartialKeyChallenge(response, challengedGuardianKeys.coefficientCommitments());
        if (!challenge_verify.error().isEmpty()) {
          System.out.printf("***FAILED to validate Guardian %s backup that was challenged by Guardian %s error = %s%n",
                  failure.generatingGuardianId(), failure.designatedGuardianId(), challenge_verify.error());
          fail = true;
        } else {
          System.out.printf("***SUCCESS validate Guardian %s backup that was challenged by Guardian %s%n",
                  failure.generatingGuardianId(), failure.designatedGuardianId());
        }
      }
    }
    return !fail;
  }

  /**
   * Round 4. All guardians compute and send their joint election public key.
   * If they agree, then key ceremony is a success.
   */
  public boolean round4() {
    boolean fail = false;
    boolean allMatch = true;

    SortedMap<String, Group.ElementModP> jointKeys = new TreeMap<>();
    for (KeyCeremonyTrusteeIF sender : trusteeProxies) {
      Optional<Group.ElementModP> jointKeyO = sender.sendJointPublicKey();
      if (jointKeyO.isEmpty()) {
        fail = true;
      } else {
        Group.ElementModP jointKey = jointKeyO.get();
        jointKeys.put(sender.id(), jointKey);
        if (this.jointKey == null) {
          this.jointKey = jointKey;
        } else {
          if (!this.jointKey.equals(jointKey)) {
            allMatch = false;
          }
        }
      }
    }

    if (!allMatch) {
      System.out.printf("Not all Guardians agree on JointKey value%n");
      jointKeys.forEach((key, value) -> System.out.printf("  %30s %s%n", key, value));
      System.out.printf("%n");
    }

    return !fail && allMatch;
  }

  public boolean makeCoefficientValidationSets() {
    // The hashing is order dependent, I think.
    List<KeyCeremony2.PublicKeySet> sorted = this.publicKeysMap.values().stream()
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

  public boolean publishElectionRecord(Publisher publisher) {
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
