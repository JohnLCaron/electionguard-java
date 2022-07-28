package com.sunya.electionguard.keyceremony;

import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.ElectionCryptoContext;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.GuardianRecord;
import com.sunya.electionguard.Hash;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.core.UInt256;
import com.sunya.electionguard.publish.Publisher;
import electionguard.ballot.ElectionConfig;
import electionguard.ballot.ElectionInitialized;
import electionguard.ballot.Guardian;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import static java.util.Collections.emptyMap;

/** Mediate the key ceremony using remote Guardians. */
public class KeyCeremonyRemoteMediator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  final Manifest election;
  final int quorum;
  final List<KeyCeremonyTrusteeIF> trusteeProxies;

  final Map<String, KeyCeremony2.PublicKeys> publicKeysMap = new HashMap<>();
  final List<Guardian> guardianRecords = new ArrayList<>();

  Group.ElementModP jointKey;
  Group.ElementModQ commitmentsHash;
  ElectionCryptoContext context;

  public KeyCeremonyRemoteMediator(Manifest election, int quorum,
                                   List<KeyCeremonyTrusteeIF> trusteeProxies) {
    this.election = election;
    this.quorum = quorum;
    this.trusteeProxies = trusteeProxies;
    System.out.printf("  KeyCeremonyRemoteMediator %d Guardians, quorum = %d%n", this.trusteeProxies.size(), this.quorum);

    HashSet<String> ids = new HashSet<>();
    HashSet<Integer> coords = new HashSet<>();
    for (KeyCeremonyTrusteeIF trustee : trusteeProxies) {
      if (ids.contains(trustee.id())) {
        throw new IllegalStateException(String.format("Duplicate trustee id = %s", trustee.id()));
      } else {
        ids.add(trustee.id());
      }
      if (coords.contains(trustee.xCoordinate())) {
        throw new IllegalStateException(String.format("Duplicate trustee xCoordinate = %d", trustee.xCoordinate()));
      } else {
        coords.add(trustee.xCoordinate());
      }
    }
  }

  /** Run the key ceremony. Caller calls publishElectionRecord() separately. */
  public void runKeyCeremony() {
    System.out.printf("  Key Ceremony Round1: exchange public keys%n");
    if (!round1()) {
      throw new RuntimeException("*** Round1 failed");
    }

    System.out.printf("  Key Ceremony Round2: exchange partial key backups%n");
    List<KeyCeremony2.PartialKeyVerification> failures = new ArrayList<>();
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

    this.context = ElectionCryptoContext.create(this.trusteeProxies.size(), this.quorum,
            this.jointKey, this.election, this.commitmentsHash, null);

    System.out.printf("  Key Ceremony complete%n");
  }

  Optional<KeyCeremonyTrusteeIF> findTrusteeById(String id) {
    return trusteeProxies.stream().filter(t -> t.id().equals(id)).findAny();
  }

  /**
   * Round 1. Each guardian shares their public keys with all the other guardians
   * Each guardian validates the other guardian's commitments against their proof.
   * Return true on success.
   */
  public boolean round1() {
    boolean fail = false;
    for (KeyCeremonyTrusteeIF trustee : trusteeProxies) {
      Optional<KeyCeremony2.PublicKeys> publicKeysO = trustee.sendPublicKeys();
      if (publicKeysO.isEmpty()) {
        fail = true;
      } else {
        KeyCeremony2.PublicKeys publicKeys = publicKeysO.get();
        this.publicKeysMap.put(publicKeys.guardianId(), publicKeys);
        // one could gather all PublicKeySets and send all at once, for 2*n, rather than n*n total messages.
        for (KeyCeremonyTrusteeIF recipient : trusteeProxies) {
          if (!trustee.id().equals(recipient.id())) {
            String error = recipient.receivePublicKeys(publicKeys);
            if (!error.isEmpty()) {
              System.out.printf("PublicKey Commitments: '%s' failed to validate '%s', error = '%s'%n", recipient.id(), trustee.id(), error);
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
  public boolean round2(List<KeyCeremony2.PartialKeyVerification> failures) {
    boolean fail = false;
    for (KeyCeremonyTrusteeIF trustee : trusteeProxies) {
      // one could gather all KeyBackups and send all at once, for 2*n, rather than 2*n*n total messages.
      for (KeyCeremonyTrusteeIF recipient : trusteeProxies) {
        if (!trustee.id().equals(recipient.id())) {
          // Each guardian T_i then publishes the encryption E_l(P_i(l)) for every other guardian T_l
          // This is the ElectionPartialKeyBackup
          Optional<KeyCeremony2.SecretKeyShare> backupO = trustee.sendPartialKeyBackup(recipient.id());
          if (backupO.isEmpty()) {
            fail = true;
          } else {
            KeyCeremony2.SecretKeyShare backup = backupO.get();
            Optional<KeyCeremony2.PartialKeyVerification> verifyO = recipient.verifyPartialKeyBackup(backup);
            if (verifyO.isEmpty()) {
              fail = true;
            } else {
              KeyCeremony2.PartialKeyVerification verify = verifyO.get();
              if (!verify.error().isEmpty()) {
                System.out.printf("Guardian %s backup challenged by Guardian %s error = '%s'%n",
                        trustee.id(), recipient.id(), verify.error());
                failures.add(verify);
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
   * Return true on success.
   */
  public boolean round3(List<KeyCeremony2.PartialKeyVerification> failures) {
    boolean fail = false;
    // Each Guardian verifies all other Guardians' partial key backup
    for (KeyCeremony2.PartialKeyVerification failure : failures) {
      // LOOK when/why does verifyPartialKeyBackup fail, but verifyPartialKeyChallenge succeed?
      //  when the designated Guardian is lying or mistaken?
      System.out.printf("Validate Guardian %s backup that was challenged by Guardian %s%n",
              failure.generatingGuardianId(), failure.designatedGuardianId());

      // If the recipient guardian T_l reports not receiving a suitable value P_i(l), the
      // sending guardian T_i publishes an unencypted P_i(l).
      Optional<KeyCeremonyTrusteeIF> challengedO = findTrusteeById(failure.generatingGuardianId());
      if (challengedO.isEmpty()) {
        System.out.printf("generatingGuardianId %s not found in trusteeProxies%n", failure.generatingGuardianId());
        fail = true;
      } else {
        KeyCeremonyTrusteeIF challenged = challengedO.get();
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
          KeyCeremony2.PublicKeys challengedGuardianKeys = publicKeysMap.get(response.generatingGuardianId());
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
    }
    return !fail;
  }

  /**
   * Round 4. All guardians compute and send their joint election public key.
   * If they agree, then key ceremony is a success.
   * Return true on success.
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
    // The hashing is order dependent, use the x coordinate to sort.
    List<KeyCeremony2.PublicKeys> sorted = this.publicKeysMap.values().stream()
            .sorted(Comparator.comparing(KeyCeremony2.PublicKeys::guardianXCoordinate)).toList();

    List<Group.ElementModP> commitments = new ArrayList<>();
    for (KeyCeremony2.PublicKeys keys : sorted) {
      GuardianRecord guardianRecord = new GuardianRecord(
              keys.guardianId(),
              keys.guardianXCoordinate(),
              keys.coefficientCommitments().get(0),
              keys.coefficientCommitments(),
              keys.coefficientProofs());
      this.guardianRecords.add(new Guardian(guardianRecord));
      commitments.addAll(keys.coefficientCommitments());
    }
    this.commitmentsHash = Hash.hash_elems(commitments);
    return true;
  }

  public boolean publishElectionRecord(Publisher publisher, ElectionConfig config) {
    System.out.printf("Publish ElectionRecord to %s%n", publisher.publishPath());
    // the election record
    try {
      publisher.writeElectionInitialized(
              //     val config: ElectionConfig,
              //    /** The joint public key (K) in the ElectionGuard Spec. */
              //    val jointPublicKey: Group.ElementModP,
              //    val manifestHash: UInt256, // matches Manifest.cryptoHash
              //    val cryptoExtendedBaseHash: UInt256, // aka qbar
              //    val guardians: List<Guardian>,
              //    val metadata: Map<String, String> = emptyMap(),
              new ElectionInitialized(
                      config,
                      this.jointKey,
                      UInt256.fromModQ(config.getManifest().cryptoHash()),
                      UInt256.fromModQ(this.context.cryptoBaseHash),
                      UInt256.fromModQ(this.context.cryptoExtendedBaseHash),
                      this.guardianRecords,
                      emptyMap()));
      return true;
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
  }

}
