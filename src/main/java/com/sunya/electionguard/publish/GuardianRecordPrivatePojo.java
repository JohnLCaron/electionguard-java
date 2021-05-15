package com.sunya.electionguard.publish;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.sunya.electionguard.Auxiliary;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.ElectionPolynomial;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.GuardianRecordPrivate;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.SchnorrProof;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;

/**
 * Conversion between GuardianRecordPrivate and Json, using python's object model.
 * This is present for compatibility with python serialization. Prefer using
 * com.sunya.electionguard.decrypting, in particular DecryptingTrustee.
 */
public class GuardianRecordPrivatePojo {
  public ElectionKeyPairPojo election_keys;
  public AuxilaryKeyPairPojo auxiliary_keys;
  public Collection<ElectionPartialKeyBackupPojo> backups_to_share;
  public Collection<AuxilaryPublicKeyPojo> guardian_auxiliary_public_keys;
  public Collection<ElectionPublicKeyPojo> guardian_election_public_keys;
  public Collection<ElectionPartialKeyBackupPojo> guardian_election_partial_key_backups;
  public Collection<ElectionPartialKeyVerificationPojo> guardian_election_partial_key_verifications;

  public static class ElectionKeyPairPojo {
    public String owner_id;
    public int sequence_order;
    public ElGamal.KeyPair key_pair;
    public ElectionPolynomialPojo polynomial;
  }

  public static class ElectionPolynomialPojo {
    public String owner_id;
    public List<Group.ElementModQ> coefficients;
    public List<Group.ElementModP> coefficient_commitments;
    public List<SchnorrProof> coefficient_proofs;
  }

  public static class AuxilaryKeyPairPojo {
    public String owner_id;
    public int sequence_order;
    public String secret_key; // LOOK
    public String public_key; // LOOK
  }

  public static class AuxilaryPublicKeyPojo {
    public String owner_id;
    public int sequence_order;
    public String key; // LOOK
  }

  public static class ElectionPublicKeyPojo {
    public String owner_id;
    public int sequence_order;
    public Group.ElementModP key;
    public List<Group.ElementModP> coefficient_commitments;
    public List<SchnorrProof> coefficient_proofs;
  }

  public static class ElectionPartialKeyBackupPojo {
    public String owner_id;
    public String designated_id;
    public int designated_sequence_order;
    public String encrypted_value;
  }

  public static class ElectionPartialKeyVerificationPojo {
    public String owner_id;
    public String designated_id;
    public String verifier_id;
    public boolean verified;
  }

  ////////////////////////////////////////////////////////////////////////////
  // deserialize

