package com.sunya.electionguard.publish;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.SchnorrProof;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Conversion between KeyCeremony.CoefficientValidationSet and Json, using python's object model. */
public class CoefficientValidationPojo {
  public String owner_id;
  public List<Group.ElementModP> coefficient_commitments;
  public List<SchnorrProofPojo> coefficient_proofs;

  public static class SchnorrProofPojo {
    public Group.ElementModP public_key;
    public Group.ElementModP commitment;
    public Group.ElementModQ challenge;
    public Group.ElementModQ response;
  }

  @Nullable
  private static <T, U> List<U> convertList(@Nullable List<T> from, Function<T, U> converter) {
    return from == null ? null : from.stream().map(converter).collect(Collectors.toList());
  }

  ////////////////////////////////////////////////////////////////////////////
  // deserialize

  public static KeyCeremony.CoefficientValidationSet deserialize(JsonElement jsonElem) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    CoefficientValidationPojo pojo = gson.fromJson(jsonElem, CoefficientValidationPojo.class);
    return translateCoefficients(pojo);
  }

  private static KeyCeremony.CoefficientValidationSet translateCoefficients(CoefficientValidationPojo pojo) {
    return KeyCeremony.CoefficientValidationSet.create(
            pojo.owner_id,
            pojo.coefficient_commitments,
            convertList(pojo.coefficient_proofs, CoefficientValidationPojo::translateProof));
  }

  private static SchnorrProof translateProof(SchnorrProofPojo pojo) {
    return new SchnorrProof(
            pojo.public_key,
            pojo.commitment,
            pojo.challenge,
            pojo.response);
  }

  ////////////////////////////////////////////////////////////////////////////
  // serialize

  public static JsonElement serialize(KeyCeremony.CoefficientValidationSet src) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    CoefficientValidationPojo pojo = convertCoefficients(src);
    Type typeOfSrc = new TypeToken<CoefficientValidationPojo>() {}.getType();
    return gson.toJsonTree(pojo, typeOfSrc);
  }

  private static CoefficientValidationPojo convertCoefficients(KeyCeremony.CoefficientValidationSet org) {
    CoefficientValidationPojo pojo = new CoefficientValidationPojo();
    pojo.owner_id = org.owner_id();
    pojo.coefficient_commitments = org.coefficient_commitments();
    pojo.coefficient_proofs = convertList(org.coefficient_proofs(), CoefficientValidationPojo::convertProof);
    return pojo;
  }

  private static SchnorrProofPojo convertProof(SchnorrProof org) {
    SchnorrProofPojo pojo = new SchnorrProofPojo();
    pojo.public_key = org.public_key;
    pojo.commitment = org.commitment;
    pojo.challenge  = org.challenge;
    pojo.response = org.response;
    return pojo;
  }

}
