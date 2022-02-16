package com.sunya.electionguard.publish;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.ElectionPolynomial;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.standard.GuardianPrivateRecord;
import com.sunya.electionguard.standard.KeyCeremony;
import com.sunya.electionguard.SchnorrProof;

import java.lang.reflect.Type;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Conversion between GuardianRecordPrivate and Json, using python's object model.
 * This is present for compatibility with python serialization. Prefer using
 * com.sunya.electionguard.decrypting, in particular DecryptingTrustee.
 * see "https://stackoverflow.com/questions/46809528/how-to-save-and-re-use-keypairs-in-java-asymmetric-encryption"
 * TODO Test if match python JSON
 */
public class GuardianRecordPrivatePojo {
  public ElectionKeyPairPojo election_keys;
  public Collection<ElectionPartialKeyBackupPojo> backups_to_share;
  public Collection<ElectionPublicKeyPojo> guardian_election_public_keys;
  public Collection<ElectionPartialKeyBackupPojo> guardian_election_partial_key_backups;
  public Collection<ElectionPartialKeyVerificationPojo> guardian_election_partial_key_verifications;

  public static class ElectionKeyPairPojo {
    public String owner_id;
    public Integer sequence_order;
    public ElGamalKeyPairPojo key_pair;
    public ElectionPolynomialPojo polynomial;
  }

  public static class ElGamalKeyPairPojo {
    public Group.ElementModQ secret_key;
    public Group.ElementModP public_key;
  }

  public static class ElectionPolynomialPojo {
    public String owner_id;
    public List<Group.ElementModQ> coefficients;
    public List<Group.ElementModP> coefficient_commitments;
    public List<SchnorrProof> coefficient_proofs;
  }

  public static class ElectionPublicKeyPojo {
    public String owner_id;
    public Integer sequence_order;
    public Group.ElementModP key;
    public List<Group.ElementModP> coefficient_commitments;
    public List<SchnorrProof> coefficient_proofs;
  }

  public static class ElectionPartialKeyBackupPojo {
    public String owner_id;
    public String designated_id;
    public Integer designated_sequence_order;
    public Group.ElementModQ value;
  }

  public static class ElectionPartialKeyVerificationPojo {
    public String owner_id;
    public String designated_id;
    public String verifier_id;
    public Boolean verified = Boolean.FALSE;
  }

  ////////////////////////////////////////////////////////////////////////////
  // deserialize