  public static GuardianRecordPrivate deserialize(JsonElement jsonElem) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    GuardianRecordPrivatePojo pojo = gson.fromJson(jsonElem, GuardianRecordPrivatePojo.class);
    return translateGuardianRecordPrivate(pojo);
  }

  private static GuardianRecordPrivate translateGuardianRecordPrivate(GuardianRecordPrivatePojo pojo) {

    return GuardianRecordPrivate.create(
            translateElectionKeyPair(pojo.election_keys),
            translateAuxilaryKeyPair(pojo.auxiliary_keys),
            ConvertPojos.convertCollection(pojo.backups_to_share, GuardianRecordPrivatePojo::translateElectionPartialKeyBackup),
            ConvertPojos.convertCollection(pojo.guardian_auxiliary_public_keys, GuardianRecordPrivatePojo::translateAuxilaryPublicKey),
            ConvertPojos.convertCollection(pojo.guardian_election_public_keys, GuardianRecordPrivatePojo::translateElectionPublicKey),
            ConvertPojos.convertCollection(pojo.guardian_election_partial_key_backups, GuardianRecordPrivatePojo::translateElectionPartialKeyBackup),
            ConvertPojos.convertCollection(pojo.guardian_election_partial_key_verifications, GuardianRecordPrivatePojo::translateElectionPartialKeyVerification));
  }

  private static KeyCeremony.ElectionKeyPair translateElectionKeyPair(ElectionKeyPairPojo pojo) {
    return KeyCeremony.ElectionKeyPair.create(
            pojo.owner_id,
            pojo.sequence_order,
            pojo.key_pair,
            translateElectionPolynomial(pojo.polynomial));
  }

  private static ElectionPolynomial translateElectionPolynomial(ElectionPolynomialPojo pojo) {
    return new ElectionPolynomial(
            pojo.coefficients,
            pojo.coefficient_commitments,
            pojo.coefficient_proofs);
  }

  private static Auxiliary.KeyPair translateAuxilaryKeyPair(AuxilaryKeyPairPojo pojo) {
    return new Auxiliary.KeyPair(
            pojo.owner_id,
            pojo.sequence_order,
            // LOOK https://crypto.stackexchange.com/questions/46893/is-there-a-specification-for-the-begin-rsa-private-key-format
            null, null);
            //pojo.secret_key, // java.security.PrivateKey
            //pojo.public_key); // java.security.PublicKey
  }

  private static Auxiliary.PublicKey translateAuxilaryPublicKey(AuxilaryPublicKeyPojo pojo) {
    return new Auxiliary.PublicKey(
            pojo.owner_id,
            pojo.sequence_order,
            null); // java.security.PublicKey
  }

  private static KeyCeremony.ElectionPublicKey translateElectionPublicKey(ElectionPublicKeyPojo pojo) {
    return KeyCeremony.ElectionPublicKey.create(
            pojo.owner_id,
            pojo.sequence_order,
            pojo.key,
            pojo.coefficient_commitments,
            pojo.coefficient_proofs);
  }

  private static KeyCeremony.ElectionPartialKeyBackup translateElectionPartialKeyBackup(ElectionPartialKeyBackupPojo pojo) {
    return KeyCeremony.ElectionPartialKeyBackup.create(
            pojo.owner_id,
            pojo.designated_id,
            pojo.designated_sequence_order,
            new Auxiliary.ByteString(pojo.encrypted_value));
  }

  private static KeyCeremony.ElectionPartialKeyVerification translateElectionPartialKeyVerification(ElectionPartialKeyVerificationPojo pojo) {
    return KeyCeremony.ElectionPartialKeyVerification.create(
            pojo.owner_id,
            pojo.designated_id,
            pojo.verifier_id,
            pojo.verified);
  }

  ////////////////////////////////////////////////////////////////////////////
  // serialize

  public static JsonElement serialize(GuardianRecordPrivate src) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    GuardianRecordPrivatePojo pojo = convertGuardianRecordPrivate(src);
    Type typeOfSrc = new TypeToken<GuardianRecordPrivatePojo>() {}.getType();
    return gson.toJsonTree(pojo, typeOfSrc);
  }

  private static GuardianRecordPrivatePojo convertGuardianRecordPrivate(GuardianRecordPrivate org) {
    GuardianRecordPrivatePojo pojo = new GuardianRecordPrivatePojo();
    pojo.election_keys = convertElectionKeyPair(org.election_keys());
    pojo.auxiliary_keys = convertAuxilaryKeyPair(org.auxiliary_keys());
    pojo.backups_to_share = ConvertPojos.convertCollection(
            org.backups_to_share().values(),
            GuardianRecordPrivatePojo::convertElectionPartialKeyBackup);
    pojo.guardian_auxiliary_public_keys = ConvertPojos.convertCollection(
            org.guardian_auxiliary_public_keys().values(),
            GuardianRecordPrivatePojo::convertAuxiliaryPublicKey);
    pojo.guardian_election_public_keys = ConvertPojos.convertCollection(
            org.guardian_election_public_keys().values(),
            GuardianRecordPrivatePojo::convertElectionPublicKey);
    pojo.guardian_election_partial_key_backups = ConvertPojos.convertCollection(
            org.guardian_election_partial_key_backups().values(),
            GuardianRecordPrivatePojo::convertElectionPartialKeyBackup);
    pojo.guardian_election_partial_key_verifications = ConvertPojos.convertCollection(
            org.guardian_election_partial_key_verifications().values(),
            GuardianRecordPrivatePojo::convertElectionPartialKeyVerification);
    return pojo;
  }

  private static ElectionKeyPairPojo  convertElectionKeyPair(KeyCeremony.ElectionKeyPair org) {
    ElectionKeyPairPojo pojo = new ElectionKeyPairPojo();
    pojo.owner_id = org.owner_id();
    pojo.sequence_order = org.sequence_order();
    pojo.key_pair = org.key_pair();
    pojo.polynomial = convertElectionPolynomial(org.polynomial());
    return pojo;
  }

  private static ElectionPolynomialPojo convertElectionPolynomial(ElectionPolynomial org) {
    ElectionPolynomialPojo pojo = new ElectionPolynomialPojo();
    pojo.coefficients = org.coefficients;
    pojo.coefficient_commitments = org.coefficient_commitments;
    pojo.coefficient_proofs = org.coefficient_proofs;
    return pojo;
  }

  private static AuxilaryKeyPairPojo  convertAuxilaryKeyPair(Auxiliary.KeyPair org) {
    AuxilaryKeyPairPojo pojo = new AuxilaryKeyPairPojo();
    pojo.owner_id = org.owner_id;
    pojo.sequence_order = org.sequence_order;
    // LOOK pojo.secret_key = org.secret_key;
    // LOOK pojo.public_key = org.public_key;
    return pojo;
  }

  private static AuxilaryPublicKeyPojo convertAuxiliaryPublicKey(Auxiliary.PublicKey org) {
    AuxilaryPublicKeyPojo pojo = new AuxilaryPublicKeyPojo();
    pojo.owner_id = org.owner_id;
    pojo.sequence_order = org.sequence_order;
    // LOOK pojo.key = org.key;
    return pojo;
  }

  private static ElectionPublicKeyPojo convertElectionPublicKey(KeyCeremony.ElectionPublicKey org) {
    ElectionPublicKeyPojo pojo = new ElectionPublicKeyPojo();
    pojo.owner_id = org.owner_id();
    pojo.sequence_order = org.sequence_order();
    pojo.key = org.key();
    pojo.coefficient_commitments = org.coefficient_commitments();
    pojo.coefficient_proofs = org.coefficient_proofs();
    return pojo;
  }

  private static ElectionPartialKeyBackupPojo convertElectionPartialKeyBackup(KeyCeremony.ElectionPartialKeyBackup org) {
    ElectionPartialKeyBackupPojo pojo = new ElectionPartialKeyBackupPojo();
    pojo.owner_id = org.owner_id();
    pojo.designated_id = org.designated_id();
    pojo.designated_sequence_order = org.designated_sequence_order();
    pojo.encrypted_value = org.encrypted_value().toString();
    return pojo;
  }

  private static ElectionPartialKeyVerificationPojo convertElectionPartialKeyVerification(KeyCeremony.ElectionPartialKeyVerification org) {
    ElectionPartialKeyVerificationPojo pojo = new ElectionPartialKeyVerificationPojo();
    pojo.owner_id = org.owner_id();
    pojo.designated_id = org.designated_id();
    pojo.verifier_id = org.verifier_id();
    pojo.verified = org.verified();
    return pojo;
  }

}
