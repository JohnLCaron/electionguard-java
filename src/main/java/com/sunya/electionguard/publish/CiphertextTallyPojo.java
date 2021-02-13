package com.sunya.electionguard.publish;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.CiphertextTally;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/** Conversion between CiphertextTally and Json, using python's object model. */
public class CiphertextTallyPojo {
  public String object_id;
  public Map<String, CiphertextTallyContestPojo> cast;

  public static class CiphertextTallyContestPojo {
    public String object_id;
    public Group.ElementModQ description_hash;
    public Map<String, CiphertextTallySelectionPojo> tally_selections;
  }

  public static class CiphertextTallySelectionPojo {
    public String object_id;
    public Group.ElementModQ description_hash;
    public ElGamal.Ciphertext ciphertext;
  }

  ////////////////////////////////////////////////////////////////////////////
  // deserialize

  public static CiphertextTally deserialize(JsonElement jsonElem) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    CiphertextTallyPojo pojo = gson.fromJson(jsonElem, CiphertextTallyPojo.class);
    return translateTally(pojo);
  }

  private static CiphertextTally translateTally(CiphertextTallyPojo pojo) {
    Map<String, CiphertextTally.Contest> contests = new HashMap<>();
    for (Map.Entry<String, CiphertextTallyContestPojo> entry : pojo.cast.entrySet()) {
      contests.put(entry.getKey(), translateContest(entry.getValue()));
    }

    return new CiphertextTally(
            pojo.object_id,
            contests);
  }

  private static CiphertextTally.Contest translateContest(CiphertextTallyContestPojo pojo) {
    Map<String, CiphertextTally.Selection> selections = new HashMap<>();
    for (Map.Entry<String, CiphertextTallySelectionPojo> entry : pojo.tally_selections.entrySet()) {
      selections.put(entry.getKey(), translateSelection(entry.getValue()));
    }
    return new CiphertextTally.Contest(
            pojo.object_id,
            pojo.description_hash,
            selections);
  }

  private static CiphertextTally.Selection translateSelection(CiphertextTallySelectionPojo pojo) {
    //     public CiphertextTallySelection(String selectionDescriptionId, ElementModQ description_hash,
    //     @Nullable ElGamal.Ciphertext ciphertext) {
    return new CiphertextTally.Selection(
            pojo.object_id,
            pojo.description_hash,
            pojo.ciphertext);
  }

  ////////////////////////////////////////////////////////////////////////////
  // serialize

  public static JsonElement serialize(CiphertextTally src) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    CiphertextTallyPojo pojo = convertTally(src);
    Type typeOfSrc = new TypeToken<CiphertextTallyPojo>() {}.getType();
    return gson.toJsonTree(pojo, typeOfSrc);
  }

  private static CiphertextTallyPojo convertTally(CiphertextTally org) {
    Map<String, CiphertextTallyContestPojo> cast = new HashMap<>();
    for (Map.Entry<String, CiphertextTally.Contest> entry : org.contests.entrySet()) {
      cast.put(entry.getKey(), convertContest(entry.getValue()));
    }

    CiphertextTallyPojo pojo = new CiphertextTallyPojo();
    pojo.object_id = org.object_id;
    pojo.cast = cast;
    return pojo;
  }

  private static CiphertextTallyContestPojo convertContest(CiphertextTally.Contest org) {
    Map<String, CiphertextTallySelectionPojo> selections = new HashMap<>();
    for (Map.Entry<String, CiphertextTally.Selection> entry : org.tally_selections.entrySet()) {
      selections.put(entry.getKey(), convertSelection(entry.getValue()));
    }
    CiphertextTallyContestPojo pojo = new CiphertextTallyContestPojo();
    pojo.object_id = org.object_id;
    pojo.description_hash = org.contestDescriptionHash;
    pojo.tally_selections = selections;
    return pojo;
  }

  private static CiphertextTallySelectionPojo convertSelection(CiphertextTally.Selection org) {
    CiphertextTallySelectionPojo pojo = new CiphertextTallySelectionPojo();
    pojo.object_id = org.object_id;
    pojo.description_hash = org.description_hash;
    pojo.ciphertext = org.ciphertext();
    return pojo;
  }

}
