package com.sunya.electionguard.publish;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.GuardianRecord;
import com.sunya.electionguard.SchnorrProof;

import java.lang.reflect.Type;
import java.util.List;

/** Conversion between GuardianRecord and Json, using python's object model. */
public class GuardianRecordPojo {
  public String guardian_id;
  public Integer sequence_order;
  public Group.ElementModP election_public_key;
  public List<Group.ElementModP> election_commitments;
  public List<SchnorrProofPojo> election_proofs;

  public static class SchnorrProofPojo {
    public Group.ElementModP public_key;
    public Group.ElementModP commitment;
    public Group.ElementModQ challenge;
    public Group.ElementModQ response;
  }

  ////////////////////////////////////////////////////////////////////////////
  // deserialize

  public static GuardianRecord deserialize(JsonElement jsonElem) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    GuardianRecordPojo pojo = gson.fromJson(jsonElem, GuardianRecordPojo.class);
    return translateGuardianRecord(pojo);
  }

  private static GuardianRecord translateGuardianRecord(GuardianRecordPojo pojo) {
    return new GuardianRecord(
            pojo.guardian_id,
            pojo.sequence_order,
            pojo.election_public_key,
            pojo.election_commitments,
            ConvertPojos.convertList(pojo.election_proofs, GuardianRecordPojo::translateProof));
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

  public static JsonElement serialize(GuardianRecord src) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    GuardianRecordPojo pojo = convertCoefficients(src);
    Type typeOfSrc = new TypeToken<GuardianRecordPojo>() {}.getType();
    return gson.toJsonTree(pojo, typeOfSrc);
  }

  private static GuardianRecordPojo convertCoefficients(GuardianRecord org) {
    GuardianRecordPojo pojo = new GuardianRecordPojo();
    pojo.guardian_id = org.guardian_id();
    pojo.sequence_order = org.sequence_order();
    pojo.election_public_key = org.election_public_key();
    pojo.election_commitments = org.election_commitments();
    pojo.election_proofs = ConvertPojos.convertList(org.election_proofs(), GuardianRecordPojo::convertProof);
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
