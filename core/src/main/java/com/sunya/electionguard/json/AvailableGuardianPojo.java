package com.sunya.electionguard.json;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.sunya.electionguard.ballot.DecryptingGuardian;
import com.sunya.electionguard.Group;

import java.lang.reflect.Type;

/** Conversion between GuardianRecord and Json, because records can be instantiated by reflection */
public class AvailableGuardianPojo {
  public String guardian_id;
  public Integer sequence;
  public Group.ElementModQ lagrangeCoordinate;

  ////////////////////////////////////////////////////////////////////////////
  // deserialize

  public static DecryptingGuardian deserialize(JsonElement jsonElem) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    AvailableGuardianPojo pojo = gson.fromJson(jsonElem, AvailableGuardianPojo.class);
    return translateAvailableGuardian(pojo);
  }

  private static DecryptingGuardian translateAvailableGuardian(AvailableGuardianPojo pojo) {
    return new DecryptingGuardian(
            pojo.guardian_id,
            pojo.sequence,
            pojo.lagrangeCoordinate);
  }

  ////////////////////////////////////////////////////////////////////////////
  // serialize

  public static JsonElement serialize(DecryptingGuardian src) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    AvailableGuardianPojo pojo = convertAvailableGuardian(src);
    Type typeOfSrc = new TypeToken<AvailableGuardianPojo>() {}.getType();
    return gson.toJsonTree(pojo, typeOfSrc);
  }

  private static AvailableGuardianPojo convertAvailableGuardian(DecryptingGuardian org) {
    AvailableGuardianPojo pojo = new AvailableGuardianPojo();
    pojo.guardian_id = org.guardianId();
    pojo.sequence = org.xCoordinate();
    pojo.lagrangeCoordinate = org.lagrangeCoefficient();
    return pojo;
  }

}
