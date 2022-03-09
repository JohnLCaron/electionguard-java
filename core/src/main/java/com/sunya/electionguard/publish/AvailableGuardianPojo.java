package com.sunya.electionguard.publish;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.sunya.electionguard.AvailableGuardian;
import com.sunya.electionguard.Group;

import java.lang.reflect.Type;

/** Conversion between GuardianRecord and Json, because records can be instantiated by reflection */
public class AvailableGuardianPojo {
  public String guardian_id;
  public Integer sequence;
  public Group.ElementModQ lagrangeCoordinate;

  ////////////////////////////////////////////////////////////////////////////
  // deserialize

  public static AvailableGuardian deserialize(JsonElement jsonElem) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    AvailableGuardianPojo pojo = gson.fromJson(jsonElem, AvailableGuardianPojo.class);
    return translateAvailableGuardian(pojo);
  }

  private static AvailableGuardian translateAvailableGuardian(AvailableGuardianPojo pojo) {
    return new AvailableGuardian(
            pojo.guardian_id,
            pojo.sequence,
            pojo.lagrangeCoordinate);
  }

  ////////////////////////////////////////////////////////////////////////////
  // serialize

  public static JsonElement serialize(AvailableGuardian src) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    AvailableGuardianPojo pojo = convertAvailableGuardian(src);
    Type typeOfSrc = new TypeToken<AvailableGuardianPojo>() {}.getType();
    return gson.toJsonTree(pojo, typeOfSrc);
  }

  private static AvailableGuardianPojo convertAvailableGuardian(AvailableGuardian org) {
    AvailableGuardianPojo pojo = new AvailableGuardianPojo();
    pojo.guardian_id = org.guardianId();
    pojo.sequence = org.xCoordinate();
    pojo.lagrangeCoordinate = org.lagrangeCoordinate();
    return pojo;
  }

}