  public static GuardianPrivateRecord deserialize(JsonElement jsonElem) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    GuardianRecordPrivatePojo pojo = gson.fromJson(jsonElem, GuardianRecordPrivatePojo.class);
    return deserializeGuardianPrivateRecord(pojo);
  }

  static GuardianPrivateRecord deserializeGuardianPrivateRecord(GuardianRecordPrivatePojo pojo) {
    List<KeyCeremony.ElectionPartialKeyBackup> backups_to_share = ConvertPojos.convertCollection(pojo.backups_to_share,
            GuardianRecordPrivatePojo::translateElectionPartialKeyBackup);
    List<KeyCeremony.ElectionPublicKey> guardian_election_public_keys = ConvertPojos.convertCollection(pojo.guardian_election_public_keys,
            GuardianRecordPrivatePojo::translateElectionPublicKey);
    List<KeyCeremony.ElectionPartialKeyBackup> guardian_election_partial_key_backups = ConvertPojos.convertCollection(pojo.guardian_election_partial_key_backups,
            GuardianRecordPrivatePojo::translateElectionPartialKeyBackup);
    List<KeyCeremony.ElectionPartialKeyVerification> guardian_election_partial_key_verifications = ConvertPojos.convertCollection(pojo.guardian_election_partial_key_verifications,
            GuardianRecordPrivatePojo::translateElectionPartialKeyVerification);

    // translate to Map
    return new GuardianPrivateRecord(
            translateElectionKeyPair(pojo.election_keys),
            backups_to_share.stream().collect(Collectors.toMap(o -> o.designated_id(), o -> o)),
            guardian_election_public_keys.stream().collect(Collectors.toMap(o -> o.owner_id(), o -> o)),
            guardian_election_partial_key_backups.stream().collect(Collectors.toMap(o -> o.owner_id(), o -> o)),
            guardian_election_partial_key_verifications.stream().collect(Collectors.toMap(o -> o.owner_id(), o -> o)));
  }

  private static KeyCeremony.ElectionKeyPair translateElectionKeyPair(ElectionKeyPairPojo pojo) {
    return new KeyCeremony.ElectionKeyPair(
            pojo.owner_id,
            pojo.sequence_order,
            translateElgamalKeyPair(pojo.key_pair),
            translateElectionPolynomial(pojo.polynomial));
  }

  private static ElGamal.KeyPair translateElgamalKeyPair(ElGamalKeyPairPojo pojo) {
    return new ElGamal.KeyPair(
            pojo.secret_key,
            pojo.public_key);
  }

  private static ElectionPolynomial translateElectionPolynomial(ElectionPolynomialPojo pojo) {
    return new ElectionPolynomial(
            pojo.coefficients,
            pojo.coefficient_commitments,
            pojo.coefficient_proofs);
  }

  private static java.security.PublicKey convertPublicKey(String publicKey) {
    try {
      byte[] publicKeyBytes = Base64.getDecoder().decode(publicKey);
      KeyFactory kf = KeyFactory.getInstance("RSA");
      X509EncodedKeySpec spec = new X509EncodedKeySpec(publicKeyBytes);
      return kf.generatePublic(spec);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static java.security.PrivateKey convertPrivateKey(String privateKey) {
    try {
      byte[] privateKeyBytes = Base64.getDecoder().decode(privateKey);
      KeyFactory kf = KeyFactory.getInstance("RSA");
      PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
      return kf.generatePrivate(privateKeySpec);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static KeyCeremony.ElectionPublicKey translateElectionPublicKey(ElectionPublicKeyPojo pojo) {
    return new KeyCeremony.ElectionPublicKey(
            pojo.owner_id,
            pojo.sequence_order,
            pojo.key,
            pojo.coefficient_commitments,
            pojo.coefficient_proofs);
  }

  private static KeyCeremony.ElectionPartialKeyBackup translateElectionPartialKeyBackup(ElectionPartialKeyBackupPojo pojo) {
    return new KeyCeremony.ElectionPartialKeyBackup(
            pojo.owner_id,
            pojo.designated_id,
            pojo.designated_sequence_order,
            pojo.value);
  }

  private static KeyCeremony.ElectionPartialKeyVerification translateElectionPartialKeyVerification(ElectionPartialKeyVerificationPojo pojo) {
    return new KeyCeremony.ElectionPartialKeyVerification(
            pojo.owner_id,
            pojo.designated_id,
            pojo.verifier_id,
            pojo.verified);
  }

  ////////////////////////////////////////////////////////////////////////////
  // serialize

  public static JsonElement serialize(GuardianPrivateRecord src) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    GuardianRecordPrivatePojo pojo = convertGuardianRecordPrivate(src);
    Type typeOfSrc = new TypeToken<GuardianRecordPrivatePojo>() {}.getType();
    return gson.toJsonTree(pojo, typeOfSrc);
  }

  private static GuardianRecordPrivatePojo convertGuardianRecordPrivate(GuardianPrivateRecord org) {
    GuardianRecordPrivatePojo pojo = new GuardianRecordPrivatePojo();
    pojo.election_keys = convertElectionKeyPair(org.election_keys());
    pojo.backups_to_share = ConvertPojos.convertCollection(
            org.backups_to_share().values(),
            GuardianRecordPrivatePojo::convertElectionPartialKeyBackup);
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
    pojo.key_pair = convertElgamalKeyPair(org.key_pair());
    pojo.polynomial = convertElectionPolynomial(org.polynomial());
    return pojo;
  }

  private static ElGamalKeyPairPojo convertElgamalKeyPair(ElGamal.KeyPair org) {
    ElGamalKeyPairPojo pojo = new ElGamalKeyPairPojo();
    pojo.secret_key = org.secret_key();
    pojo.public_key = org.public_key();
    return pojo;
  }

  private static ElectionPolynomialPojo convertElectionPolynomial(ElectionPolynomial org) {
    ElectionPolynomialPojo pojo = new ElectionPolynomialPojo();
    pojo.coefficients = org.coefficients;
    pojo.coefficient_commitments = org.coefficient_commitments;
    pojo.coefficient_proofs = org.coefficient_proofs;
    return pojo;
  }

  private static String convertPublicKey(java.security.PublicKey publicKey) {
    return Base64.getEncoder().encodeToString(publicKey.getEncoded());
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
    pojo.value = org.value();
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
